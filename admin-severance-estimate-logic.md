# 퇴직금추계액 로직 명세 — 어드민 / 슈퍼어드민 급여관리 탭

> **프로젝트**: PeopleCore HR
> **모듈**: hr-service
> **대상 사용자**: `HR_SUPER_ADMIN`, `HR_ADMIN`
> **관련 화면**: 급여관리 > 퇴직금추계액 (`SeveranceEstimate`)
> **작성일**: 2026-04-22

---

## 1. 개요

회사의 **퇴직급여충당부채**(재무제표상 잠재 부채)를 추정하기 위해, **기준일(baseDate) 현재 재직 중인 근속 1년 이상 사원 전원**에 대해 "오늘 퇴직한다고 가정할 때" 법정 퇴직금을 일괄 산정하는 화면의 로직.

### 다른 화면과의 관계

| 화면 | 대상 | 목적 | 저장 여부 |
|---|---|---|---|
| **퇴직금대장(작성)** | 퇴직 확정자(`RESIGNED`) | 실제 지급 산정 | `SeverancePays` INSERT |
| **퇴직금추계액** (본 문서) | 재직자(`ACTIVE`/`ON_LEAVE`) 중 근속 1년↑ | 재무 부채 추정 | **저장 없음** (조회 결과만 반환) |
| 예상 퇴직금 조회 (사원용) | 본인 (임의 가정일) | 사원 개인 시뮬레이션 | 저장 없음 |

> 퇴직금대장(작성)의 실제 산정 로직은 `SeveranceService.calculateSeverance()` 에 이미 구현되어 있음. 본 문서는 **추계액만** 다룬다.

### 공통 계산 로직 재사용 전략

`SeveranceService.calculateSeverance()`의 **핵심 계산 부분(§5.1)을 private helper로 추출**해서, 추계액 서비스와 공유한다. 재산정·소급 등도 같은 helper를 쓸 수 있다.

---

## 2. API 스펙

| 항목 | 값 |
|---|---|
| Method | GET |
| Path | `/pay/admin/severance/estimate` |
| Header | `X-User-Company: {UUID}` |
| Query | `baseDate` (LocalDate, 기본 = 오늘), `typeFilter?` (`severance`/`DB`/`DC`) |
| 응답 | `SeveranceEstimateSummaryResDto` |
| 권한 | `@RoleRequired({"HR_SUPER_ADMIN","HR_ADMIN"})` |

### 응답 필드 (`SeveranceEstimateSummaryResDto`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `baseDate` | LocalDate | 기준일 |
| `totalEmployees` | Integer | 산정 대상자 수 |
| `totalEstimateAmount` | Long | 산정 총액 (유형별 displayAmount 합계) |
| `severanceCount` / `severanceAmount` | Integer / Long | severance형 집계 |
| `dbCount` / `dbAmount` | Integer / Long | DB형 집계 |
| `dcCount` / `dcDiffAmount` | Integer / Long | DC형 **차액** 집계 (회사 추가 부담분) |
| `employees` | List<SeveranceEstimateRowDto> | 사원별 추계 상세 |

### 사원별 행 (`SeveranceEstimateRowDto`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `empId`, `empName`, `deptName`, `gradeName` | - | 사원 |
| `hireDate` | LocalDate | 입사일 |
| `serviceYears` | BigDecimal | 기준일 기준 근속연수 |
| `retirementType` | String | `severance` / `DB` / `DC` |
| `avgDailyWage` | BigDecimal | 1일 평균임금 |
| `estimatedSeverance` | Long | 법정 퇴직금 산정액 |
| `dcDepositedTotal` | Long? (DC만) | 현재까지 누적 적립액 |
| `dcDiffAmount` | Long? (DC만) | 추가 지급 예상 차액 |
| `displayAmount` | Long | 회사 부담 추정액 <br> · severance/DB: `estimatedSeverance` <br> · DC: `dcDiffAmount` |

---

## 3. 산정 공식

```
serviceDays   = 기준일 - 입사일
bonusAdded    = 직전 1년 상여 × 3 / 12
avgDailyWage  = (최근 3개월 급여 + bonusAdded + 연차수당) / 3개월 총일수
severanceAmt  = avgDailyWage × 30 × serviceDays / 365

DC형:
  dcDepositedTotal = Σ RetirementPensionDeposits.depositAmount (status=COMPLETED)
  dcDiffAmount     = max(0, severanceAmt - dcDepositedTotal)

displayAmount:
  severance/DB → severanceAmt
  DC           → dcDiffAmount
```

