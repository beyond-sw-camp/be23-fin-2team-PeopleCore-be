# 내 퇴직금 예상 조회 로직 명세 — 전직원 급여 탭

> **프로젝트**: PeopleCore HR
> **모듈**: mysalary-crud
> **대상 사용자**: 전 사원 (권한 제한 없음, 본인 정보만 조회)
> **관련 화면**: 급여 탭 > 내 퇴직금 예상
> **작성일**: 2026-04-24

---

## 1. 개요

전 사원이 본인의 **기준일 현재 시점 예상 퇴직금(추계액)** 을 조회할 수 있는 API.
관리자용 전사 추계(`SeveranceEstimateService.getEstimateSummary`) 의 단일 사원 버전.
로그인한 사원의 `empId` 만으로 계산하며, 다른 사람 조회는 불가.

- 컨트롤러: `MySalaryController` (기존 `/pay/my` 라우트에 엔드포인트 추가)
- 서비스: `MySeveranceService` (신규)
- 공용 헬퍼: `SeveranceService.resolveRetirementType` 재사용
- 계산 공식: 기존 `SeveranceEstimateService.calculateOneRow` 와 동일 (근로기준법 시행령 제2조 기준)

---

## 2. API 스펙 — 내 퇴직금 예상

| 항목 | 값 |
|---|---|
| Method | `GET` |
| Path | `/pay/my/severance-estimate` |
| Header | `X-User-Company: {UUID}`, `X-User-Id: {empId}` |
| Query | `baseDate` (선택, `YYYY-MM-DD`, 미지정 시 오늘) |
| 응답 | `MySeveranceEstimateResDto` |

### 응답 필드 구성

- **사원 정보**: empId, empName, deptName, gradeName, retirementType, hireDate
- **근속**: baseDate, serviceDays, serviceYears
- **평균임금 구성**: last3MonthPay, lastYearBonus, annualLeaveAllowance, last3MonthDays, avgDailyWage
- **추계 결과**: estimatedSeverance, dcDepositedTotal (DC 한정), dcDiffAmount (DC 한정), displayAmount
- **메타**: calculatedAt (응답 생성 시각)

---

## 3. 처리 절차 (MySeveranceService.getMyEstimate)

1. **`baseDate` 보정** — null 이면 `LocalDate.now()`
2. **캐시 확인** — `cacheService.getSeveranceEstimateCache(companyId, empId, baseDate, MySeveranceEstimateResDto.class)` Hit 시 즉시 반환
3. **사원 조회** — `employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId)`, 없으면 `EMPLOYEE_NOT_FOUND`
4. **근속 기간 계산**
   - `serviceDays = baseDate - hireDate` (ChronoUnit.DAYS)
   - `serviceYears = serviceDays / 365` (소수점 2자리, HALF_UP)
   - **근속 1년 미만이면 퇴직금 0 으로 정상 응답** (예외 아님)
5. **퇴직 유형 판정** — `severanceService.resolveRetirementType(emp, companyId)` → `severance` / `DB` / `DC`
6. **평균임금 산정**
   - `last3Months = baseDate-3개월 ~ baseDate-1개월`
   - `last12Months = baseDate-12개월 ~ baseDate-1개월`
   - `last3MonthPay = sumLast3MonthPay(empId, companyId, last3Months)`
   - `lastYearBonus = sumLastYearBonus(empId, companyId, last12Months)`
   - `annualLeaveAllowance = 0L` (TODO: 연차수당 모듈 연동)
   - `bonusAdded = lastYearBonus × 3 / 12`
   - `totalWageBase = last3MonthPay + bonusAdded + annualLeaveAllowance`
   - `last3MonthDays = baseDate - baseDate.minusMonths(3)`
   - `avgDailyWage = totalWageBase / last3MonthDays` (소수점 2자리, HALF_UP)
