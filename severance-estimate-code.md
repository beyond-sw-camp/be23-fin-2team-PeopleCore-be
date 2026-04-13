# 퇴직금추계액 - 백엔드 코드

> Admin 화면 — 재직 사원의 퇴직금 추계액 조회 (시뮬레이션)
> **실제 퇴직금이 아닌, "지금 퇴직했다면 얼마인지"** 추정하는 기능
> DB 저장 없이 실시간 계산 후 반환

---

## API 목록

| # | Method | URL | 설명 |
|---|--------|-----|------|
| 1 | GET | `/pay/admin/severance-estimate` | 전 사원 퇴직금 추계액 목록 |
| 2 | GET | `/pay/admin/severance-estimate/{empId}` | 특정 사원 퇴직금 추계 상세 |

---

## 파일 체크리스트

| # | 구분 | 파일명 | 위치 | 작업 |
|---|------|--------|------|------|
| 1 | DTO | `SeveranceEstimateResDto.java` | pay/dtos/ | 신규 (목록용) |
| 2 | DTO | `SeveranceEstimateDetailResDto.java` | pay/dtos/ | 신규 (상세용) |
| 3 | Service | `SeveranceEstimateService.java` | pay/service/ | 신규 |
| 4 | Controller | `SeveranceEstimateController.java` | pay/controller/ | 신규 |

> Entity/Repository 변경 없음 — 기존 PayrollRuns, PayrollDetails, Employee 조회만 사용

---

## 1. DTO

### SeveranceEstimateResDto.java (신규)
**파일 위치**: `pay/dtos/SeveranceEstimateResDto.java`

> 목록 행 — 사원별 추계액 요약

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeveranceEstimateResDto {

    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private LocalDate hireDate;
    private BigDecimal serviceYears;        // 현재 기준 근속연수
    private Long avg3monthPay;              // 최근 3개월 평균
    private Long estimatedSeverance;        // 추계 퇴직금
    private String retirementType;          // severance / DB / DC
}
```

### SeveranceEstimateDetailResDto.java (신규)
**파일 위치**: `pay/dtos/SeveranceEstimateDetailResDto.java`

> 상세 — 산정 근거 포함

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeveranceEstimateDetailResDto {

    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private LocalDate hireDate;
    private LocalDate estimateDate;         // 추계 기준일 (오늘)
    private BigDecimal serviceYears;

    // 산정 내역
    private Long sum3monthPay;              // 최근 3개월 급여 합계
    private Long avg3monthPay;              // 월평균
    private Long dailyAvgPay;              // 1일 평균 임금
    private Long estimatedSeverance;        // 추계 퇴직금

    // 최근 3개월 내역
    private List<MonthlyPayDto> recentPayHistory;

    private String retirementType;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyPayDto {
        private String payYearMonth;
        private Long totalPay;
    }
}
```

---

## 2. Service

### SeveranceEstimateService.java (신규)
**파일 위치**: `pay/service/SeveranceEstimateService.java`