> 퇴직금대장(Part 1)과 **공식 자체는 동일**. 다만 "퇴사일" 자리에 "기준일(baseDate)"을 대체해서 쓴다.

---

## 4. 구현 코드

### 4-1. DTO — 사원별 행

**`hr-service/.../pay/dtos/SeveranceEstimateRowDto.java`**
```java
package com.peoplecore.pay.dtos;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeveranceEstimateRowDto {

    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;

    private LocalDate hireDate;
    private BigDecimal serviceYears;

    private String retirementType;        // "severance" / "DB" / "DC"

    private BigDecimal avgDailyWage;
    private Long estimatedSeverance;

    // DC 전용 (severance/DB는 null)
    private Long dcDepositedTotal;
    private Long dcDiffAmount;

    // 화면 표시용 실제 회사 부담 추정액
    private Long displayAmount;
}
```

### 4-2. DTO — 응답 요약

**`hr-service/.../pay/dtos/SeveranceEstimateSummaryResDto.java`**
```java
package com.peoplecore.pay.dtos;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeveranceEstimateSummaryResDto {

    private LocalDate baseDate;
    private Integer totalEmployees;
    private Long totalEstimateAmount;

    // 유형별 집계
    private Integer severanceCount;
    private Long severanceAmount;
    private Integer dbCount;
    private Long dbAmount;
    private Integer dcCount;
    private Long dcDiffAmount;

    private List<SeveranceEstimateRowDto> employees;
}
```

### 4-3. Controller 추가 (기존 `SeveranceController`에 메서드 추가)

**`hr-service/.../pay/controller/SeveranceController.java`** (기존 파일에 추가)
```java
//    퇴직금추계액 조회 (재직자 전원 기준)
@GetMapping("/estimate")
public ResponseEntity<SeveranceEstimateSummaryResDto> getEstimateSummary(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestParam(required = false) LocalDate baseDate,
        @RequestParam(required = false) String typeFilter) {

    LocalDate effectiveDate = baseDate != null ? baseDate : LocalDate.now();
    return ResponseEntity.ok(
            severanceEstimateService.getEstimateSummary(companyId, effectiveDate, typeFilter)
    );
}
```

> 기존 Controller에 `private final SeveranceEstimateService severanceEstimateService;` 주입 추가.

### 4-4. Service 신규

