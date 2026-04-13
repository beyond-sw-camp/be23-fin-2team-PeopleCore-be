# 퇴직금대장(작성) - 백엔드 코드

> Admin 화면 — 퇴직 사원의 퇴직금 산정, 확정, 지급 처리
> 흐름: **산정중(CALCULATING) → 확정(CONFIRMED) → 지급완료(PAID)**
> 퇴직금 = 1일 평균임금 × 30일 × 근속연수

---

## API 목록

| # | Method | URL | 설명 |
|---|--------|-----|------|
| 1 | GET | `/pay/admin/severance` | 퇴직금대장 목록 조회 (기간 필터) |
| 2 | POST | `/pay/admin/severance/calculate` | 퇴직금 산정 (퇴직 사원 대상) |
| 3 | GET | `/pay/admin/severance/{sevId}` | 퇴직금 상세 |
| 4 | PUT | `/pay/admin/severance/{sevId}/confirm` | 퇴직금 확정 |
| 5 | PUT | `/pay/admin/severance/{sevId}/pay` | 지급 처리 |

---

## 파일 체크리스트

| # | 구분 | 파일명 | 위치 | 작업 |
|---|------|--------|------|------|
| 1 | Entity | `SeverancePays.java` | pay/domain/ | FK 변경(empId→Employee) + 상태변경 메서드 + @Index |
| 2 | Entity | `RetirementPensionDeposits.java` | pay/domain/ | FK 변경(empId→Employee, payrollRunId→PayrollRuns) + @Index |
| 3 | Repository | `SeverancePaysRepository.java` | pay/repository/ | 신규 |
| 4 | Repository | `RetirementPensionDepositsRepository.java` | pay/repository/ | 신규 |
| 5 | DTO | `SeveranceListResDto.java` | pay/dtos/ | 신규 (목록용) |
| 6 | DTO | `SeveranceDetailResDto.java` | pay/dtos/ | 신규 (상세용) |
| 7 | DTO | `SeveranceCalcReqDto.java` | pay/dtos/ | 신규 (산정 요청) |
| 8 | Service | `SeveranceService.java` | pay/service/ | 신규 |
| 9 | Controller | `SeveranceController.java` | pay/controller/ | 신규 |
| 10 | ErrorCode | `ErrorCode.java` | common/ | 추가 |

---

## 1. Entity 수정

### SeverancePays.java (수정)
**파일 위치**: `pay/domain/SeverancePays.java`

> FK 변경: `Long empId` → `@ManyToOne Employee`
> 상태 변경 메서드, 산정 값 갱신 메서드 추가
> `transferDate` nullable로 변경 (산정 시 아직 미정)

```java
package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.pay.enums.SevStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "severance_pays",
    indexes = {
        @Index(name = "idx_sev_company_status", columnList = "company_id, sev_status"),
        @Index(name = "idx_sev_emp", columnList = "emp_id")
    })
public class SeverancePays extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sevId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDate resignDate;           // 퇴직일

    // 근속연수
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal serviceYears;

    @Column(nullable = false)
    private Long avg3monthPay;              // 최근 3개월 평균 임금

    // 퇴직금산출액 = 1일평균임금 × 30 × 근속연수
    @Column(nullable = false)
    private Long severanceAmount;

    // 퇴직소득세
    @Builder.Default
    @Column(nullable = false)
    private Long taxAmount = 0L;

    // 실지급액 = 퇴직금 - 퇴직소득세
    @Column(nullable = false)
    private Long netAmount;

    // 지급일 (산정 시 미정 → null 허용)
    private LocalDate transferDate;

    // 퇴직금상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SevStatus sevStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // 확정자
    private Long confirmedBy;
    private LocalDateTime confirmedAt;

    // 지급처리자
    private Long paidBy;
    private LocalDateTime paidAt;


    // ── 상태 변경: 확정 ──
    public void confirm(Long confirmedBy) {
        if (this.sevStatus != SevStatus.CALCULATING) {
            throw new IllegalStateException("산정중 상태에서만 확정 가능합니다.");
        }
        this.sevStatus = SevStatus.CONFIRMED;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = LocalDateTime.now();
    }

    // ── 상태 변경: 지급완료 ──
    public void markPaid(Long paidBy, LocalDate transferDate) {
        if (this.sevStatus != SevStatus.CONFIRMED) {
            throw new IllegalStateException("확정 상태에서만 지급처리 가능합니다.");
        }
        this.sevStatus = SevStatus.PAID;
        this.paidBy = paidBy;
        this.paidAt = LocalDateTime.now();
        this.transferDate = transferDate;
    }
}
```

