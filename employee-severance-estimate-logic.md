# 예상 퇴직금 조회 로직 명세 — 전직원 급여 탭

> **프로젝트**: PeopleCore HR
> **모듈**: mysalary-crud
> **대상 사용자**: 전 사원 (권한 제한 없음, 본인 기준만 조회)
> **관련 화면**: 급여 탭 > 예상 퇴직금 조회
> **작성일**: 2026-04-17

---

## 1. 개요

재직 중인 사원이 임의의 **예상 퇴사일**을 입력하면, 그 시점을 기준으로 법정 퇴직금 예상액을 시뮬레이션해서 조회할 수 있는 API. 관리자 측 `SeveranceService.calculateSeverance()`와 동일한 법정 산식을 사용하되, **DB 저장 없이** 응답 DTO만 반환한다.

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
| 필드 | 타입 | 설명 |
|---|---|---|
| `hireDate` | LocalDate | 입사일 |
| `resignDate` | LocalDate | 예상 퇴사일 |
| `hasMidSettlement` | Boolean | 퇴직금 중간정산 여부 (현재 false 고정) |
| `settlementPeriod` | String | "hireDate ~ resignDate" 형식 |
| `serviceDays` | Long | 근속일수 |
| `last3MonthTotalDays` | Integer | 예상 퇴직일 이전 3개월 총 일수 |
| `last3MonthTotalPay` | Long | 최근 3개월 급여 총액 |
| `lastYearBonusTotal` | Long | 직전 1년간 상여금 총액 |
| `annualLeaveAllowance` | Long | 연차수당 (현재 0 고정) |
| `avgDailyWage` | BigDecimal | 1일 평균임금 |
| `estimatedSeverance` | Long | 예상 퇴직금 |

---

## 3. 산정 공식 (법정 퇴직금 기준)

```
1일 평균임금 = (최근 3개월 급여총액 + 상여금 가산액 + 연차수당) / 직전 3개월 총일수
상여금 가산액 = 직전 1년 상여금 총액 × (3 / 12)
퇴직금        = 1일 평균임금 × 30 × (근속일수 / 365)
```

> **근속 1년 미만**: 퇴직금 없음 (모든 금액 필드 0으로 채워 반환)
> **통상임금 비교**: 본 조회는 평균임금만 사용 (관리자 확정 단계의 통상임금 대체 로직은 미포함)
> **연차수당**: 현재 `0L` 고정 — 추후 연차 모듈 연동 예정

---

## 4. 처리 절차 (MySalaryService.estimateSeverance)

1. **사원 조회**
   - `findEmployeeOrThrow(companyId, empId)` → 없으면 `EMPLOYEE_NOT_FOUND`
   - `hireDate = emp.getEmpHireDate()`

2. **근속일수 계산 & 미달 처리**
   - `serviceDays = ChronoUnit.DAYS.between(hireDate, resignDate)`
   - `serviceDays < 365`이면 즉시 0값 리턴:
     ```
     hireDate, resignDate, hasMidSettlement=false,
     settlementPeriod="hireDate ~ resignDate",
     serviceDays, last3MonthTotalDays=0,
     last3MonthTotalPay=0, lastYearBonusTotal=0,
     annualLeaveAllowance=0, avgDailyWage=0,
     estimatedSeverance=0
     ```

3. **직전 3개월 구간 산출**
   - `resignYm = YearMonth.from(resignDate)`
   - `last3Months = buildMonthRange(resignYm.minusMonths(3), resignYm.minusMonths(1), new ArrayList<>())`
     - 재귀 방식으로 `YYYY-MM` 문자열 리스트 생성
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
   - `last3MonthTotalDays == 0`이면 `avgDailyWage = 0`

8. **예상 퇴직금 산출**
   ```
   severance = avgDailyWage × 30 × serviceDays / 365   (FLOOR, Long)
   ```

9. **DTO 반환**
   - `SeveranceEstimateResDto.builder()...build()`
   - **DB 저장 없음** — 순수 조회/시뮬레이션

---

