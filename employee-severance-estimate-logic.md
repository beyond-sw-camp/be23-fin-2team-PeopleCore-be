# 예상 퇴직금 조회 로직 명세 — 전직원 급여 탭

> **프로젝트**: PeopleCore HR
> **모듈**: mysalary-crud
> **대상 사용자**: 전 사원 (권한 제한 없음, 본인 기준만 조회)
> **관련 화면**: 급여 탭 > 예상 퇴직금 조회
> **작성일**: 2026-04-17
> **최종 수정일**: 2026-04-22 (DC 기적립 정보 포함)

---

## 1. 개요

재직 중인 사원이 임의의 **예상 퇴사일**을 입력하면, 그 시점을 기준으로 법정 퇴직금 예상액을 시뮬레이션해서 조회할 수 있는 API. 관리자 측 `SeveranceService.calculateSeverance()`와 동일한 법정 산식을 사용하되, **DB 저장 없이** 응답 DTO만 반환한다.

추가로 퇴직제도(`retirementType`)에 따라 다음을 함께 제공한다:
- **severance / DB**: 예상 퇴직금만 반환
- **DC**: 예상 퇴직금 + 회사가 사원 DC 계좌에 적립한 누적 금액 + 월별 적립 내역 + **오늘 퇴직 시 회사가 추가로 지급해야 하는 차액**

- 컨트롤러: `MySalaryController.estimateSeverance`
- 서비스: `MySalaryService.estimateSeverance`
- 엔드포인트: `POST /pay/my/severance`
- 권한: `@RoleRequired` 미적용 (X-User-Id 기준 본인만 조회)

---

## 2. API 스펙

| 항목 | 값 |
|---|---|
| Method | POST |
| Path | `/pay/my/severance` |
| Header | `X-User-Company: {UUID}`, `X-User-Id: {empId}` |
| Body | `SeveranceEstimateReqDto { resignDate: LocalDate (필수) }` |
| 응답 | `SeveranceEstimateResDto` |

### 요청 필드
| 필드 | 타입 | 설명 |
|---|---|---|
| `resignDate` | LocalDate | 예상 퇴사일 (@NotNull) |

### 응답 필드 (`SeveranceEstimateResDto`)

| 필드 | 타입 | 설명 | 해당 유형 |
|---|---|---|---|
| `hireDate` | LocalDate | 입사일 | 공통 |
| `resignDate` | LocalDate | 예상 퇴사일 | 공통 |
| `hasMidSettlement` | Boolean | 퇴직금 중간정산 여부 (현재 false 고정) | 공통 |
| `settlementPeriod` | String | "hireDate ~ resignDate" 형식 | 공통 |
| `serviceDays` | Long | 근속일수 | 공통 |
| `last3MonthTotalDays` | Integer | 예상 퇴직일 이전 3개월 총 일수 | 공통 |
| `last3MonthTotalPay` | Long | 최근 3개월 급여 총액 | 공통 |
| `lastYearBonusTotal` | Long | 직전 1년간 상여금 총액 | 공통 |
| `annualLeaveAllowance` | Long | 연차수당 (현재 0 고정) | 공통 |
| `avgDailyWage` | BigDecimal | 1일 평균임금 | 공통 |
| `estimatedSeverance` | Long | 예상 퇴직금 (법정 산식) | 공통 |
| `retirementType` | String | "severance" / "DB" / "DC" | 공통 |
| `dcDepositedTotal` | Long | DC형 누적 기적립금 (severance/DB면 null) | **DC 전용** |
| `dcDiffAmount` | Long | 오늘 퇴직 시 회사가 추가 지급할 차액 (`max(0, estimatedSeverance - dcDepositedTotal)`) | **DC 전용** |
| `dcDeposits` | List<DcDepositDto> | 월별 적립 내역 (최신순) | **DC 전용** |

### 하위 DTO — `DcDepositDto`

| 필드 | 타입 | 설명 |
|---|---|---|
| `depositDate` | LocalDateTime | 적립 일시 |
| `payYearMonth` | String | 적립 기준 월 (YYYY-MM) |
| `baseAmount` | Long | 적립기준임금 (당월 지급 합계) |
| `depositAmount` | Long | 적립 금액 |
| `depStatus` | String | `COMPLETED` / `PENDING` |

---

## 3. 산정 공식 (법정 퇴직금 기준)

```
1일 평균임금 = (최근 3개월 급여총액 + 상여금 가산액 + 연차수당) / 직전 3개월 총일수
상여금 가산액 = 직전 1년 상여금 총액 × (3 / 12)
퇴직금        = 1일 평균임금 × 30 × (근속일수 / 365)
```