7. **예상 퇴직금**: `avgDailyWage × 30 × serviceDays / 365` (원 단위 FLOOR)
8. **DC 형 차액 처리**
   - `retirementType == DC` 인 경우: `dcDepositedTotal`, `dcDiffAmount = max(0, estimatedSeverance - dcDepositedTotal)`, `displayAmount = dcDiffAmount`
   - 그 외: `dcDepositedTotal/dcDiffAmount = null`, `displayAmount = estimatedSeverance`
9. **DTO 빌드 + 캐시 저장**

---

## 4. 계산 공식 참고 (근로기준법 시행령 제2조)

```
평균임금 = (직전 3개월 임금 총액 + 상여금 × 3/12 + 연차수당 × 3/12) / 총일수

예상 퇴직금 = 평균임금 × 30일 × (근속일수 / 365일)

DC형 차액 = max(0, 예상 퇴직금 - 이미 적립된 DC 금액)
```

- **상여금**: 직전 12개월 동안 받은 상여금의 3/12 만 평균임금에 포함 (판례 95다2562)
- **연차수당**: 직전 1년 치 연차수당의 3/12 (미구현 — TODO)
- **근속 1년 미만**: 퇴직금 없음 (0 처리)

---

## 5. 캐싱 전략

| 대상 | 키 | 적용 API | 무효화 시점 |
|---|---|---|---|
| 퇴직금 추계 | `(companyId, empId, baseDate)` | `/pay/my/severance-estimate` | 급여확정(PayrollPaid), 연봉계약 변경, DC적립 완료 이벤트 |

- `baseDate` 가 쿼리마다 다를 수 있어 key 에 포함
- Redis Miss 시 DB 조회 + 계산 → 캐시 저장
- **TTL 짧게 권장** (예: 1시간) — 임금 데이터는 월 단위라 길게 둬도 되지만, 급여확정 이벤트로 즉시 무효화 못 할 경우 TTL 이 안전망

---

## 6. 예외 처리

| ErrorCode | 발생 조건 |
|---|---|
| `EMPLOYEE_NOT_FOUND` | 본인 사원 정보가 없는 경우 |
| `INVALID_BASE_DATE` | `baseDate` 가 입사일 이전인 경우 (선택) |

근속 1년 미만은 **예외가 아니라 정상 응답**(`estimatedSeverance: 0, serviceYears: 0.xx`). UI 에서 "근속 1년 이상부터 발생" 안내 표시.

---

## 7. 보안 / 접근 제어

- `@RoleRequired` 미부착 → 전 사원 호출 가능
- 모든 조회에 `empId` + `companyId` 동반 → **본인·자기 회사 데이터**만 접근
- `X-User-Id` 는 게이트웨이가 JWT 에서 파싱해 주입 (위조 방지)
- **다른 사원의 empId 를 파라미터로 받지 않음** — 헤더의 본인 empId 만 사용

---

## 8. 파일 위치 요약

| 유형 | 경로 |
|---|---|
| Controller | `hr-service/pay/controller/MySalaryController.java` (기존 파일에 엔드포인트 추가) |
| Service | `hr-service/pay/service/MySeveranceService.java` (신규) |
| DTO | `hr-service/pay/dtos/MySeveranceEstimateResDto.java` (신규) |
| Cache | `hr-service/pay/cache/MySalaryCacheService.java` (기존 파일에 메서드 추가) |
| 재사용 | `SeveranceService.resolveRetirementType`, `SeverancePaysRepository` (sum 메서드들), `EmployeeRepository` |

---

## 9. 흐름 요약 다이어그램

```
[사원] 급여 탭 > 내 퇴직금 예상 진입
  └─ GET /pay/my/severance-estimate?baseDate=2026-04-24
        ├─ Redis 캐시 조회 (Hit → 즉시 반환)
        └─ Miss:
              ├─ Employee 조회 (본인)
              ├─ 근속 계산 (baseDate - hireDate)
              ├─ resolveRetirementType (severance / DB / DC)
              ├─ 직전 3개월 임금 + 12개월 상여 합계 조회
              ├─ 평균임금 / 예상퇴직금 / (DC 차액) 계산
              └─ MySeveranceEstimateResDto 빌드 → Redis 저장 → 반환
```