---

### RetirementPensionDeposits.java (수정)
**파일 위치**: `pay/domain/RetirementPensionDeposits.java`

> FK 변경: `Long empId` → `@ManyToOne Employee`, `Long payrollRunId` → `@ManyToOne PayrollRuns`
> 상태 변경 메서드 추가

```java
package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.pay.enums.DepStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "retirement_pension_deposits",
    indexes = {
        @Index(name = "idx_dep_company_status", columnList = "company_id, dep_status"),
        @Index(name = "idx_dep_emp", columnList = "emp_id")
    })
public class RetirementPensionDeposits {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long depId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    // 적립기준임금
    @Column(nullable = false)
    private Long baseAmount;

    // 적립금액 : 연간임금/12
    @Column(nullable = false)
    private Long depositAmount;

    private LocalDateTime depositDate;

    // 퇴직연금 상태 (적립예정, 완료)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepStatus depStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRuns payrollRuns;


    // ── 적립 완료 처리 ──
    public void markCompleted() {
        if (this.depStatus != DepStatus.SCHEDULED) {
            throw new IllegalStateException("적립예정 상태에서만 완료 처리 가능합니다.");
        }
        this.depStatus = DepStatus.COMPLETED;
        this.depositDate = LocalDateTime.now();
    }
}
```

---

## 2. Repository

### SeverancePaysRepository.java (신규)
**파일 위치**: `pay/repository/SeverancePaysRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.SeverancePays;
import com.peoplecore.pay.enums.SevStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeverancePaysRepository extends JpaRepository<SeverancePays, Long> {

    // 회사 + 기간 조회 (퇴직일 기준, JOIN FETCH)
    @Query("SELECT s FROM SeverancePays s " +
           "JOIN FETCH s.employee e " +
           "JOIN FETCH e.dept " +
           "WHERE s.company.companyId = :companyId " +
           "AND s.resignDate BETWEEN :startDate AND :endDate " +
           "ORDER BY s.resignDate DESC")
    List<SeverancePays> findAllByPeriod(
            @Param("companyId") UUID companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // 단건 조회
    Optional<SeverancePays> findBySevIdAndCompany_CompanyId(Long sevId, UUID companyId);

    // 해당 사원의 퇴직금 존재 여부 (중복 방지)
    boolean existsByEmployee_EmpIdAndCompany_CompanyId(Long empId, UUID companyId);
}
```

### RetirementPensionDepositsRepository.java (신규)
**파일 위치**: `pay/repository/RetirementPensionDepositsRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.RetirementPensionDeposits;
import com.peoplecore.pay.enums.DepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RetirementPensionDepositsRepository extends JpaRepository<RetirementPensionDeposits, Long> {

    // 회사 + 사원별 적립 이력
    @Query("SELECT d FROM RetirementPensionDeposits d " +
           "JOIN FETCH d.employee e " +
           "WHERE d.company.companyId = :companyId " +
           "AND d.employee.empId = :empId " +
           "ORDER BY d.depId DESC")
    List<RetirementPensionDeposits> findByCompanyAndEmp(
            @Param("companyId") UUID companyId,
            @Param("empId") Long empId);

    // 특정 급여대장의 DC 적립 내역
    List<RetirementPensionDeposits> findByPayrollRuns_PayrollRunId(Long payrollRunId);
}
```

---

## 3. DTO

### SeveranceCalcReqDto.java (신규)
**파일 위치**: `pay/dtos/SeveranceCalcReqDto.java`

> 퇴직금 산정 요청

```java
package com.peoplecore.pay.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeveranceCalcReqDto {

    @NotNull(message = "사원 ID는 필수입니다.")
    private Long empId;
}
```

### SeveranceListResDto.java (신규)
**파일 위치**: `pay/dtos/SeveranceListResDto.java`