## 5. 관리자 퇴직금 산정과의 차이

| 항목 | 관리자(`SeveranceService.calculateSeverance`) | 전직원(`MySalaryService.estimateSeverance`) |
|---|---|---|
| 대상 | 퇴직 확정 사원 (`empResignDate` 필수) | 재직 중 본인이 입력한 임의 일자 |
| 권한 | `HR_SUPER_ADMIN`, `HR_ADMIN` | 전 사원 (본인만) |
| 통상임금 비교 | O (근로기준법 제2조) | X |
| 연차수당 | 전년도 미사용 연차수당 실제값 합산 | 0 고정 (임시) |
| DC형 차액 계산 | O (기적립금 차감 후 `dcDiffAmount`) | X |
| 저장 | `SeverancePays` 엔티티 저장 | 저장 없음 (DTO 반환만) |
| 스냅샷 | empName/dept/grade/workGroup 저장 | 없음 |
| 예외 | `RESIGN_DATE_NOT_SET`, `SERVICE_PERIOD_TOO_SHORT` 던짐 | 1년 미만이면 0값 반환(예외 X) |

---

## 6. 예외 처리

| ErrorCode | 발생 조건 |
|---|---|
| `EMPLOYEE_NOT_FOUND` | 본인 사원 정보 없음 (X-User-Id/Company 불일치) |

> 근속 1년 미만은 정상 응답으로 0값을 돌려주므로 예외를 발생시키지 않는다.

---

## 7. 내부 유틸 함수

### 7-1. `buildMonthRange(YearMonth from, YearMonth to, List<String> acc)` — 재귀
- `from > to` 이면 acc 반환
- `acc.add(from.toString())` 후 `buildMonthRange(from.plusMonths(1), to, acc)`
- 결과: `["2026-01", "2026-02", ...]` 형태 (QueryDSL IN 절에 전달)

### 7-2. `calcTotalDays(LocalDate from, LocalDate to)`
- `ChronoUnit.DAYS.between(from, to)` → `int`로 캐스팅

---

## 8. 파일 위치 요약

| 유형 | 경로 |
|---|---|
| Controller | `mysalary-crud/controller/MySalaryController.java` (`estimateSeverance`) |
| Service | `mysalary-crud/service/MySalaryService.java` (`estimateSeverance`) |
| 요청 DTO | `mysalary-crud/dto/SeveranceEstimateReqDto.java` |
| 응답 DTO | `mysalary-crud/dto/SeveranceEstimateResDto.java` |
| QueryDSL | `mysalary-crud/repository/MySalaryQueryRepository` (`sumRecentMonthsPay`, `sumBonusAmount`) |

---

## 9. 흐름 요약 다이어그램

```
[사원] 급여 탭 > 예상 퇴직금 조회
  └─ [예상 퇴사일 입력] → POST /pay/my/severance
        └─ MySalaryService.estimateSeverance()
              1) Employee 조회 (본인)
              2) serviceDays = days(hire → resign)
              3) serviceDays < 365 → 0값 DTO 즉시 반환
              4) 직전 3개월·12개월 구간 월 문자열 생성
              5) QueryDSL: 3개월 급여 + 12개월 상여 합계
              6) 상여 가산액 (×3/12)
              7) 평균임금 = (급여+상여+연차) / 3개월일수
              8) 퇴직금 = 평균임금 × 30 × (근속일수/365)
              9) DTO 빌드 후 반환 (DB 저장 없음)
```

---

## 10. 향후 개선 포인트

- **연차수당 연동**: 현재 `0L` 하드코딩 → `LeaveAllowance` 모듈에서 미사용 연차 수당 합산 예정
- **통상임금 대체 비교**: 관리자 정산과 동일 기준으로 통상임금 비교 로직 추가 가능
- **중간정산 이력 반영**: `hasMidSettlement` 플래그를 실제 중간정산 엔티티 조회 결과로 연동
- **DC 기적립금 대비 차액 표기**: 사원이 DC형일 경우 추가 지급 예상액을 함께 노출하면 체감성 개선