---

## 10. 서비스 구현 전체 코드 (`MySeveranceService`)

**경로**: `hr-service/src/main/java/com/peoplecore/pay/service/MySeveranceService.java`

```java
package com.peoplecore.pay.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.service.MySalaryCacheService;
import com.peoplecore.pay.dtos.MySeveranceEstimateResDto;
import com.peoplecore.pay.enums.RetirementType;
import com.peoplecore.pay.repository.SeverancePaysRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 전직원 개인용 "내 퇴직금 예상" 조회 서비스.
 * 관리자용 SeveranceEstimateService 의 단일 사원 버전.
 * 계산 공식은 동일 — 근로기준법 시행령 제2조 + 대법 95다2562 판례.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MySeveranceService {

    private final EmployeeRepository employeeRepository;
    private final SeverancePaysRepository severancePaysRepository;
    private final SeveranceService severanceService;          // resolveRetirementType 재사용
    private final MySalaryCacheService cacheService;

    public MySeveranceEstimateResDto getMyEstimate(UUID companyId, Long empId, LocalDate baseDate) {
        // 1. baseDate 보정
        if (baseDate == null) baseDate = LocalDate.now();

        // 2. 캐시 확인
        MySeveranceEstimateResDto cached = cacheService.getSeveranceEstimateCache(
                companyId, empId, baseDate, MySeveranceEstimateResDto.class);
        if (cached != null) return cached;

        // 3. 사원 조회
        Employee emp = employeeRepository
                .findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        LocalDate hireDate = emp.getEmpHireDate();

        // 4. 근속 계산
        long serviceDays = ChronoUnit.DAYS.between(hireDate, baseDate);
        BigDecimal serviceYears = serviceDays > 0
                ? BigDecimal.valueOf(serviceDays)
                    .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 5. 근속 1년 미만 → 퇴직금 0 (정상 응답)
        if (serviceDays < 365) {
            MySeveranceEstimateResDto lessThanOneYear = MySeveranceEstimateResDto.builder()
                    .empId(emp.getEmpId())
                    .empName(emp.getEmpName())
                    .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                    .gradeName(emp.getGrade() != null ? emp.getGrade().getGradeName() : null)
                    .retirementType("severance")
                    .hireDate(hireDate)
                    .baseDate(baseDate)
                    .serviceDays(serviceDays)
                    .serviceYears(serviceYears)
                    .last3MonthPay(0L)
                    .lastYearBonus(0L)
                    .annualLeaveAllowance(0L)
                    .last3MonthDays(0)
                    .avgDailyWage(BigDecimal.ZERO)
                    .estimatedSeverance(0L)
                    .dcDepositedTotal(null)
                    .dcDiffAmount(null)
                    .displayAmount(0L)
                    .calculatedAt(LocalDateTime.now())
                    .build();
            cacheService.cacheSeveranceEstimate(companyId, empId, baseDate, lessThanOneYear);
            return lessThanOneYear;
        }

        // 6. 퇴직 유형
        RetirementType rt = severanceService.resolveRetirementType(emp, companyId);

        // 7. 직전 3개월 / 12개월 집계
        YearMonth baseYm = YearMonth.from(baseDate);
        List<String> last3Months = buildMonthRange(
                baseYm.minusMonths(3), baseYm.minusMonths(1), new ArrayList<>());
        List<String> last12Months = buildMonthRange(
                baseYm.minusMonths(12), baseYm.minusMonths(1), new ArrayList<>());

        long last3MonthPay = nz(severancePaysRepository.sumLast3MonthPay(
                empId, companyId, last3Months));
        long lastYearBonus = nz(severancePaysRepository.sumLastYearBonus(
                empId, companyId, last12Months));
        long annualLeaveAllowance = 0L;                         // TODO: 연차수당 모듈 연동
        long bonusAdded = lastYearBonus * 3 / 12;
        long totalWageBase = last3MonthPay + bonusAdded + annualLeaveAllowance;

        // 8. 평균임금
        int last3MonthDays = (int) ChronoUnit.DAYS.between(
                baseDate.minusMonths(3), baseDate);
        BigDecimal avgDailyWage = last3MonthDays > 0
                ? BigDecimal.valueOf(totalWageBase)
                    .divide(BigDecimal.valueOf(last3MonthDays), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 9. 예상 퇴직금 = 평균임금 × 30 × 근속일수 / 365
        long estimatedSeverance = avgDailyWage
                .multiply(BigDecimal.valueOf(30))
                .multiply(BigDecimal.valueOf(serviceDays))
                .divide(BigDecimal.valueOf(365), 0, RoundingMode.FLOOR)
                .longValue();

        // 10. DC 형 차액 처리
        Long dcDepositedTotal = null;
        Long dcDiffAmount = null;
        long displayAmount = estimatedSeverance;
        if (rt == RetirementType.DC) {
            dcDepositedTotal = nz(severancePaysRepository.sumDcDepositedTotal(empId, companyId));
            dcDiffAmount = Math.max(0L, estimatedSeverance - dcDepositedTotal);
            displayAmount = dcDiffAmount;
        }

        MySeveranceEstimateResDto result = MySeveranceEstimateResDto.builder()
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .gradeName(emp.getGrade() != null ? emp.getGrade().getGradeName() : null)
                .retirementType(rt.name())
                .hireDate(hireDate)
                .baseDate(baseDate)
                .serviceDays(serviceDays)
                .serviceYears(serviceYears)
                .last3MonthPay(last3MonthPay)
                .lastYearBonus(lastYearBonus)
                .annualLeaveAllowance(annualLeaveAllowance)
                .last3MonthDays(last3MonthDays)
                .avgDailyWage(avgDailyWage)
                .estimatedSeverance(estimatedSeverance)
                .dcDepositedTotal(dcDepositedTotal)
                .dcDiffAmount(dcDiffAmount)
                .displayAmount(displayAmount)
                .calculatedAt(LocalDateTime.now())
                .build();

        cacheService.cacheSeveranceEstimate(companyId, empId, baseDate, result);
        log.info("[MySeverance] estimate - empId={}, type={}, display={}",
                empId, rt, displayAmount);
        return result;
    }

    // ── helpers ──

    /** YearMonth 범위를 재귀로 "YYYY-MM" 문자열 리스트로 변환 (기존 Estimate 서비스와 동일 패턴) */
    private List<String> buildMonthRange(YearMonth from, YearMonth to, List<String> acc) {
        if (from.isAfter(to)) return acc;
        acc.add(from.toString());
        return buildMonthRange(from.plusMonths(1), to, acc);
    }

    private long nz(Long v) { return v != null ? v : 0L; }
}
```