> 목록 행 — 퇴직금 대장 요약

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.SeverancePays;
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
public class SeveranceListResDto {

    private Long sevId;
    private Long empId;
    private String empName;
    private String deptName;
    private LocalDate hireDate;
    private LocalDate resignDate;
    private BigDecimal serviceYears;
    private Long avg3monthPay;
    private Long severanceAmount;
    private Long taxAmount;
    private Long netAmount;
    private String sevStatus;
    private LocalDate transferDate;

    public static SeveranceListResDto fromEntity(SeverancePays s) {
        return SeveranceListResDto.builder()
                .sevId(s.getSevId())
                .empId(s.getEmployee().getEmpId())
                .empName(s.getEmployee().getEmpName())
                .deptName(s.getEmployee().getDept().getDeptName())
                .hireDate(s.getEmployee().getEmpHireDate())
                .resignDate(s.getResignDate())
                .serviceYears(s.getServiceYears())
                .avg3monthPay(s.getAvg3monthPay())
                .severanceAmount(s.getSeveranceAmount())
                .taxAmount(s.getTaxAmount())
                .netAmount(s.getNetAmount())
                .sevStatus(s.getSevStatus().name())
                .transferDate(s.getTransferDate())
                .build();
    }
}
```

### SeveranceDetailResDto.java (신규)
**파일 위치**: `pay/dtos/SeveranceDetailResDto.java`

> 상세 — 퇴직금 산정 내역 전체

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.SeverancePays;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeveranceDetailResDto {

    private Long sevId;
    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;

    // 근무 기간
    private LocalDate hireDate;
    private LocalDate resignDate;
    private BigDecimal serviceYears;    // 근속연수

    // 산정 내역
    private Long avg3monthPay;          // 최근 3개월 평균 임금
    private Long dailyAvgPay;           // 1일 평균 임금 (3개월 평균 / 91일)
    private Long severanceAmount;       // 퇴직금 산출액
    private Long taxAmount;             // 퇴직소득세
    private Long netAmount;             // 실지급액

    // 최근 3개월 급여 내역 (참고용)
    private List<MonthlyPayDto> recentPayHistory;

    // 상태
    private String sevStatus;
    private LocalDate transferDate;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Long paidBy;
    private LocalDateTime paidAt;

    // 사원 퇴직연금 정보
    private String retirementType;      // severance / DB / DC

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyPayDto {
        private String payYearMonth;
        private Long totalPay;
    }

    public static SeveranceDetailResDto fromEntity(SeverancePays s,
                                                    Long dailyAvgPay,
                                                    List<MonthlyPayDto> recentPay) {
        return SeveranceDetailResDto.builder()
                .sevId(s.getSevId())
                .empId(s.getEmployee().getEmpId())
                .empName(s.getEmployee().getEmpName())
                .deptName(s.getEmployee().getDept().getDeptName())
                .gradeName(s.getEmployee().getGrade().getGradeName())
                .hireDate(s.getEmployee().getEmpHireDate())
                .resignDate(s.getResignDate())
                .serviceYears(s.getServiceYears())
                .avg3monthPay(s.getAvg3monthPay())
                .dailyAvgPay(dailyAvgPay)
                .severanceAmount(s.getSeveranceAmount())
                .taxAmount(s.getTaxAmount())
                .netAmount(s.getNetAmount())
                .recentPayHistory(recentPay)
                .sevStatus(s.getSevStatus().name())
                .transferDate(s.getTransferDate())
                .confirmedBy(s.getConfirmedBy())
                .confirmedAt(s.getConfirmedAt())
                .paidBy(s.getPaidBy())
                .paidAt(s.getPaidAt())
                .retirementType(s.getEmployee().getRetirementType().name())
                .build();
    }
}
```

---

## 4. Service

### SeveranceService.java (신규)
**파일 위치**: `pay/service/SeveranceService.java`

