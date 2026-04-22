# 퇴직금 산정 로직 명세 — 어드민 / 슈퍼어드민 급여관리 탭

> **프로젝트**: PeopleCore HR
> **모듈**: hr-service
> **대상 사용자**: `HR_SUPER_ADMIN`, `HR_ADMIN`
> **관련 화면**:
> - **퇴직금대장(작성)** — `SeveranceLedger` : 퇴직 확정 사원의 실제 퇴직금 산정·확정·결재·지급
> - **퇴직금추계액** — `SeveranceEstimate` : 재직 중 1년 이상 사원의 "오늘 퇴직한다고 가정" 시 예상 지급액(재무 부채 추정용) — 별도 로직
> **작성일**: 2026-04-17
> **최종 수정일**: 2026-04-22

---

## 1. 개요

이 문서는 **퇴직금대장(작성) 화면의 퇴직금 산정**(법정 퇴직금 계산) 흐름을 기술한다. 산정 결과는 `SeverancePays` 엔티티로 저장되며 이후 **확정 → 전자결재 상신 → 결재 완료 → 지급 처리** 의 기준이 된다.

- 컨트롤러: `SeveranceController`
- 서비스: `SeveranceService`
- 엔드포인트: `POST /pay/admin/severance/calculate`
- 권한: `@RoleRequired({"HR_SUPER_ADMIN","HR_ADMIN"})`
- 트랜잭션: `@Transactional` (쓰기)

> **참고**: 퇴직금추계액(`SeveranceEstimate`)은 재직자 대상 예상액 조회용으로, 이 문서가 다루는 "산정" 로직과는 **별개의 기능**이다. 단, 산정 공식(§3)은 동일하게 사용될 수 있다.

---

## 2. API 스펙

| 항목 | 값 |
|---|---|
| Method | POST |
| Path | `/pay/admin/severance/calculate` |
| Header | `X-User-Company: {companyId}` (UUID) |
| Body | `SeveranceCalcReqDto` (empId 포함) |
| 응답 | `SeveranceDetailResDto` (HTTP 201 Created) |

> 이 엔드포인트는 **수동 산정/재산정**용이다. 일반적인 퇴직자는 §11의 자동 산정 플로우로 처리된다.

---

## 3. 산정 알고리즘 (법정 퇴직금)

### 3-1. 기본 공식
```
퇴직금 = 1일 평균임금 × 30 × (근속일수 / 365)
```

### 3-2. 1일 평균임금 계산
```
1일 평균임금 = (최근 3개월 급여총액 + 상여금 가산액 + 연차수당) / 직전 3개월 총일수
상여금 가산액 = 직전 1년 상여금 × (3 / 12)
```

### 3-3. 통상임금 비교 (근로기준법 제2조)
- 평균임금 < 통상임금이면 **통상임금**을 적용한다.
- 1일 통상임금 = `(기본급 + 고정수당) 월액 / 209 × 8`
  - 대상: `PayItems.isFixed = true` AND `PayItemCategory IN (SALARY, ALLOWANCE)`
  - 제외: 상여금, 성과급, 연장/야간/휴일수당 등

### 3-4. 퇴직제도별 처리
| 유형 | 처리 방식 |
|---|---|
| **법정퇴직금(severance)** | 산정액 전액 지급 대상 |
| **DB형** | 법정 퇴직금 전액 지급 (적립은 사업주 부담) |
| **DC형** | `차액 = 퇴직금 - 기적립금 합계`, 차액만 추가 지급 (`dcDiffAmount = max(0, severanceAmount - dcDepositedTotal)`) |

---

## 4. 처리 절차 (`SeveranceService.calculateSeverance`)

1. **유효성 검증**
   - Company / Employee 조회 후 없으면 `EMPLOYEE_NOT_FOUND`
   - `empResignDate == null` 이면 `RESIGN_DATE_NOT_SET`
   - 근속일수 `< 365일` 이면 `SERVICE_PERIOD_TOO_SHORT`

2. **재산정 여부 판단**
   - 기존 `SevStatus.CALCULATING` 상태의 SeverancePays 존재 시 해당 엔티티를 재계산 (`recalculate(...)`)
   - 없으면 신규 엔티티 생성 (Builder)

3. **근속일수 / 근속연수**
   - `serviceDays = ChronoUnit.DAYS.between(hireDate, resignDate)`
   - `serviceYears = serviceDays / 365` (2자리 반올림)

4. **직전 3개월 구간 산출**
   - `resignYm = YearMonth.from(resignDate)`
   - 월 문자열 리스트 (`buildMonthRange`) = `resignYm.minusMonths(3) ~ resignYm.minusMonths(1)`
   - 예: 퇴사월 2026-06 → `["2026-03","2026-04","2026-05"]`
   - 총 일수 = `calcTotalDays(resignDate.minusMonths(3), resignDate)`