---

## 11. Controller / DTO / Cache 추가분

### 11-1. `MySalaryController` 에 엔드포인트 추가

기존 `MySalaryController` 에 메서드 하나만 추가:

```java
// 기존 imports + 추가
import com.peoplecore.pay.dtos.MySeveranceEstimateResDto;
import com.peoplecore.pay.service.MySeveranceService;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

// 기존 필드 + 추가
private final MySeveranceService mySeveranceService;

/** 내 퇴직금 예상 조회 */
@GetMapping("/severance-estimate")
public ResponseEntity<MySeveranceEstimateResDto> getMyEstimate(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader("X-User-Id") Long empId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {
    return ResponseEntity.ok(mySeveranceService.getMyEstimate(companyId, empId, baseDate));
}
```

### 11-2. `MySeveranceEstimateResDto` — 응답 DTO

**경로**: `hr-service/src/main/java/com/peoplecore/pay/dtos/MySeveranceEstimateResDto.java`

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class MySeveranceEstimateResDto {

    // ── 사원 정보 ──
    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private String retirementType;      // "severance" / "DB" / "DC"

    // ── 근속 ──
    private LocalDate hireDate;
    private LocalDate baseDate;
    private Long serviceDays;
    private BigDecimal serviceYears;    // 소수점 2자리

    // ── 평균임금 구성 (계산 투명성) ──
    private Long last3MonthPay;
    private Long lastYearBonus;
    private Long annualLeaveAllowance;
    private Integer last3MonthDays;
    private BigDecimal avgDailyWage;

    // ── 추계 결과 ──
    private Long estimatedSeverance;    // 공식에 의한 예상 퇴직금 전체
    private Long dcDepositedTotal;      // DC형 전용, 그 외 null
    private Long dcDiffAmount;          // DC형 전용, 그 외 null
    private Long displayAmount;         // 화면에 크게 보여줄 값 (severance/DB=estimatedSeverance, DC=dcDiffAmount)

    // ── 메타 ──
    private LocalDateTime calculatedAt;
}
```

### 11-3. `MySalaryCacheService` 에 메서드 추가

기존 `MySalaryCacheService` 에 3개 메서드 추가:

```java
import com.peoplecore.pay.dtos.MySeveranceEstimateResDto;
import java.time.LocalDate;