```java
package com.peoplecore.pay.service;

import com.peoplecore.common.ErrorCode;
import com.peoplecore.common.exception.BusinessException;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.pay.domain.PayrollDetails;
import com.peoplecore.pay.domain.PayrollRuns;
import com.peoplecore.pay.domain.SeverancePays;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.enums.SevStatus;
import com.peoplecore.pay.repository.PayrollDetailsRepository;
import com.peoplecore.pay.repository.PayrollRunsRepository;
import com.peoplecore.pay.repository.SeverancePaysRepository;
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
public class SeveranceService {

    @Autowired
    private SeverancePaysRepository severancePaysRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayrollRunsRepository payrollRunsRepository;

    @Autowired
    private PayrollDetailsRepository payrollDetailsRepository;


    /**
     * 퇴직금대장 목록 조회 (기간 필터)
     */
    public List<SeveranceListResDto> getSeveranceList(UUID companyId,
                                                       LocalDate startDate,
                                                       LocalDate endDate) {
        return severancePaysRepository.findAllByPeriod(companyId, startDate, endDate)
                .stream()
                .map(SeveranceListResDto::fromEntity)
                .collect(Collectors.toList());
    }


    /**
     * 퇴직금 산정
     *
     * 로직:
     * 1. 퇴직 사원 조회 (RESIGNED 상태 + empResign 있는 사원)
     * 2. 근속연수 계산: 입사일 ~ 퇴직일
     * 3. 최근 3개월 급여 합산 (PayrollDetails에서 PAYMENT 합계)
     * 4. 1일 평균임금 = 3개월 급여 합계 / 91일
     * 5. 퇴직금 = 1일 평균임금 × 30 × 근속연수
     * 6. 퇴직소득세는 0으로 초기화 (별도 세금 계산 로직 필요 시 추후 추가)
     */
    @Transactional
    public SeveranceDetailResDto calculateSeverance(UUID companyId, Long empId, Long adminId) {
        // 1. 사원 조회
        Employee employee = employeeRepository
                .findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // 퇴직 상태 확인
        if (employee.getEmpStatus() != EmpStatus.RESIGNED || employee.getEmpResign() == null) {
            throw new BusinessException(ErrorCode.SEVERANCE_NOT_RESIGNED);
        }

        // 중복 산정 방지
        if (severancePaysRepository.existsByEmployee_EmpIdAndCompany_CompanyId(empId, companyId)) {
            throw new BusinessException(ErrorCode.SEVERANCE_ALREADY_EXISTS);
        }

        // 1년 미만 근무자 체크
        LocalDate hireDate = employee.getEmpHireDate();
        LocalDate resignDate = employee.getEmpResign();
        long totalDays = ChronoUnit.DAYS.between(hireDate, resignDate);
        if (totalDays < 365) {
            throw new BusinessException(ErrorCode.SEVERANCE_UNDER_ONE_YEAR);
        }

        // 2. 근속연수 (소수점 2자리까지)
        BigDecimal serviceYears = BigDecimal.valueOf(totalDays)
                .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);

        // 3. 최근 3개월 급여 조회
        //    퇴직일 기준으로 직전 3개월의 PayrollRuns를 찾고, PayrollDetails에서 PAYMENT 합산
        List<SeveranceDetailResDto.MonthlyPayDto> recentPay = new ArrayList<>();
        long sum3monthPay = 0L;

        for (int i = 1; i <= 3; i++) {
            LocalDate targetMonth = resignDate.minusMonths(i);
            String payYearMonth = targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));

            Optional<PayrollRuns> runOpt = payrollRunsRepository
                    .findByCompany_CompanyIdAndPayYearMonth(companyId, payYearMonth);

            if (runOpt.isPresent()) {
                List<PayrollDetails> details = payrollDetailsRepository
                        .findByPayrollRunsAndEmployee_EmpId(runOpt.get(), empId);

                long monthTotal = details.stream()
                        .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
                        .mapToLong(PayrollDetails::getAmount)
                        .sum();

                sum3monthPay += monthTotal;
                recentPay.add(SeveranceDetailResDto.MonthlyPayDto.builder()
                        .payYearMonth(payYearMonth)
                        .totalPay(monthTotal)
                        .build());
            }
        }

        // 급여 데이터가 없는 경우 연봉계약 기반 추정
        if (sum3monthPay == 0L) {
            throw new BusinessException(ErrorCode.SEVERANCE_NO_PAY_DATA);
        }

        // 4. 평균임금 계산
        long avg3monthPay = sum3monthPay / 3;           // 월평균
        long dailyAvgPay = sum3monthPay / 91;           // 1일 평균 (3개월 = 91일)

        // 5. 퇴직금 = 1일평균임금 × 30 × 근속연수
        long severanceAmount = BigDecimal.valueOf(dailyAvgPay)
                .multiply(BigDecimal.valueOf(30))
                .multiply(serviceYears)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        // 6. 퇴직소득세 (기본 0, 추후 별도 로직 적용 가능)
        long taxAmount = 0L;
        long netAmount = severanceAmount - taxAmount;

        // 7. 저장
        SeverancePays severancePay = SeverancePays.builder()
                .employee(employee)
                .resignDate(resignDate)
                .serviceYears(serviceYears)
                .avg3monthPay(avg3monthPay)
                .severanceAmount(severanceAmount)
                .taxAmount(taxAmount)
                .netAmount(netAmount)
                .sevStatus(SevStatus.CALCULATING)
                .company(employee.getCompany())
                .build();

        severancePaysRepository.save(severancePay);

        return SeveranceDetailResDto.fromEntity(severancePay, dailyAvgPay, recentPay);
    }


    /**
     * 퇴직금 상세 조회
     */
    public SeveranceDetailResDto getSeveranceDetail(UUID companyId, Long sevId) {
        SeverancePays sev = severancePaysRepository
                .findBySevIdAndCompany_CompanyId(sevId, companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEVERANCE_NOT_FOUND));

        // 1일 평균임금 역산
        long dailyAvgPay = sev.getAvg3monthPay() * 3 / 91;

        // 최근 3개월 급여 이력 조회
        List<SeveranceDetailResDto.MonthlyPayDto> recentPay = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            LocalDate targetMonth = sev.getResignDate().minusMonths(i);
            String payYearMonth = targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));

            Optional<PayrollRuns> runOpt = payrollRunsRepository
                    .findByCompany_CompanyIdAndPayYearMonth(companyId, payYearMonth);

            if (runOpt.isPresent()) {
                List<PayrollDetails> details = payrollDetailsRepository
                        .findByPayrollRunsAndEmployee_EmpId(runOpt.get(), sev.getEmployee().getEmpId());

                long monthTotal = details.stream()
                        .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
                        .mapToLong(PayrollDetails::getAmount)
                        .sum();

                recentPay.add(SeveranceDetailResDto.MonthlyPayDto.builder()
                        .payYearMonth(payYearMonth)
                        .totalPay(monthTotal)
                        .build());
            }
        }

        return SeveranceDetailResDto.fromEntity(sev, dailyAvgPay, recentPay);
    }


    /**
     * 퇴직금 확정
     */
    @Transactional
    public void confirmSeverance(UUID companyId, Long sevId, Long adminId) {
        SeverancePays sev = severancePaysRepository
                .findBySevIdAndCompany_CompanyId(sevId, companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEVERANCE_NOT_FOUND));

        sev.confirm(adminId);
    }


    /**
     * 지급 처리
     */
    @Transactional
    public void paySeverance(UUID companyId, Long sevId, Long adminId) {
        SeverancePays sev = severancePaysRepository
                .findBySevIdAndCompany_CompanyId(sevId, companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEVERANCE_NOT_FOUND));

        sev.markPaid(adminId, LocalDate.now());
    }
}
```