```java
package com.peoplecore.pay.service;

import com.peoplecore.common.ErrorCode;
import com.peoplecore.common.exception.BusinessException;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.pay.domain.PayrollDetails;
import com.peoplecore.pay.domain.PayrollRuns;
import com.peoplecore.pay.dtos.SeveranceEstimateDetailResDto;
import com.peoplecore.pay.dtos.SeveranceEstimateResDto;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.repository.PayrollDetailsRepository;
import com.peoplecore.pay.repository.PayrollRunsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SeveranceEstimateService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayrollRunsRepository payrollRunsRepository;

    @Autowired
    private PayrollDetailsRepository payrollDetailsRepository;


    /**
     * 전 사원 퇴직금 추계액 목록
     * 재직중(ACTIVE) + 휴직중(ON_LEAVE) 사원 대상
     * 1년 미만 근무자는 0원 표시
     */
    public List<SeveranceEstimateResDto> getEstimateList(UUID companyId) {
        // 재직/휴직 사원 조회
        List<Employee> employees = employeeRepository
                .findByCompany_CompanyIdAndEmpStatusInAndDeleteAtIsNull(
                        companyId,
                        List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE));

        LocalDate today = LocalDate.now();
        List<SeveranceEstimateResDto> result = new ArrayList<>();

        for (Employee emp : employees) {
            long totalDays = ChronoUnit.DAYS.between(emp.getEmpHireDate(), today);
            BigDecimal serviceYears = BigDecimal.valueOf(totalDays)
                    .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);

            long estimatedSev = 0L;
            long avg3monthPay = 0L;

            // 1년 이상인 경우만 추계
            if (totalDays >= 365) {
                long sum3month = getRecent3MonthPaySum(companyId, emp.getEmpId(), today);
                if (sum3month > 0) {
                    avg3monthPay = sum3month / 3;
                    long dailyAvg = sum3month / 91;
                    estimatedSev = BigDecimal.valueOf(dailyAvg)
                            .multiply(BigDecimal.valueOf(30))
                            .multiply(serviceYears)
                            .setScale(0, RoundingMode.HALF_UP)
                            .longValue();
                }
            }

            result.add(SeveranceEstimateResDto.builder()
                    .empId(emp.getEmpId())
                    .empName(emp.getEmpName())
                    .deptName(emp.getDept().getDeptName())
                    .gradeName(emp.getGrade().getGradeName())
                    .hireDate(emp.getEmpHireDate())
                    .serviceYears(serviceYears)
                    .avg3monthPay(avg3monthPay)
                    .estimatedSeverance(estimatedSev)
                    .retirementType(emp.getRetirementType().name())
                    .build());
        }

        return result;
    }


    /**
     * 특정 사원 퇴직금 추계 상세
     */
    public SeveranceEstimateDetailResDto getEstimateDetail(UUID companyId, Long empId) {
        Employee emp = employeeRepository
                .findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));

        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(emp.getEmpHireDate(), today);
        BigDecimal serviceYears = BigDecimal.valueOf(totalDays)
                .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);

        // 최근 3개월 급여 이력
        List<SeveranceEstimateDetailResDto.MonthlyPayDto> recentPay = new ArrayList<>();
        long sum3monthPay = 0L;

        for (int i = 1; i <= 3; i++) {
            LocalDate targetMonth = today.minusMonths(i);
            String payYearMonth = targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));

            Optional<PayrollRuns> runOpt = payrollRunsRepository
                    .findByCompany_CompanyIdAndPayYearMonth(companyId, payYearMonth);

            long monthTotal = 0L;
            if (runOpt.isPresent()) {
                List<PayrollDetails> details = payrollDetailsRepository
                        .findByPayrollRunsAndEmployee_EmpId(runOpt.get(), empId);

                monthTotal = details.stream()
                        .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
                        .mapToLong(PayrollDetails::getAmount)
                        .sum();
            }

            sum3monthPay += monthTotal;
            recentPay.add(SeveranceEstimateDetailResDto.MonthlyPayDto.builder()
                    .payYearMonth(payYearMonth)
                    .totalPay(monthTotal)
                    .build());
        }

        long avg3monthPay = sum3monthPay > 0 ? sum3monthPay / 3 : 0;
        long dailyAvgPay = sum3monthPay > 0 ? sum3monthPay / 91 : 0;
        long estimatedSev = 0L;

        if (totalDays >= 365 && dailyAvgPay > 0) {
            estimatedSev = BigDecimal.valueOf(dailyAvgPay)
                    .multiply(BigDecimal.valueOf(30))
                    .multiply(serviceYears)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
        }

        return SeveranceEstimateDetailResDto.builder()
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .deptName(emp.getDept().getDeptName())
                .gradeName(emp.getGrade().getGradeName())
                .hireDate(emp.getEmpHireDate())
                .estimateDate(today)
                .serviceYears(serviceYears)
                .sum3monthPay(sum3monthPay)
                .avg3monthPay(avg3monthPay)
                .dailyAvgPay(dailyAvgPay)
                .estimatedSeverance(estimatedSev)
                .recentPayHistory(recentPay)
                .retirementType(emp.getRetirementType().name())
                .build();
    }


    // ── 내부 유틸: 최근 3개월 급여 합산 ──
    private long getRecent3MonthPaySum(UUID companyId, Long empId, LocalDate baseDate) {
        long sum = 0L;
        for (int i = 1; i <= 3; i++) {
            String payYearMonth = baseDate.minusMonths(i)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM"));

            Optional<PayrollRuns> runOpt = payrollRunsRepository
                    .findByCompany_CompanyIdAndPayYearMonth(companyId, payYearMonth);

            if (runOpt.isPresent()) {
                List<PayrollDetails> details = payrollDetailsRepository
                        .findByPayrollRunsAndEmployee_EmpId(runOpt.get(), empId);

                sum += details.stream()
                        .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
                        .mapToLong(PayrollDetails::getAmount)
                        .sum();
            }
        }
        return sum;
    }
}
```

---

## 3. Controller

### SeveranceEstimateController.java (신규)
**파일 위치**: `pay/controller/SeveranceEstimateController.java`

```java
package com.peoplecore.pay.controller;

import com.peoplecore.common.annotation.RoleRequired;
import com.peoplecore.pay.dtos.SeveranceEstimateDetailResDto;
import com.peoplecore.pay.dtos.SeveranceEstimateResDto;
import com.peoplecore.pay.service.SeveranceEstimateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/admin/severance-estimate")
@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
public class SeveranceEstimateController {

    @Autowired
    private SeveranceEstimateService severanceEstimateService;

    /**
     * 전 사원 퇴직금 추계액 목록
     * GET /pay/admin/severance-estimate
     */
    @GetMapping
    public ResponseEntity<List<SeveranceEstimateResDto>> getEstimateList(
            @RequestHeader("X-User-Company") UUID companyId) {

        return ResponseEntity.ok(
                severanceEstimateService.getEstimateList(companyId));
    }

    /**
     * 특정 사원 퇴직금 추계 상세
     * GET /pay/admin/severance-estimate/{empId}
     */
    @GetMapping("/{empId}")
    public ResponseEntity<SeveranceEstimateDetailResDto> getEstimateDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId) {

        return ResponseEntity.ok(
                severanceEstimateService.getEstimateDetail(companyId, empId));
    }
}
```

---

## 참고: EmployeeRepository 추가 필요 메서드

```java
// 재직/휴직 사원 조회 (소프트삭제 제외)
List<Employee> findByCompany_CompanyIdAndEmpStatusInAndDeleteAtIsNull(
        UUID companyId, List<EmpStatus> statuses);

// 단건 조회 (이미 존재할 수 있음)
Optional<Employee> findByEmpIdAndCompany_CompanyId(Long empId, UUID companyId);
```

---

## 추계액 산정 로직 요약

| 항목 | 공식 |
|------|------|
| 기준일 | 오늘 (LocalDate.now()) |
| 근속연수 | `(오늘 - 입사일) / 365` |
| 3개월 급여합계 | 직전 3개월 PayrollDetails(PAYMENT) 합산 |
| 1일 평균임금 | `3개월합계 / 91일` |
| 추계 퇴직금 | `1일평균 × 30 × 근속연수` |
| 1년 미만 | 0원 표시 (퇴직금 미대상) |

> DB 저장 없이 실시간 계산 — 매번 조회 시 현재 기준으로 재계산됨