### DC형 추가 계산
```
dcDepositedTotal = Σ(RetirementPensionDeposits.depositAmount) where status = COMPLETED
dcDiffAmount     = max(0, estimatedSeverance - dcDepositedTotal)
```

> **근속 1년 미만**: 퇴직금 없음 (모든 금액 필드 0으로 채워 반환)
> **통상임금 비교**: 본 조회는 평균임금만 사용 (관리자 확정 단계의 통상임금 대체 로직은 미포함)
> **연차수당**: 현재 `0L` 고정 — 추후 연차 모듈 연동 예정

---

## 4. 처리 절차 (`MySalaryService.estimateSeverance`)

1. **사원 조회**
   - `findEmployeeOrThrow(companyId, empId)` → 없으면 `EMPLOYEE_NOT_FOUND`
   - `hireDate = emp.getEmpHireDate()`
   - `retirementType = resolveRetirementType(emp, companyId)`
     - 1순위: `Employee.retirementType`
     - 2순위: `RetirementSettings.pensionType.toRetirementType()`
     - 회사가 `DB_DC` 병행인데 개인 설정이 없으면 예외

2. **근속일수 계산 & 미달 처리**
   - `serviceDays = ChronoUnit.DAYS.between(hireDate, resignDate)`
   - `serviceDays < 365`이면 즉시 0값 리턴 (retirementType은 채움):
     ```
     estimatedSeverance=0, dcDepositedTotal=null, dcDiffAmount=null, dcDeposits=null
     (severance/DB인 경우 DC 필드 3개는 null)
     ```

3. **직전 3개월 구간 산출**
   - `resignYm = YearMonth.from(resignDate)`
   - `last3Months = buildMonthRange(resignYm.minusMonths(3), resignYm.minusMonths(1), new ArrayList<>())`
   - `last3MonthTotalDays = calcTotalDays(resignDate.minusMonths(3), resignDate)`

4. **급여/상여 합산 (QueryDSL)**
   - `last3MonthPay = mySalaryQueryRepository.sumRecentMonthsPay(empId, companyId, last3Months)`
   - `last12Months = buildMonthRange(resignYm.minusMonths(12), resignYm.minusMonths(1), new ArrayList<>())`
   - `lastYearBonus = mySalaryQueryRepository.sumBonusAmount(empId, companyId, last12Months)`

5. **상여금 가산액 계산**
   ```
   bonusAdded = lastYearBonus × 3 / 12   (FLOOR)
   ```

6. **연차수당 처리**
   - 현재 `annualLeaveAllowance = 0L` 고정
   - TODO: 추후 `LeaveAllowance` 모듈 연동 예정

7. **1일 평균임금 계산**
   ```
   totalWageBase = last3MonthPay + bonusAdded + annualLeaveAllowance
   avgDailyWage  = totalWageBase / last3MonthTotalDays   (2자리, HALF_UP)
   ```

8. **예상 퇴직금 산출**
   ```
   estimatedSeverance = avgDailyWage × 30 × serviceDays / 365   (FLOOR, Long)
   ```

9. **DC형인 경우 추가 조회**
   - `retirementType == DC`이면:
     ```
     dcDepositedTotal = severanceRepository.sumDcDepositedTotal(empId, companyId)
     dcDiffAmount     = max(0, estimatedSeverance - dcDepositedTotal)
     dcDeposits       = retirementPensionDepositsRepository
                          .findByEmpIdAndCompany_CompanyIdOrderByDepositDateDesc(empId, companyId)
                          .stream()
                          .map(DcDepositDto::fromEntity)
                          .toList()
     ```
   - severance / DB인 경우: 위 세 필드 모두 `null`

10. **DTO 반환**
    - `SeveranceEstimateResDto.builder()...retirementType(...).dcDepositedTotal(...).dcDiffAmount(...).dcDeposits(...).build()`
    - **DB 저장 없음** — 순수 조회/시뮬레이션

---

## 5. 관리자 퇴직금 산정과의 차이