**`hr-service/.../pay/service/SeveranceEstimateService.java`** (신규)
```java
package com.peoplecore.pay.service;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.dtos.SeveranceEstimateRowDto;
import com.peoplecore.pay.dtos.SeveranceEstimateSummaryResDto;
import com.peoplecore.pay.enums.RetirementType;
import com.peoplecore.pay.repository.RetirementPensionDepositsRepository;
import com.peoplecore.pay.repository.SeverancePaysRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeveranceEstimateService {

    private final EmployeeRepository employeeRepository;
    private final SeverancePaysRepository severanceRepository;
    private final SeveranceService severanceService;  // 공통 helper 재사용 (resolveRetirementType 등)
    private final RetirementPensionDepositsRepository retirementPensionDepositsRepository;

    /**
     * 재직 중 근속 1년 이상 사원 전원의 퇴직금 추계액을 계산한다.
     * typeFilter: null이면 전체, 아니면 해당 유형만.
     */
    public SeveranceEstimateSummaryResDto getEstimateSummary(UUID companyId, LocalDate baseDate, String typeFilter) {
        long startTs = System.currentTimeMillis();

        // 1) 재직자 중 근속 1년 이상 조회
        List<Employee> targets = employeeRepository.findAllActiveOverOneYear(
                companyId,
                List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE),
                baseDate.minusYears(1)  // hireDate <= baseDate - 1년
        );

        log.info("[SeveranceEstimate] 추계액 산정 시작 - companyId={}, baseDate={}, target={}명",
                companyId, baseDate, targets.size());

        List<SeveranceEstimateRowDto> rows = new ArrayList<>();

        for (Employee emp : targets) {
            try {
                RetirementType rt = severanceService.resolveRetirementType(emp, companyId);

                // typeFilter 적용
                if (typeFilter != null && !typeFilter.isBlank()
                        && !rt.name().equalsIgnoreCase(typeFilter)) continue;

                SeveranceEstimateRowDto row = calculateOneRow(emp, baseDate, rt, companyId);
                rows.add(row);
            } catch (Exception e) {
                log.warn("[SeveranceEstimate] 사원 산정 실패 - empId={}, reason={}",
                        emp.getEmpId(), e.getMessage());
            }
        }

        // 2) 유형별 집계
        int sevCount = 0, dbCount = 0, dcCount = 0;
        long sevAmt = 0, dbAmt = 0, dcDiff = 0, totalAmt = 0;
        for (SeveranceEstimateRowDto r : rows) {
            totalAmt += r.getDisplayAmount() != null ? r.getDisplayAmount() : 0L;
            switch (r.getRetirementType()) {
                case "severance" -> { sevCount++; sevAmt += r.getEstimatedSeverance(); }
                case "DB"        -> { dbCount++;  dbAmt  += r.getEstimatedSeverance(); }
                case "DC"        -> { dcCount++;  dcDiff += r.getDcDiffAmount() != null ? r.getDcDiffAmount() : 0L; }
            }
        }

        long elapsed = System.currentTimeMillis() - startTs;
        log.info("[SeveranceEstimate] 추계액 산정 완료 - totalAmount={}, 소요시간={}ms", totalAmt, elapsed);

        return SeveranceEstimateSummaryResDto.builder()
                .baseDate(baseDate)
                .totalEmployees(rows.size())
                .totalEstimateAmount(totalAmt)
                .severanceCount(sevCount).severanceAmount(sevAmt)
                .dbCount(dbCount).dbAmount(dbAmt)
                .dcCount(dcCount).dcDiffAmount(dcDiff)
                .employees(rows)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // 사원 1명에 대한 추계액 계산 (공식은 SeveranceService.calculateSeverance와 동일)
    // ─────────────────────────────────────────────────────────────
    private SeveranceEstimateRowDto calculateOneRow(
            Employee emp, LocalDate baseDate, RetirementType rt, UUID companyId) {

        LocalDate hireDate = emp.getEmpHireDate();
        long serviceDays = ChronoUnit.DAYS.between(hireDate, baseDate);
        BigDecimal serviceYears = BigDecimal.valueOf(serviceDays)
                .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);

        // 최근 3개월 / 12개월 구간 월 문자열 리스트
        YearMonth baseYm = YearMonth.from(baseDate);
        List<String> last3Months = buildMonthRange(baseYm.minusMonths(3), baseYm.minusMonths(1), new ArrayList<>());
        List<String> last12Months = buildMonthRange(baseYm.minusMonths(12), baseYm.minusMonths(1), new ArrayList<>());

        int last3MonthDays = (int) ChronoUnit.DAYS.between(baseDate.minusMonths(3), baseDate);

        Long last3MonthPay = severanceRepository.sumLast3MonthPay(emp.getEmpId(), companyId, last3Months);
        Long lastYearBonus = severanceRepository.sumLastYearBonus(emp.getEmpId(), companyId, last12Months);
        Long annualLeaveAllowance = 0L;  // TODO: 연차수당 모듈 연동 시 변경

        long bonusAdded = lastYearBonus != null ? (lastYearBonus * 3 / 12) : 0L;
        long totalWageBase = (last3MonthPay != null ? last3MonthPay : 0L) + bonusAdded + annualLeaveAllowance;

        BigDecimal avgDailyWage = last3MonthDays > 0
                ? BigDecimal.valueOf(totalWageBase).divide(BigDecimal.valueOf(last3MonthDays), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long estimatedSeverance = avgDailyWage
                .multiply(BigDecimal.valueOf(30))
                .multiply(BigDecimal.valueOf(serviceDays))
                .divide(BigDecimal.valueOf(365), 0, RoundingMode.FLOOR)
                .longValue();

        // DC형 처리
        Long dcDepositedTotal = null;
        Long dcDiffAmount = null;
        long displayAmount = estimatedSeverance;

        if (rt == RetirementType.DC) {
            dcDepositedTotal = severanceRepository.sumDcDepositedTotal(emp.getEmpId(), companyId);
            dcDiffAmount = Math.max(0, estimatedSeverance - (dcDepositedTotal != null ? dcDepositedTotal : 0L));
            displayAmount = dcDiffAmount;
        }

        return SeveranceEstimateRowDto.builder()
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .gradeName(emp.getGrade() != null ? emp.getGrade().getGradeName() : null)
                .hireDate(hireDate)
                .serviceYears(serviceYears)
                .retirementType(rt.name())
                .avgDailyWage(avgDailyWage)
                .estimatedSeverance(estimatedSeverance)
                .dcDepositedTotal(dcDepositedTotal)
                .dcDiffAmount(dcDiffAmount)
                .displayAmount(displayAmount)
                .build();
    }

    // ─── 유틸 ───
    private List<String> buildMonthRange(YearMonth from, YearMonth to, List<String> acc) {
        if (from.isAfter(to)) return acc;
        acc.add(from.toString());
        return buildMonthRange(from.plusMonths(1), to, acc);
    }
}
```