---

## 5. Controller

### SeveranceController.java (신규)
**파일 위치**: `pay/controller/SeveranceController.java`

```java
package com.peoplecore.pay.controller;

import com.peoplecore.common.annotation.RoleRequired;
import com.peoplecore.pay.dtos.SeveranceCalcReqDto;
import com.peoplecore.pay.dtos.SeveranceDetailResDto;
import com.peoplecore.pay.dtos.SeveranceListResDto;
import com.peoplecore.pay.service.SeveranceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/admin/severance")
@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
public class SeveranceController {

    @Autowired
    private SeveranceService severanceService;

    /**
     * 퇴직금대장 목록 조회 (기간 필터)
     * GET /pay/admin/severance?startDate=2026-01-01&endDate=2026-04-30
     */
    @GetMapping
    public ResponseEntity<List<SeveranceListResDto>> getSeveranceList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {

        return ResponseEntity.ok(
                severanceService.getSeveranceList(companyId, startDate, endDate));
    }

    /**
     * 퇴직금 산정
     * POST /pay/admin/severance/calculate
     */
    @PostMapping("/calculate")
    public ResponseEntity<SeveranceDetailResDto> calculateSeverance(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long adminId,
            @Valid @RequestBody SeveranceCalcReqDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                severanceService.calculateSeverance(companyId, request.getEmpId(), adminId));
    }

    /**
     * 퇴직금 상세
     * GET /pay/admin/severance/{sevId}
     */
    @GetMapping("/{sevId}")
    public ResponseEntity<SeveranceDetailResDto> getSeveranceDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long sevId) {

        return ResponseEntity.ok(
                severanceService.getSeveranceDetail(companyId, sevId));
    }

    /**
     * 퇴직금 확정
     * PUT /pay/admin/severance/{sevId}/confirm
     */
    @PutMapping("/{sevId}/confirm")
    public ResponseEntity<Void> confirmSeverance(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long adminId,
            @PathVariable Long sevId) {

        severanceService.confirmSeverance(companyId, sevId, adminId);
        return ResponseEntity.ok().build();
    }

    /**
     * 지급 처리
     * PUT /pay/admin/severance/{sevId}/pay
     */
    @PutMapping("/{sevId}/pay")
    public ResponseEntity<Void> paySeverance(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long adminId,
            @PathVariable Long sevId) {

        severanceService.paySeverance(companyId, sevId, adminId);
        return ResponseEntity.ok().build();
    }
}
```