| 항목 | 관리자(`SeveranceService.calculateSeverance`) | 전직원(`MySalaryService.estimateSeverance`) |
|---|---|---|
| 대상 | 퇴직 확정 사원 (`empResignDate` 필수) | 재직 중 본인이 입력한 임의 일자 |
| 권한 | `HR_SUPER_ADMIN`, `HR_ADMIN` | 전 사원 (본인만) |
| 통상임금 비교 | O (근로기준법 제2조) | X |
| 연차수당 | 전년도 미사용 연차수당 실제값 합산 | 0 고정 (임시) |
| DC형 차액 계산 | O (저장까지) | O (조회만, 저장 없음) |
| **DC 월별 내역** | 응답에 포함 안 함 | **응답에 포함 (최신순 N개)** |
| 저장 | `SeverancePays` 엔티티 저장 | 저장 없음 (DTO 반환만) |
| 스냅샷 | empName/dept/grade/workGroup 저장 | 없음 |
| 예외 | `RESIGN_DATE_NOT_SET`, `SERVICE_PERIOD_TOO_SHORT` 던짐 | 1년 미만이면 0값 반환(예외 X) |

---

## 6. 예외 처리

| ErrorCode | 발생 조건 |
|---|---|
| `EMPLOYEE_NOT_FOUND` | 본인 사원 정보 없음 (X-User-Id/Company 불일치) |
| `RETIREMENT_SETTINGS_NOT_FOUND` | 회사 퇴직제도 설정 누락 (`resolveRetirementType`) |

> 근속 1년 미만은 정상 응답으로 0값을 돌려주므로 예외를 발생시키지 않는다.

---

## 7. 내부 유틸 함수

### 7-1. `buildMonthRange(YearMonth from, YearMonth to, List<String> acc)` — 재귀
- `from > to` 이면 acc 반환
- `acc.add(from.toString())` 후 `buildMonthRange(from.plusMonths(1), to, acc)`

### 7-2. `calcTotalDays(LocalDate from, LocalDate to)`
- `ChronoUnit.DAYS.between(from, to)` → `int`로 캐스팅

### 7-3. `resolveRetirementType(Employee emp, UUID companyId)` (공통 유틸 재사용)
- `SeveranceService`의 기존 메서드와 동일 로직 재사용 권장 (중복 구현 방지)

---

## 8. 파일 위치 요약

| 유형 | 경로 |
|---|---|
| Controller | `mysalary-crud/controller/MySalaryController.java` (`estimateSeverance`) |
| Service | `mysalary-crud/service/MySalaryService.java` (`estimateSeverance`) |
| 요청 DTO | `mysalary-crud/dto/SeveranceEstimateReqDto.java` |
| 응답 DTO | `mysalary-crud/dto/SeveranceEstimateResDto.java` |
| 하위 DTO (신규) | `mysalary-crud/dto/DcDepositDto.java` |
| QueryDSL | `mysalary-crud/repository/MySalaryQueryRepository` (`sumRecentMonthsPay`, `sumBonusAmount`) |
| 기적립 Repository | `pay/repository/RetirementPensionDepositsRepository.java` (findByEmpId...) |
| 기적립 합계 Repository | `pay/repository/SeverancePaysRepositoryImpl.sumDcDepositedTotal` (기존 재사용) |

---

## 9. 흐름 요약 다이어그램

```
[사원] 급여 탭 > 예상 퇴직금 조회
  └─ [예상 퇴사일 입력] → POST /pay/my/severance
        └─ MySalaryService.estimateSeverance()
              1) Employee 조회 + retirementType 해석
              2) serviceDays = days(hire → resign)
              3) serviceDays < 365 → 0값 DTO 즉시 반환 (retirementType 포함)
              4) 직전 3개월·12개월 구간 월 문자열 생성
              5) QueryDSL: 3개월 급여 + 12개월 상여 합계
              6) 상여 가산액 (×3/12)
              7) 평균임금 = (급여+상여+연차) / 3개월일수
              8) estimatedSeverance = 평균임금 × 30 × (근속일수/365)
              9) retirementType == DC 인 경우:
                   ├─ dcDepositedTotal = sumDcDepositedTotal(empId, companyId)
                   ├─ dcDiffAmount = max(0, estimatedSeverance - dcDepositedTotal)
                   └─ dcDeposits = 월별 적립 이력 조회 (최신순)
             10) DTO 빌드 후 반환 (DB 저장 없음)
```

---

## 10. 향후 개선 포인트

- **연차수당 연동**: 현재 `0L` 하드코딩 → `LeaveAllowance` 모듈에서 미사용 연차 수당 합산 예정
- **통상임금 대체 비교**: 관리자 정산과 동일 기준으로 통상임금 비교 로직 추가 가능
- **중간정산 이력 반영**: `hasMidSettlement` 플래그를 실제 중간정산 엔티티 조회 결과로 연동
- **DC 그래프/차트**: 프론트에서 월별 적립 추이 시각화 (응답의 `dcDeposits` 활용)
- **DB형 납입 이력 표시**: DB형도 회사가 외부 금융기관에 납입한 이력을 읽을 수 있다면 유사하게 표기 가능