### 4-5. `resolveRetirementType` 처리 방법 (선택)

`SeveranceEstimateService`에서 퇴직제도 유형 해석(`retirementType`)이 필요하다. 두 가지 방법 중 선택:

**방법 A (추천 · 간단)**: `SeveranceEstimateService`에 **똑같은 메서드를 복사**
```java
// SeveranceEstimateService 안에 추가
private RetirementType resolveRetirementType(Employee emp, UUID companyId) {
    // SeveranceService의 로직을 복사 (1순위: Employee.retirementType, 2순위: RetirementSettings)
}
```
→ 기존 `SeveranceService` 수정 없음. 가장 빠름. 다만 로직 바뀌면 두 곳 동기화 필요.

**방법 B (리팩터)**: `SeveranceService`의 `private` 가시성을 **package-private(default)** 로 풀고 공유
```java
// SeveranceService.java 수정
// Before
private RetirementType resolveRetirementType(Employee emp, UUID companyId) { ... }
// After  (접근 제어자만 제거 → 같은 패키지의 다른 클래스가 호출 가능)
RetirementType resolveRetirementType(Employee emp, UUID companyId) { ... }
```
그리고 `SeveranceEstimateService`에서:
```java
private final SeveranceService severanceService;   // 주입
...
RetirementType rt = severanceService.resolveRetirementType(emp, companyId);
```

**방법 C (장기 리팩터)**: 공용 유틸 클래스로 분리
```java
// pay/util/RetirementTypeResolver.java (신규)
@Component
public class RetirementTypeResolver {
    public RetirementType resolve(Employee emp, UUID companyId) { ... }
}
```
→ 두 서비스가 같은 Resolver 주입받아 사용. 가장 깔끔하지만 작업량 많음.

> 아래 §4-4의 서비스 코드는 **방법 B** 기준으로 작성되어 있다. 방법 A를 선택하면 `SeveranceService` 주입을 제거하고 직접 `resolveRetirementType`을 서비스 내부에 정의하면 된다.

### 4-6. EmployeeRepository 추가 쿼리

**`employee/repository/EmployeeRepository.java`** (인터페이스에 메서드 추가)
```java
/**
 * 재직 중(ACTIVE/ON_LEAVE)이고 기준일 기준 근속 1년 이상인 사원 전원 조회.
 * hireDate <= cutoffDate  ⇒  근속 ≥ 1년
 */
@Query("""
    SELECT e FROM Employee e
    WHERE e.company.companyId = :companyId
      AND e.empStatus IN :statuses
      AND e.empHireDate <= :cutoffDate
""")
List<Employee> findAllActiveOverOneYear(
        @Param("companyId") UUID companyId,
        @Param("statuses") List<EmpStatus> statuses,
        @Param("cutoffDate") LocalDate cutoffDate
);
```

### 4-7. SeverancePaysRepository 메서드 재사용

기존 `SeverancePaysRepository`에 이미 정의된 아래 메서드를 그대로 호출:
- `sumLast3MonthPay(empId, companyId, monthList)` — 월 리스트 기반 IN 절
- `sumLastYearBonus(empId, companyId, monthList)`
- `sumDcDepositedTotal(empId, companyId)`

추가 구현 불필요.

---

## 5. 성능 고려

### N+1 쿼리 발산 가능성

사원 수 500명이면 `calculateOneRow`가 500번 돌면서 `sumLast3MonthPay` + `sumLastYearBonus` + `sumDcDepositedTotal` 각 1회씩 = **1,500회 쿼리**. 개발 단계에선 OK, 운영에선 최적화 필요.

### 최적화 옵션

**옵션 A (추천): 일괄 쿼리로 대체**
```java
// SeverancePaysRepository에 추가
Map<Long, Long> sumLast3MonthPayByEmpIds(UUID companyId, List<Long> empIds, List<String> months);
Map<Long, Long> sumLastYearBonusByEmpIds(UUID companyId, List<Long> empIds, List<String> months);
Map<Long, Long> sumDcDepositedTotalByEmpIds(UUID companyId, List<Long> empIds);
```
GROUP BY empId로 한 번에 조회하고 Map으로 반환. 서비스에서 map.get(empId)로 조회.

**옵션 B: @Async 병렬**
```java
@Async
public CompletableFuture<SeveranceEstimateRowDto> calculateOneRowAsync(...) { ... }
```
쓰레드 풀 설정 필요. 쿼리 수 줄어들진 않지만 응답 시간 단축.