// ... 기존 메서드들 아래에 추가

// ── 내 퇴직금 예상 ──
public <T> T getSeveranceEstimateCache(
        UUID companyId, Long empId, LocalDate baseDate, Class<T> clazz) {
    return null;   // no-op (Redis 도입 시 교체)
}
public void cacheSeveranceEstimate(
        UUID companyId, Long empId, LocalDate baseDate, MySeveranceEstimateResDto value) {
    /* no-op */
}
public void evictSeveranceEstimateCache(UUID companyId, Long empId) {
    /* no-op */
}
```

캐시 키 예시 (Redis 도입 시): `mysalary:severance-estimate:{companyId}:{empId}:{baseDate}`

---

## 12. 기존 코드 재사용 포인트

| 재사용 대상 | 용도 | 이 서비스에서의 호출 |
|-----------|------|-------------------|
| `SeveranceService#resolveRetirementType(emp, companyId)` | 사원의 retirementType (severance/DB/DC) 판정 — 회사 기본 설정 + 사원 개별 지정 반영 | `severanceService.resolveRetirementType(emp, companyId)` |
| `SeverancePaysRepository#sumLast3MonthPay(empId, companyId, months)` | 직전 3개월 지급임금 합계 | 평균임금 base |
| `SeverancePaysRepository#sumLastYearBonus(empId, companyId, months)` | 직전 12개월 상여 합계 | 3/12 가산 |
| `SeverancePaysRepository#sumDcDepositedTotal(empId, companyId)` | DC 형 누적 적립금 | 차액 계산 |
| `EmployeeRepository#findByEmpIdAndCompany_CompanyId` | 본인 사원 조회 | 진입 검증 |

**재사용 원칙**: 관리자용 `SeveranceEstimateService` 와 **동일 공식** 을 보장해야 "관리자가 보는 나의 추계"와 "내가 보는 추계" 가 일치. 따라서 공식 변경 시 두 서비스에 같이 반영하거나, 공식을 별도 helper 로 뽑아서 공유하는 리팩터링 고려.

---

## 13. 테스트 시나리오

