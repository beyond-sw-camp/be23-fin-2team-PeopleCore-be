# 퇴직급추계액(퇴직금 산정) 로직 명세 — 어드민 / 슈퍼어드민 급여관리 탭

> **프로젝트**: PeopleCore HR
> **모듈**: hr-service
> **대상 사용자**: `HR_SUPER_ADMIN`, `HR_ADMIN`
> **관련 화면**: 급여관리 탭 > 퇴직금대장 (Severance Ledger)
> **작성일**: 2026-04-17

---

## 1. 개요

퇴직이 확정된 사원을 대상으로 법정 퇴직금을 자동 산정하여 "퇴직급추계액"(= 예상 지급액 / DC형의 경우 추가 지급할 차액)을 산출한다. 산정 결과는 `SeverancePays` 엔티티로 저장되며 이후 전자결재 → 지급 처리의 기준이 된다.

- 컨트롤러: `SeveranceController`
- 서비스: `SeveranceService`
- 엔드포인트: `POST /pay/admin/severance/calculate`
- 권한: `@RoleRequired({"HR_SUPER_ADMIN","HR_ADMIN"})`
- 트랜잭션: `@Transactional` (쓰기 트랜잭션)

---

## 2. API 스펙

| 항목 | 값 |
|---|---|
| Method | POST |
| Path | `/pay/admin/severance/calculate` |
| Header | `X-User-Company: {companyId}` (UUID) |
| Body | `SeveranceCalcReqDto` (empId 포함) |
| 응답 | `SeveranceDetailResDto` (HTTP 201 Created) |

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

## 4. 처리 절차 (SeveranceService.calculateSeverance)

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
| `netAmount` | 세후 지급액 (초기값은 severanceAmount) |
| `dcDepositedTotal` | DC형 기적립금 합계 |
| `dcDiffAmount` | DC형 차액 (실제 추가 지급) |
| `sevStatus` | CALCULATING → CONFIRMED → PENDING_APPROVAL → APPROVED → PAID |
| `empName`, `deptName`, `gradeName`, `workGroupName` | 산정 시점 스냅샷 |

---

## 6. 예외 처리

| ErrorCode | 발생 조건 |
|---|---|
| `EMPLOYEE_NOT_FOUND` | Company 또는 Employee 없음 |
| `RESIGN_DATE_NOT_SET` | `empResignDate`가 null |
| `SERVICE_PERIOD_TOO_SHORT` | 근속일수 < 365일 |
| `RETIREMENT_SETTINGS_NOT_FOUND` | 회사 퇴직제도 설정 누락 (resolveRetirementType) |
| `RETIREMENT_ACCOUNT_NOT_FOUND` | 지급 단계에서 퇴직연금 계좌 없음 (buildTransferDto) |

---

## 7. 로그

- 산정 완료 시 INFO 로그:
  `[SeveranceService 퇴직금 산정 완료 - empId:{}, amount={}, type:{}`
- 통상임금 대체 시 INFO 로그:
  `[SeveranceService] 평균임금 < 통상임금 -> 통상임금 적용 - emp={}, ordinary={}`

---

## 8. 연관 모듈

- **전자결재 연동**: `SeveranceApprovalResultEvent` / `SeveranceApprovalResultConsumer` (collaboration-service)
- **이체 파일 생성**: `BankTransferFileFactory` 재사용 (`PayrollTransferDto`)
- **연차수당**: `LeaveAllowanceRepository.getAnnualLeaveAllowance`
- **DC 기적립금**: `RetirementPensionDeposits` (status = COMPLETED)

---

## 9. 파일 위치 요약

| 유형 | 경로 |
|---|---|
| Controller | `hr-service/src/main/java/com/peoplecore/pay/controller/SeveranceController.java` |
| Service | `hr-service/src/main/java/com/peoplecore/pay/service/SeveranceService.java` |
| Repository (QueryDSL Impl) | `pay/repository/SeveranceRepositoryImpl.java` |
| Entity | `pay/domain/SeverancePays.java` |
| 요청 DTO | `pay/dtos/SeveranceCalcReqDto.java` |
| 응답 DTO | `pay/dtos/SeveranceDetailResDto.java` |

---

## 10. 관리자 관점 퇴직급추계액 흐름 요약

```
[급여관리 탭]
  └─ 퇴직금대장
        └─ [퇴직금 산정] 버튼 클릭
              └─ POST /pay/admin/severance/calculate
                    └─ SeveranceService.calculateSeverance()
                          1) 근속일수·3개월·1년 구간 산출
                          2) 급여·상여·연차 합산 → 평균임금
                          3) 통상임금 대체 비교
                          4) severanceAmount 산출
                          5) DB/DC 유형 판단
                          6) DC 차액 산출
                          7) 스냅샷 저장 (CALCULATING)
                    └─ SeveranceDetailResDto 반환
```