5. **급여 데이터 조회 (QueryDSL)**
   - `severanceRepository.sumLast3MonthPay(empId, companyId, last3Months)` — 최근 3개월 급여총액
   - `severanceRepository.sumLastYearBonus(empId, companyId, last12Months)` — 직전 1년 상여금
   - `severanceRepository.getAnnualLeaveAllowance(empId, companyId, year-1)` — 전년도 미사용 연차수당

6. **상여금 가산 / 평균임금 산출**
   - `bonusAdded = lastYearBonus × 3 / 12` (FLOOR)
   - `totalWageBase = last3MonthPay + bonusAdded + annualLeaveAllowance`
   - `avgDailyWage = totalWageBase / last3MonthDays` (2자리, HALF_UP)

7. **통상임금 비교**
   - `ordinaryDailyWage = calcOrdinaryDailyWage(empId, companyId)`
   - `avgDailyWage < ordinaryDailyWage` → `avgDailyWage = ordinaryDailyWage` 로 교체
   - 로그: `평균임금 < 통상임금 → 통상임금 적용`

8. **퇴직금 확정액 계산**
   ```
   severanceAmount = avgDailyWage × 30 × serviceDays / 365  (FLOOR, Long)
   ```

9. **퇴직제도 유형 판단 (`resolveRetirementType`)**
   - 1순위: `Employee.retirementType`
   - 2순위: `RetirementSettings.pensionType.toRetirementType()`
   - 회사가 `DB_DC` 병행인데 개인 설정이 없으면 변환 예외 발생

10. **DC형 차액 계산**
    - `dcDepositedTotal = severanceRepository.sumDcDepositedTotal(empId, companyId)`
      - 출처: `RetirementPensionDeposits` 테이블 (status = COMPLETED)
      - 적립 INSERT 지점은 §10 참조
    - `dcDiffAmount = max(0, severanceAmount - dcDepositedTotal)`

11. **스냅샷 필드 저장**
    - `empName`, `deptName`, `gradeName`, `workGroupName` — 산정 시점 정보 고정
    - 추후 소속·직급 변경 이력과 무관하게 퇴직금대장이 시점 데이터를 유지하도록 함

12. **저장 / 응답**
    - 신규: `severanceRepository.save(sev)`
    - 재산정: `sev.recalculate(...)`
    - 응답: `SeveranceDetailResDto.fromEntity(sev)`

---

## 5. 저장 필드 (`SeverancePays` 주요 컬럼)

| 필드 | 설명 |
|---|---|
| `hireDate`, `resignDate` | 입사일 / 퇴사일 |
| `retirementType` | severance / DB / DC |
| `serviceYears`, `serviceDays` | 근속연수 / 근속일수 |
| `last3MonthDays` | 직전 3개월 총 일수 |
| `lastYearBonus` | 직전 1년 상여금 총액 |
| `avgDailyWage` | 1일 평균임금 (통상임금 대체 반영 후) |
| `severanceAmount` | 법정 퇴직금 (추계액) |
| `taxAmount`, `localIncomeTax` | 퇴직소득세 / 지방소득세 |
| `netAmount` | 실지급액 (`severanceAmount - taxAmount - localIncomeTax + 퇴직정산 연차수당`) |
| `dcDepositedTotal` | DC형 기적립금 합계 |
| `dcDiffAmount` | DC형 차액 (실제 추가 지급) |
| `sevStatus` | CALCULATING → CONFIRMED → PENDING_APPROVAL → APPROVED → PAID |
| `approvalDocId` | 전자결재 문서 ID (상신 이후 바인딩) |
| `empName`, `deptName`, `gradeName`, `workGroupName` | 산정 시점 스냅샷 |

---

## 6. 예외 처리

| ErrorCode | 발생 조건 |
|---|---|
| `EMPLOYEE_NOT_FOUND` | Company 또는 Employee 없음 |
| `RESIGN_DATE_NOT_SET` | `empResignDate`가 null |
| `SERVICE_PERIOD_TOO_SHORT` | 근속일수 < 365일 (자동 산정 경로에서는 예외 아닌 스킵 처리) |
| `RETIREMENT_SETTINGS_NOT_FOUND` | 회사 퇴직제도 설정 누락 (`resolveRetirementType`) |
| `RETIREMENT_ACCOUNT_NOT_FOUND` | 지급 단계에서 퇴직연금 계좌 없음 (`buildTransferDto`) |

---

## 7. 로그

- 산정 완료 시 INFO:
  `[SeveranceService] 퇴직금 산정 완료 - empId={}, amount={}, type={}`