---

## 6. ErrorCode 추가

**파일 위치**: `common/ErrorCode.java` (기존 파일에 추가)

```java
// ── 퇴직금 ──
SEVERANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "퇴직금 데이터가 존재하지 않습니다."),
SEVERANCE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 퇴직금이 산정된 사원입니다."),
SEVERANCE_NOT_RESIGNED(HttpStatus.BAD_REQUEST, "퇴직 상태인 사원만 퇴직금 산정이 가능합니다."),
SEVERANCE_UNDER_ONE_YEAR(HttpStatus.BAD_REQUEST, "1년 미만 근무자는 퇴직금 지급 대상이 아닙니다."),
SEVERANCE_NO_PAY_DATA(HttpStatus.BAD_REQUEST, "최근 3개월 급여 데이터가 없어 퇴직금 산정이 불가합니다."),
EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND, "사원 정보를 찾을 수 없습니다."),
```

> `EMPLOYEE_NOT_FOUND`가 이미 존재하면 추가 불필요

---

## 퇴직금 산정 로직 요약

### 계산 공식

```
근속연수 = (퇴직일 - 입사일) / 365   (소수 2자리)
3개월 급여합계 = 퇴직 직전 3개월의 PayrollDetails(PAYMENT) 합산
1일 평균임금 = 3개월 급여합계 / 91일
퇴직금 = 1일 평균임금 × 30일 × 근속연수
실지급액 = 퇴직금 - 퇴직소득세
```

### 대상 조건
- `Employee.empStatus = RESIGNED`
- `Employee.empResign IS NOT NULL`
- 근속기간 1년(365일) 이상

### 참고: EmployeeRepository 추가 필요 메서드
```java
Optional<Employee> findByEmpIdAndCompany_CompanyId(Long empId, UUID companyId);
```
> 이미 존재할 가능성 높음. 없으면 추가 필요.

---

## 참고: DC형 퇴직연금 적립 (RetirementPensionDeposits)

DC형은 퇴직 시 일시 산정이 아니라 **매월 급여 산정 시 적립**합니다.
급여대장 생성 시 DC형 사원은 자동으로 RetirementPensionDeposits에 INSERT됩니다.

```
적립기준임금 = 해당 월 지급항목 합계
적립금액 = 연간임금총액 / 12 (연봉계약 기준)
```

이 로직은 PayrollService.createPayroll()에서 DC형 사원 판별 후 처리하면 됩니다.
현재 버전에서는 퇴직금대장에 포함하지 않고, 별도 관리합니다.