**옵션 C: 캐시**
- Spring Cache + Caffeine / Redis
- 키: `(companyId, baseDate.toString(), typeFilter)`
- TTL: 1시간 (또는 급여대장 상태 변화 이벤트 기반 무효화)
- 사용 사례: 연말정산·분기 보고 등 동일 기준일 반복 조회

---

## 6. 예외 처리

| ErrorCode | 발생 조건 | 처리 방식 |
|---|---|---|
| `COMPANY_NOT_FOUND` | companyId 불일치 | 전체 응답 실패 (예외 throw) |
| `RETIREMENT_SETTINGS_NOT_FOUND` | 회사 퇴직제도 설정 누락 | 개별 사원 스킵 + WARN 로그 |
| 기타 (연산 오류 등) | - | 개별 사원 스킵 + WARN 로그 |

> 한 사원의 계산 실패가 전체 응답을 깨지 않도록 try-catch로 감싼다.

---

## 7. 로그

```
[SeveranceEstimate] 추계액 산정 시작 - companyId={uuid}, baseDate=2026-04-22, target=87명
[SeveranceEstimate] 사원 산정 실패 - empId=42, reason=RETIREMENT_SETTINGS_NOT_FOUND
[SeveranceEstimate] 추계액 산정 완료 - totalAmount=1234567890, 소요시간=432ms
```

---

## 8. 파일 위치 요약

| 유형 | 경로 | 상태 |
|---|---|---|
| Controller | `hr-service/.../pay/controller/SeveranceController.java` | 기존 + 메서드 추가 |
| Service | `hr-service/.../pay/service/SeveranceEstimateService.java` | **신규** |
| Employee Repository | `employee/repository/EmployeeRepository.java` | 기존 + 쿼리 추가 |
| Severance Repository | `pay/repository/SeverancePaysRepository(Impl).java` | 기존 재사용 |
| DC Repository | `pay/repository/RetirementPensionDepositsRepository.java` | 기존 재사용 |
| 요약 응답 DTO | `pay/dtos/SeveranceEstimateSummaryResDto.java` | **신규** |
| 사원별 행 DTO | `pay/dtos/SeveranceEstimateRowDto.java` | **신규** |

---

## 9. 화면 흐름 요약

```
[급여관리 > 퇴직금추계액]
  ├─ 기준일 입력 (기본: 오늘)
  ├─ 유형 필터 선택 (전체/severance/DB/DC)
  └─ [조회] → GET /pay/admin/severance/estimate?baseDate=&typeFilter=
        └─ SeveranceEstimateService.getEstimateSummary()
              1) 재직 1년↑ 대상자 조회
              2) 각 사원별로 calculateOneRow()
                   · 근속일수 / 3개월구간 / 평균임금 / 법정 퇴직금
                   · DC면 기적립금 차감해서 dcDiffAmount, displayAmount = dcDiffAmount
                   · severance/DB면 displayAmount = estimatedSeverance
              3) 유형별 집계
              4) 총 부채 추정액 반환
        └─ 화면 렌더링
              · 요약 카드 (대상자 / 총 추정액 / 유형별 집계)
              · 사원별 테이블 (근속 / 평균임금 / 예상 퇴직금 / DC 차액)
```

---

## 10. 향후 개선 포인트

- **Excel 내보내기**: 재무팀 보고서용 다운로드 기능
- **기간 추이 비교**: 월/분기별 추계액 변화 그래프
- **시나리오 분석**: 특정 사원 그룹 퇴직 가정 시 부채 변동 시뮬레이션
- **통상임금 대체 옵션**: 토글로 Part 1처럼 통상임금 비교 적용 가능
- **예상 지급일 기반 할인**: 미래 예측 시 현재가치 할인 적용 (회계기준 ASC 715 등)
- **연차수당 연동**: 현재 `0L` 하드코딩 → `LeaveAllowance` 모듈 연동

---

## 11. 퇴직금대장(작성) 산정 로직 참고

본 문서는 **추계액 전용**이다. 실제 퇴직금 산정(퇴직확정자 대상) 로직은 `SeveranceService.calculateSeverance()`에 이미 구현되어 있으며, 본 추계액 서비스는 그 계산 방식을 그대로 **참조·재사용**한다:

- `resolveRetirementType()` — 재사용
- `buildMonthRange()` / 날짜 유틸 — 복제 또는 공통 유틸로 추출
- `SeverancePaysRepository.sumLast3MonthPay / sumLastYearBonus / sumDcDepositedTotal` — 재사용

계산 공식이 바뀌면 두 곳을 함께 수정해야 하므로, 향후 공통 계산 모듈(`SeveranceCalculator` 유틸 클래스)로 추출하는 리팩터를 권장한다.