- 통상임금 대체 시 INFO:
  `[SeveranceService] 평균임금 < 통상임금 → 통상임금 적용 - emp={}, ordinary={}`
- 자동 산정 스킵(근속 1년 미만) INFO:
  `[Severance] 근속 1년 미만 - 자동 산정 스킵, empId={}`

---

## 8. 연관 모듈

- **전자결재 상신**: `ApprovalDraftController` → `ApprovalDraftFacade` → `SeveranceApprovalDraftService.submit()` (§12)
- **전자결재 결과 수신**: `SeveranceApprovalResultEvent` / `SeveranceApprovalResultConsumer` (collab → hr)
- **이체 파일 생성**: `BankTransferFileFactory` 재사용 (`PayrollTransferDto`)
- **연차수당**: `LeaveAllowanceRepository.getAnnualLeaveAllowance`
- **DC 기적립금**: `RetirementPensionDeposits` (status = COMPLETED) — §10 적립 흐름 참조

---

## 9. 파일 위치 요약

| 유형 | 경로 |
|---|---|
| Controller (산정) | `hr-service/src/main/java/com/peoplecore/pay/controller/SeveranceController.java` |
| Controller (전자결재) | `hr-service/src/main/java/com/peoplecore/pay/approval/ApprovalDraftController.java` |
| Service | `hr-service/src/main/java/com/peoplecore/pay/service/SeveranceService.java` |
| Service (상신) | `hr-service/src/main/java/com/peoplecore/pay/approval/SeveranceApprovalDraftService.java` |
| Listener (자동산정) | `hr-service/src/main/java/com/peoplecore/pay/listener/SeveranceEventListener.java` |
| Repository (QueryDSL Impl) | `pay/repository/SeveranceRepositoryImpl.java` |
| Repository (DC 적립) | `pay/repository/RetirementPensionDepositsRepository.java` |
| Entity | `pay/domain/SeverancePays.java`, `pay/domain/RetirementPensionDeposits.java` |
| 요청 DTO | `pay/dtos/SeveranceCalcReqDto.java` |
| 응답 DTO | `pay/dtos/SeveranceDetailResDto.java`, `SeveranceResDto.java`, `SeveranceListResDto.java` |

---

## 10. DC 기적립금 저장 흐름 (`RetirementPensionDeposits`)

DC형 사원은 매월 회사가 사원 개인 DC 계좌에 일정액을 적립한다. 이 기록이 있어야 퇴직금 산정 시 기적립금을 정확히 차감할 수 있다.

### 트리거 시점
`PayrollService.processPayment(companyId, payrollRunId)` 실행 시 (급여 지급처리 완료 시점)

### 흐름
```
급여대장 지급처리(PayrollStatus.PAID 전이)
  ↓
PayrollService.createDcDeposits(run, company)
  ├─ PayrollDetails 사원별 그룹핑
  ├─ DC형 사원만 필터 (Employee.retirementType == DC)
  ├─ 중복 방지 (existsByPayrollRunIdAndEmpId)
  └─ RetirementPensionDeposits INSERT (status = COMPLETED)
       · baseAmount = 당월 지급 합계
       · depositAmount = 회사 내규에 따른 적립액 (월단위 1/12 기준)
       · depositDate = 지급 처리 시각
       · payrollRunId = 해당 급여대장 ID
```

### 주의
- 퇴직금 산정 전에 충분한 적립 이력이 존재해야 DC 차액이 정확히 계산됨
- 누락 시 `sumDcDepositedTotal()`이 0을 반환 → 전액 이중지급 위험
- 과거 소급 적립이 필요한 경우 별도 어드민 기능으로 수동 INSERT

---

## 11. 자동 산정 흐름 (Spring ApplicationEvent)

관리자가 수동으로 `POST /calculate`를 호출하지 않아도, **퇴직 처리 스케줄러가 돌 때 자동으로 산정**된다.

### 흐름
```
ResignService.processScheduledResigns()
  ├─ Resign.processRetire()
  ├─ Employee.updateStatus(RESIGNED)
  ├─ Employee.updateResignDate(...)
  └─ eventPublisher.publishEvent(EmployeeRetiredEvent)
       ↓ (AFTER_COMMIT)
SeveranceEventListener.onEmployeeRetired()
  └─ SeveranceService.calculateByEmpId(companyId, empId)
       └─ SeverancePays INSERT (status = CALCULATING)
```