| # | 시나리오 | 기대 결과 |
|---|---------|----------|
| 1 | 근속 3년 사원, retirementType=severance, baseDate=오늘 | `estimatedSeverance = displayAmount`, dc 필드 null |
| 2 | 근속 5년 사원, retirementType=DC, 이미 2000만원 적립 | `dcDiffAmount = max(0, estimatedSeverance - 20000000)`, `displayAmount = dcDiffAmount` |
| 3 | 근속 6개월 사원 | `estimatedSeverance = 0, serviceYears < 1.0` (정상 응답, 에러 아님) |
| 4 | 다른 사원의 empId 를 억지로 헤더에 넣으려 시도 | 게이트웨이가 JWT 기준 헤더를 덮어쓰므로 본인 데이터만 조회됨 |
| 5 | `baseDate` 미지정 | 오늘 날짜 기준 계산 |
| 6 | `baseDate` 가 미래 날짜 (예: 2030-01-01) | 계산은 되지만 "미래 시점 추계" 로 기록. UI 에서 제한 고려 |
| 7 | 같은 baseDate 로 재조회 | 캐시 hit → Redis/스텁에서 바로 반환 |
| 8 | 급여 지급 확정 이벤트 발생 후 재조회 | 캐시 invalidate 되어 재계산된 값 반영 (캐시 무효화 훅 구현 후) |

---

## 14. TODO / 개선 여지

- [ ] **연차수당 연동**: 현재 `annualLeaveAllowance = 0L` 하드코딩. 연차수당 모듈 완성 후 `LeaveAllowanceRepository.sumLastYearByEmpId` 같은 조회 추가
- [ ] **급여확정 이벤트로 자동 invalidate**: 급여대장 CONFIRMED → `MySalaryCacheService.evictSeveranceEstimateCache` 호출하는 consumer 추가
- [ ] **계산 공식 공통화**: `SeveranceEstimateService.calculateOneRow` 와 `MySeveranceService.getMyEstimate` 의 계산 로직이 중복 → `SeveranceCalculator` 유틸 클래스로 추출
- [ ] **baseDate 유효성**: 입사일 이전, 혹은 극단적 미래 날짜 방어

---

## 15. 흐름 전체 예시

사원 A(empId=7, 입사 2023-01-01, DC형, 월급 500만원 가정)가 2026-04-24 기준 조회:

```
GET /pay/my/severance-estimate?baseDate=2026-04-24
  X-User-Company: <UUID>
  X-User-Id: 7

→ hr-service MySalaryController.getMyEstimate()
→ MySeveranceService.getMyEstimate(companyId, 7, 2026-04-24)

계산:
  serviceDays  = 1209 일
  serviceYears = 3.31
  last3MonthPay  = 15,000,000 (1~3월)
  lastYearBonus  = 3,000,000
  bonusAdded     = 750,000
  totalWageBase  = 15,750,000
  last3MonthDays = 91
  avgDailyWage   = 173,076.92
  estimatedSeverance = 173,076 × 30 × 1209 / 365 = 17,187,XXX
  dcDepositedTotal   = 10,000,000 (이미 적립됨)
  dcDiffAmount       = 7,187,XXX
  displayAmount      = 7,187,XXX

응답 JSON:
{
  "empId": 7,
  "empName": "홍길동",
  "retirementType": "DC",
  "hireDate": "2023-01-01",
  "baseDate": "2026-04-24",
  "serviceDays": 1209,
  "serviceYears": 3.31,
  "last3MonthPay": 15000000,
  "lastYearBonus": 3000000,
  "annualLeaveAllowance": 0,
  "last3MonthDays": 91,
  "avgDailyWage": 173076.92,
  "estimatedSeverance": 17187XXX,
  "dcDepositedTotal": 10000000,
  "dcDiffAmount": 7187XXX,
  "displayAmount": 7187XXX,
  "calculatedAt": "2026-04-24T14:30:00"
}
```

프론트는 **`displayAmount` 를 메인**으로 표시하고, 접기 영역에 `평균임금 구성` / `근속 내역` / `DC 적립 현황` 을 세부 breakdown 으로 렌더.