### 구현 포인트
- `@TransactionalEventListener(phase = AFTER_COMMIT)` 로 퇴직 트랜잭션 커밋 후 실행
- 근속 1년 미만은 `SERVICE_PERIOD_TOO_SHORT`로 예외 발생 → Listener에서 정상 스킵 처리 (로그만)
- 한 명 실패가 다른 사원 처리에 영향 주지 않도록 예외를 삼키는 구조
- 수동 산정(`POST /calculate`)도 유지되어 재산정/소급 산정 가능

### 이벤트 정의
`hr-service/.../resign/event/EmployeeRetiredEvent.java`
```java
@Getter @RequiredArgsConstructor
public class EmployeeRetiredEvent {
    private final UUID companyId;
    private final Long empId;
}
```

---

## 12. 전자결재 상신 흐름

퇴직금대장에서 CALCULATING → 확정 → 상신은 **공통 결재 퍼사드**(`ApprovalDraftFacade`)를 통한다.

### 상신 엔드포인트
- `POST /pay/admin/approval/submit`
- Request Body:
  ```json
  {
    "type": "RETIREMENT",
    "ledgerId": 123,
    "htmlContent": "<...렌더링된 결의서...>",
    "approvalLine": [{"approverId":1,"order":1,"approvalType":"APPROVE"}]
  }
  ```

### 흐름
```
프론트 [결재상신] 클릭
  ↓
GET /pay/admin/approval/draft?type=RETIREMENT&ledgerId={sevId}
  └─ SeveranceApprovalDraftService.draft()
       - 양식 로드 (퇴직급여지급결의서.html)
       - dataMap 구성 (사원 정보, 근속, 산정액, 세액, 실지급액 등)
  ↓
프론트 모달에서 데이터 치환 미리보기 + 결재선 선택
  ↓
POST /pay/admin/approval/submit
  └─ SeveranceApprovalDraftService.submit()
       - Kafka 이벤트 발행 (PayrollApprovalDocCreatedPublisher의 Severance 버전)
       - SeverancePays.submitApproval(...) 상태 전이 (CONFIRMED → PENDING_APPROVAL)
  ↓
collab-service: 결재 문서 생성 → 결재선 통보
  ↓
결재 결과 수신 (SeveranceApprovalResultConsumer)
  └─ SeveranceService.applyApprovalResult()
       - APPROVED / REJECTED / CANCELED 3분기 상태 전이
```

---

## 13. 상태 전이표

| 상태 | 진입 방법 | 다음 상태 |
|---|---|---|
| `CALCULATING` | 산정 완료 (자동/수동) | CONFIRMED (확정) |
| `CONFIRMED` | 관리자 확정 버튼 | PENDING_APPROVAL (상신) |
| `PENDING_APPROVAL` | 전자결재 상신 | APPROVED / REJECTED / CANCELED |
| `APPROVED` | 최종 결재 승인 | PAID (지급처리) |
| `PAID` | 지급 완료 | — |
| `REJECTED` | 반려 수신 | CALCULATING (재산정 가능) |
| `CANCELED` | 회수 수신 | CONFIRMED (재상신 가능) |

---

## 14. 알려진 버그 / 개선 포인트

| 위치 | 내용 | 영향 | 조치 |
|---|---|---|---|
| `SeveranceService.java` line 209 | `sev.recalculate(..., severanceAmount, dcDepositedTotal, dcDepositedTotal)` - 세 번째 인자가 `dcDiffAmount`여야 함 | **재산정 시 차액 필드에 기적립금 전체가 저장됨** | `dvDiffAmount` 로 변경 필요 |
| `SeveranceService.java` line 179 | 변수명 `dvDiffAmount` 오타 (정상: `dcDiffAmount`) | 가독성 | 리네이밍 |

---

## 15. 관리자 관점 전체 흐름 요약

```
[퇴직 스케줄러 발동]
  └─ ResignService.processScheduledResigns()
       ├─ Employee RESIGNED 전이
       └─ EmployeeRetiredEvent 발행 (AFTER_COMMIT)
              ↓
       SeveranceEventListener → calculateSeverance()
              ↓
       SeverancePays INSERT (CALCULATING)

[급여관리 탭 > 퇴직금대장(작성)]
  ├─ 조회: 산정된 내역 목록 확인
  ├─ 상세: 사원별 산정 내역 (근속/평균임금/세액/실지급액)
  ├─ 재산정: POST /calculate (CALCULATING 상태에서만)
  ├─ 확정: PUT /{sevId}/confirm → CONFIRMED
  ├─ 결재 상신: POST /approval/submit → PENDING_APPROVAL
  ├─ 결재 결과 자동 수신 (Kafka) → APPROVED/REJECTED/CANCELED
  └─ 지급 처리: PUT /{sevId}/pay → PAID
          └─ (DC형이면) RetirementPensionDeposits에 잔여 적립 기록 완료
```
