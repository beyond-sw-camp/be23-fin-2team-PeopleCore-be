# 정산보험료 - 백엔드 코드

> Admin 화면 — 월별 4대보험 정산 조회/생성
> 급여대장(PayrollRuns) 확정 시 보험료 자동 산정, 별도 조회 가능
> 보수월액 기반 × 요율 계산, 국민연금 상·하한 적용

---

## API 목록

| # | Method | URL | 설명 |
|---|--------|-----|------|
| 1 | GET | `/pay/admin/insurance-settlement` | 정산보험료 목록 조회 (특정 월) |
| 2 | POST | `/pay/admin/insurance-settlement/calculate` | 보험료 산정 (급여대장 기반) |
| 3 | GET | `/pay/admin/insurance-settlement/{settlementId}` | 사원별 보험료 상세 |

---

## 파일 체크리스트

| # | 구분 | 파일명 | 위치 | 작업 |
|---|------|--------|------|------|
| 1 | Entity | `InsuranceSettlement.java` | pay/domain/ | update 메서드 추가 + @Index |
| 2 | Repository | `InsuranceSettlementRepository.java` | pay/repository/ | 신규 |
| 3 | DTO | `InsuranceSettlementResDto.java` | pay/dtos/ | 신규 (목록용) |
| 4 | DTO | `InsuranceSettlementDetailResDto.java` | pay/dtos/ | 신규 (상세용) |
| 5 | DTO | `InsuranceSettlementSummaryResDto.java` | pay/dtos/ | 신규 (상단 요약) |
| 6 | Service | `InsuranceSettlementService.java` | pay/service/ | 신규 |
| 7 | Controller | `InsuranceSettlementController.java` | pay/controller/ | 신규 |
| 8 | ErrorCode | `ErrorCode.java` | common/ | 추가 |

---

## 1. Entity 수정

### InsuranceSettlement.java (수정)
**파일 위치**: `pay/domain/InsuranceSettlement.java`

> update 메서드 추가, @Index 추가, @Builder.Default 추가

```java
package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
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
@Table(name = "insurance_settlement",
    indexes = {
        @Index(name = "idx_settlement_company_month", columnList = "company_id, pay_year_month"),
        @Index(name = "idx_settlement_payroll_run", columnList = "payroll_run_id"),
        @Index(name = "idx_settlement_emp", columnList = "emp_id, pay_year_month")
    })
public class InsuranceSettlement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long settlementId;

    @Column(nullable = false, length = 7)
    private String payYearMonth;

    @Column(nullable = false)
    private Long baseSalary;            // 보수월액

    // ── 국민연금 ──
    @Column(nullable = false)
    private Long pensionEmployee;
    @Column(nullable = false)
    private Long pensionEmployer;

    // ── 건강보험 ──
    @Column(nullable = false)
    private Long healthEmployee;
    @Column(nullable = false)
    private Long healthEmployer;

    // ── 장기요양보험 ──
    @Column(nullable = false)
    private Long ltcEmployee;
    @Column(nullable = false)
    private Long ltcEmployer;

    // ── 고용보험 ──
    @Column(nullable = false)
    private Long employmentEmployee;
    @Column(nullable = false)
    private Long employmentEmployer;

    // ── 산재보험 (사업주만 부담) ──
    @Column(nullable = false)
    private Long industrialEmployer;

    // ── 합계 ──
    @Column(nullable = false)
    private Long totalEmployee;         // 근로자 부담 합계
    @Column(nullable = false)
    private Long totalEmployer;         // 사업주 부담 합계
    @Column(nullable = false)
    private Long totalAmount;           // 전체 합계

    @Builder.Default
    @Column(nullable = false)
    private Boolean isApplied = false;  // 급여 반영 여부
    private LocalDateTime appliedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRuns payrollRuns;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurance_rates", nullable = false)
    private InsuranceRates insuranceRates;


    // ── 급여 반영 처리 ──
    public void markApplied() {
        this.isApplied = true;
        this.appliedAt = LocalDateTime.now();
    }

    // ── 재산정 시 값 갱신 ──
    public void recalculate(Long baseSalary,
                            Long pensionEmp, Long pensionEmpr,
                            Long healthEmp, Long healthEmpr,
                            Long ltcEmp, Long ltcEmpr,
                            Long employmentEmp, Long employmentEmpr,
                            Long industrialEmpr) {
        this.baseSalary = baseSalary;
        this.pensionEmployee = pensionEmp;
        this.pensionEmployer = pensionEmpr;
        this.healthEmployee = healthEmp;
        this.healthEmployer = healthEmpr;
        this.ltcEmployee = ltcEmp;
        this.ltcEmployer = ltcEmpr;
        this.employmentEmployee = employmentEmp;
        this.employmentEmployer = employmentEmpr;
        this.industrialEmployer = industrialEmpr;
        this.totalEmployee = pensionEmp + healthEmp + ltcEmp + employmentEmp;
        this.totalEmployer = pensionEmpr + healthEmpr + ltcEmpr + employmentEmpr + industrialEmpr;
        this.totalAmount = this.totalEmployee + this.totalEmployer;
    }
}
```

---

## 2. Repository

### InsuranceSettlementRepository.java (신규)
**파일 위치**: `pay/repository/InsuranceSettlementRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.InsuranceSettlement;
import com.peoplecore.pay.domain.PayrollRuns;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InsuranceSettlementRepository extends JpaRepository<InsuranceSettlement, Long> {

    // 특정 급여대장의 전체 정산보험료
    List<InsuranceSettlement> findByPayrollRuns(PayrollRuns payrollRuns);

    // 회사 + 연월 조회 (JOIN FETCH로 N+1 방지)
    @Query("SELECT s FROM InsuranceSettlement s " +
           "JOIN FETCH s.employee e " +
           "JOIN FETCH e.dept " +
           "WHERE s.company.companyId = :companyId " +
           "AND s.payYearMonth = :payYearMonth " +
           "ORDER BY e.empName ASC")
    List<InsuranceSettlement> findAllWithEmployee(
            @Param("companyId") UUID companyId,
            @Param("payYearMonth") String payYearMonth);

    // 특정 정산 상세 (단건)
    Optional<InsuranceSettlement> findBySettlementIdAndCompany_CompanyId(
            Long settlementId, UUID companyId);

    // 해당 급여대장에 대한 정산 존재 여부
    boolean existsByPayrollRuns(PayrollRuns payrollRuns);

    // 해당 월 정산 삭제 (재산정 시)
    void deleteByPayrollRuns(PayrollRuns payrollRuns);
}
```

---

## 3. DTO

### InsuranceSettlementSummaryResDto.java (신규)
**파일 위치**: `pay/dtos/InsuranceSettlementSummaryResDto.java`

> 상단 요약 카드 (전체 합계)

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceSettlementSummaryResDto {

    private String payYearMonth;
    private Integer totalEmployees;

    // 전체 합산
    private Long totalBaseSalary;
    private Long totalPensionEmployee;
    private Long totalPensionEmployer;
    private Long totalHealthEmployee;
    private Long totalHealthEmployer;
    private Long totalLtcEmployee;
    private Long totalLtcEmployer;
    private Long totalEmploymentEmployee;
    private Long totalEmploymentEmployer;
    private Long totalIndustrialEmployer;
    private Long grandTotalEmployee;    // 근로자 부담 합계
    private Long grandTotalEmployer;    // 사업주 부담 합계

    // 사원별 목록
    private List<InsuranceSettlementResDto> settlements;
}
```

### InsuranceSettlementResDto.java (신규)
**파일 위치**: `pay/dtos/InsuranceSettlementResDto.java`

> 목록 행 — 사원별 보험료 요약

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.InsuranceSettlement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceSettlementResDto {

    private Long settlementId;
    private Long empId;
    private String empName;
    private String deptName;
    private Long baseSalary;            // 보수월액

    // 보험료 (근로자 + 사업주)
    private Long pensionEmployee;
    private Long healthEmployee;
    private Long ltcEmployee;
    private Long employmentEmployee;
    private Long industrialEmployer;    // 산재 (사업주만)

    private Long totalEmployee;         // 근로자 부담 합계
    private Long totalEmployer;         // 사업주 부담 합계

    public static InsuranceSettlementResDto fromEntity(InsuranceSettlement s) {
        return InsuranceSettlementResDto.builder()
                .settlementId(s.getSettlementId())
                .empId(s.getEmployee().getEmpId())
                .empName(s.getEmployee().getEmpName())
                .deptName(s.getEmployee().getDept().getDeptName())
                .baseSalary(s.getBaseSalary())
                .pensionEmployee(s.getPensionEmployee())
                .healthEmployee(s.getHealthEmployee())
                .ltcEmployee(s.getLtcEmployee())
                .employmentEmployee(s.getEmploymentEmployee())
                .industrialEmployer(s.getIndustrialEmployer())
                .totalEmployee(s.getTotalEmployee())
                .totalEmployer(s.getTotalEmployer())
                .build();
    }
}
```

### InsuranceSettlementDetailResDto.java (신규)
**파일 위치**: `pay/dtos/InsuranceSettlementDetailResDto.java`

> 상세 — 근로자/사업주 양쪽 금액 모두 표시

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.InsuranceSettlement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceSettlementDetailResDto {

    private Long settlementId;
    private String payYearMonth;
    private Long empId;
    private String empName;
    private String deptName;
    private Long baseSalary;

    // 요율 정보 (표시용)
    private BigDecimal pensionRate;
    private BigDecimal healthRate;
    private BigDecimal ltcRate;
    private BigDecimal employmentRate;
    private BigDecimal employmentEmployerRate;
    private BigDecimal industrialRate;

    // 국민연금
    private Long pensionEmployee;
    private Long pensionEmployer;
    // 건강보험
    private Long healthEmployee;
    private Long healthEmployer;
    // 장기요양
    private Long ltcEmployee;
    private Long ltcEmployer;
    // 고용보험
    private Long employmentEmployee;
    private Long employmentEmployer;
    // 산재보험
    private Long industrialEmployer;

    // 합계
    private Long totalEmployee;
    private Long totalEmployer;
    private Long totalAmount;

    private Boolean isApplied;

    public static InsuranceSettlementDetailResDto fromEntity(InsuranceSettlement s) {
        return InsuranceSettlementDetailResDto.builder()
                .settlementId(s.getSettlementId())
                .payYearMonth(s.getPayYearMonth())
                .empId(s.getEmployee().getEmpId())
                .empName(s.getEmployee().getEmpName())
                .deptName(s.getEmployee().getDept().getDeptName())
                .baseSalary(s.getBaseSalary())
                // 요율
                .pensionRate(s.getInsuranceRates().getNationalPension())
                .healthRate(s.getInsuranceRates().getHealthInsurance())
                .ltcRate(s.getInsuranceRates().getLongTermCare())
                .employmentRate(s.getInsuranceRates().getEmploymentInsurance())
                .employmentEmployerRate(s.getInsuranceRates().getEmploymentInsuranceEmployer())
                .industrialRate(s.getInsuranceRates().getIndustrialAccident())
                // 금액
                .pensionEmployee(s.getPensionEmployee())
                .pensionEmployer(s.getPensionEmployer())
                .healthEmployee(s.getHealthEmployee())
                .healthEmployer(s.getHealthEmployer())
                .ltcEmployee(s.getLtcEmployee())
                .ltcEmployer(s.getLtcEmployer())
                .employmentEmployee(s.getEmploymentEmployee())
                .employmentEmployer(s.getEmploymentEmployer())
                .industrialEmployer(s.getIndustrialEmployer())
                // 합계
                .totalEmployee(s.getTotalEmployee())
                .totalEmployer(s.getTotalEmployer())
                .totalAmount(s.getTotalAmount())
                .isApplied(s.getIsApplied())
                .build();
    }
}
```

---

## 4. Service

### InsuranceSettlementService.java (신규)
**파일 위치**: `pay/service/InsuranceSettlementService.java`

```java
package com.peoplecore.pay.service;

import com.peoplecore.common.ErrorCode;
import com.peoplecore.common.exception.BusinessException;
import com.peoplecore.pay.domain.*;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.enums.PayrollStatus;
import com.peoplecore.pay.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class InsuranceSettlementService {

    @Autowired
    private InsuranceSettlementRepository insuranceSettlementRepository;

    @Autowired
    private PayrollRunsRepository payrollRunsRepository;

    @Autowired
    private PayrollDetailsRepository payrollDetailsRepository;

    @Autowired
    private InsuranceRatesRepository insuranceRatesRepository;


    /**
     * 정산보험료 목록 조회 (특정 월)
     */
    public InsuranceSettlementSummaryResDto getSettlementList(UUID companyId, String payYearMonth) {
        List<InsuranceSettlement> settlements =
                insuranceSettlementRepository.findAllWithEmployee(companyId, payYearMonth);

        if (settlements.isEmpty()) {
            return InsuranceSettlementSummaryResDto.builder()
                    .payYearMonth(payYearMonth)
                    .totalEmployees(0)
                    .totalBaseSalary(0L)
                    .totalPensionEmployee(0L).totalPensionEmployer(0L)
                    .totalHealthEmployee(0L).totalHealthEmployer(0L)
                    .totalLtcEmployee(0L).totalLtcEmployer(0L)
                    .totalEmploymentEmployee(0L).totalEmploymentEmployer(0L)
                    .totalIndustrialEmployer(0L)
                    .grandTotalEmployee(0L).grandTotalEmployer(0L)
                    .settlements(List.of())
                    .build();
        }

        List<InsuranceSettlementResDto> dtoList = settlements.stream()
                .map(InsuranceSettlementResDto::fromEntity)
                .collect(Collectors.toList());

        // 합산
        long sumBase = 0, sumPenEmp = 0, sumPenEmpr = 0;
        long sumHlthEmp = 0, sumHlthEmpr = 0;
        long sumLtcEmp = 0, sumLtcEmpr = 0;
        long sumEmpInsEmp = 0, sumEmpInsEmpr = 0;
        long sumIndEmpr = 0;
        long sumTotalEmp = 0, sumTotalEmpr = 0;

        for (InsuranceSettlement s : settlements) {
            sumBase += s.getBaseSalary();
            sumPenEmp += s.getPensionEmployee();
            sumPenEmpr += s.getPensionEmployer();
            sumHlthEmp += s.getHealthEmployee();
            sumHlthEmpr += s.getHealthEmployer();
            sumLtcEmp += s.getLtcEmployee();
            sumLtcEmpr += s.getLtcEmployer();
            sumEmpInsEmp += s.getEmploymentEmployee();
            sumEmpInsEmpr += s.getEmploymentEmployer();
            sumIndEmpr += s.getIndustrialEmployer();
            sumTotalEmp += s.getTotalEmployee();
            sumTotalEmpr += s.getTotalEmployer();
        }

        return InsuranceSettlementSummaryResDto.builder()
                .payYearMonth(payYearMonth)
                .totalEmployees(settlements.size())
                .totalBaseSalary(sumBase)
                .totalPensionEmployee(sumPenEmp)
                .totalPensionEmployer(sumPenEmpr)
                .totalHealthEmployee(sumHlthEmp)
                .totalHealthEmployer(sumHlthEmpr)
                .totalLtcEmployee(sumLtcEmp)
                .totalLtcEmployer(sumLtcEmpr)
                .totalEmploymentEmployee(sumEmpInsEmp)
                .totalEmploymentEmployer(sumEmpInsEmpr)
                .totalIndustrialEmployer(sumIndEmpr)
                .grandTotalEmployee(sumTotalEmp)
                .grandTotalEmployer(sumTotalEmpr)
                .settlements(dtoList)
                .build();
    }


    /**
     * 보험료 산정 (급여대장 기반)
     *
     * 로직:
     * 1. PayrollRuns 조회 (CALCULATING 또는 CONFIRMED 상태)
     * 2. PayrollDetails에서 사원별 지급항목 합산 → 보수월액
     * 3. 해당 연도 InsuranceRates 조회
     * 4. 보수월액 × 요율 계산 (국민연금 상/하한 적용)
     * 5. InsuranceSettlement 저장
     */
    @Transactional
    public InsuranceSettlementSummaryResDto calculateSettlement(UUID companyId, String payYearMonth) {
        // 1. 급여대장 조회
        PayrollRuns payrollRun = payrollRunsRepository
                .findByCompany_CompanyIdAndPayYearMonth(companyId, payYearMonth)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYROLL_NOT_FOUND));

        // 이미 지급완료된 급여대장은 재산정 불가
        if (payrollRun.getPayrollStatus() == PayrollStatus.PAID) {
            throw new BusinessException(ErrorCode.PAYROLL_STATUS_INVALID);
        }

        // 2. 해당 연도 보험요율 조회
        int year = Integer.parseInt(payYearMonth.substring(0, 4));
        InsuranceRates rates = insuranceRatesRepository
                .findByCompany_CompanyIdAndYear(companyId, year)
                .orElseThrow(() -> new BusinessException(ErrorCode.INSURANCE_RATES_NOT_FOUND));

        // 3. PayrollDetails에서 사원별 지급항목(PAYMENT) 합산 → 보수월액
        List<PayrollDetails> allDetails = payrollDetailsRepository.findByPayrollRuns(payrollRun);

        // empId별 지급합계 집계
        Map<Long, Long> empBaseSalaryMap = allDetails.stream()
                .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
                .collect(Collectors.groupingBy(
                        d -> d.getEmployee().getEmpId(),
                        Collectors.summingLong(PayrollDetails::getAmount)
                ));

        // Employee 엔티티 맵 (산재요율 참조용)
        Map<Long, PayrollDetails> empDetailMap = allDetails.stream()
                .collect(Collectors.toMap(
                        d -> d.getEmployee().getEmpId(),
                        d -> d,
                        (a, b) -> a   // 중복 시 첫 번째 사용
                ));

        // 4. 기존 정산 데이터가 있으면 삭제 후 재생성
        if (insuranceSettlementRepository.existsByPayrollRuns(payrollRun)) {
            insuranceSettlementRepository.deleteByPayrollRuns(payrollRun);
        }

        // 5. 사원별 보험료 계산
        List<InsuranceSettlement> newSettlements = new ArrayList<>();

        for (Map.Entry<Long, Long> entry : empBaseSalaryMap.entrySet()) {
            Long empId = entry.getKey();
            Long baseSalary = entry.getValue();
            PayrollDetails sampleDetail = empDetailMap.get(empId);

            // 산재보험요율: 사원의 업종별 요율 사용, 없으면 기본 요율
            BigDecimal industrialRate = rates.getIndustrialAccident();
            if (sampleDetail.getEmployee().getJobTypes() != null
                    && sampleDetail.getEmployee().getJobTypes().getIndustrialAccidentRate() != null) {
                industrialRate = sampleDetail.getEmployee().getJobTypes().getIndustrialAccidentRate();
            }

            // ── 국민연금: 보수월액에 상/하한 적용 ──
            long pensionBase = baseSalary;
            if (pensionBase > rates.getPensionUpperLimit()) {
                pensionBase = rates.getPensionUpperLimit();
            } else if (pensionBase < rates.getPensionLowerLimit()) {
                pensionBase = rates.getPensionLowerLimit();
            }
            // 국민연금은 근로자/사업주 동일 요율 (각 반씩)
            long pensionEmp = calcHalf(pensionBase, rates.getNationalPension());
            long pensionEmpr = pensionEmp;

            // ── 건강보험: 근로자/사업주 동일 (각 반씩) ──
            long healthEmp = calcHalf(baseSalary, rates.getHealthInsurance());
            long healthEmpr = healthEmp;

            // ── 장기요양: 건강보험료 × 장기요양 요율 ──
            // 건강보험료 전액(근로자+사업주)에 장기요양요율 적용 후 반씩 부담
            long healthTotal = healthEmp + healthEmpr;
            long ltcTotal = calcAmount(healthTotal, rates.getLongTermCare());
            long ltcEmp = ltcTotal / 2;
            long ltcEmpr = ltcTotal - ltcEmp;   // 홀수원 처리

            // ── 고용보험: 근로자/사업주 요율 다름 ──
            long employmentEmp = calcAmount(baseSalary, rates.getEmploymentInsurance());
            long employmentEmpr = calcAmount(baseSalary, rates.getEmploymentInsuranceEmployer());

            // ── 산재보험: 사업주만 부담 ──
            long industrialEmpr = calcAmount(baseSalary, industrialRate);

            // 합계
            long totalEmp = pensionEmp + healthEmp + ltcEmp + employmentEmp;
            long totalEmpr = pensionEmpr + healthEmpr + ltcEmpr + employmentEmpr + industrialEmpr;

            InsuranceSettlement settlement = InsuranceSettlement.builder()
                    .payYearMonth(payYearMonth)
                    .baseSalary(baseSalary)
                    .pensionEmployee(pensionEmp)
                    .pensionEmployer(pensionEmpr)
                    .healthEmployee(healthEmp)
                    .healthEmployer(healthEmpr)
                    .ltcEmployee(ltcEmp)
                    .ltcEmployer(ltcEmpr)
                    .employmentEmployee(employmentEmp)
                    .employmentEmployer(employmentEmpr)
                    .industrialEmployer(industrialEmpr)
                    .totalEmployee(totalEmp)
                    .totalEmployer(totalEmpr)
                    .totalAmount(totalEmp + totalEmpr)
                    .isApplied(false)
                    .company(payrollRun.getCompany())
                    .employee(sampleDetail.getEmployee())
                    .payrollRuns(payrollRun)
                    .insuranceRates(rates)
                    .build();

            newSettlements.add(settlement);
        }

        insuranceSettlementRepository.saveAll(newSettlements);

        // 조회 후 반환
        return getSettlementList(companyId, payYearMonth);
    }


    /**
     * 사원별 보험료 상세 조회
     */
    public InsuranceSettlementDetailResDto getSettlementDetail(UUID companyId, Long settlementId) {
        InsuranceSettlement settlement = insuranceSettlementRepository
                .findBySettlementIdAndCompany_CompanyId(settlementId, companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INSURANCE_SETTLEMENT_NOT_FOUND));

        return InsuranceSettlementDetailResDto.fromEntity(settlement);
    }


    // ── 계산 유틸 ──

    /**
     * 보수월액 × 요율 (반올림, 원 단위)
     */
    private long calcAmount(long base, BigDecimal rate) {
        return BigDecimal.valueOf(base)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    /**
     * 보수월액 × 요율 / 2 (근로자/사업주 반씩, 반올림)
     */
    private long calcHalf(long base, BigDecimal rate) {
        return BigDecimal.valueOf(base)
                .multiply(rate)
                .divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)
                .longValue();
    }
}
```

---

## 5. Controller

### InsuranceSettlementController.java (신규)
**파일 위치**: `pay/controller/InsuranceSettlementController.java`

```java
package com.peoplecore.pay.controller;

import com.peoplecore.common.annotation.RoleRequired;
import com.peoplecore.pay.dtos.InsuranceSettlementDetailResDto;
import com.peoplecore.pay.dtos.InsuranceSettlementSummaryResDto;
import com.peoplecore.pay.service.InsuranceSettlementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/pay/admin/insurance-settlement")
@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
public class InsuranceSettlementController {

    @Autowired
    private InsuranceSettlementService insuranceSettlementService;

    /**
     * 정산보험료 목록 조회 (특정 월)
     * GET /pay/admin/insurance-settlement?payYearMonth=2026-04
     */
    @GetMapping
    public ResponseEntity<InsuranceSettlementSummaryResDto> getSettlementList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam String payYearMonth) {

        return ResponseEntity.ok(
                insuranceSettlementService.getSettlementList(companyId, payYearMonth));
    }

    /**
     * 보험료 산정 (급여대장 기반)
     * POST /pay/admin/insurance-settlement/calculate?payYearMonth=2026-04
     */
    @PostMapping("/calculate")
    public ResponseEntity<InsuranceSettlementSummaryResDto> calculateSettlement(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam String payYearMonth) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                insuranceSettlementService.calculateSettlement(companyId, payYearMonth));
    }

    /**
     * 사원별 보험료 상세
     * GET /pay/admin/insurance-settlement/{settlementId}
     */
    @GetMapping("/{settlementId}")
    public ResponseEntity<InsuranceSettlementDetailResDto> getSettlementDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long settlementId) {

        return ResponseEntity.ok(
                insuranceSettlementService.getSettlementDetail(companyId, settlementId));
    }
}
```

---

## 6. ErrorCode 추가

**파일 위치**: `common/ErrorCode.java` (기존 파일에 추가)

```java
// ── 정산보험료 ──
INSURANCE_RATES_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 연도 보험요율이 존재하지 않습니다."),
INSURANCE_SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "정산보험료 데이터가 존재하지 않습니다."),
```

---

## 보험료 산정 로직 요약

### 계산 공식

| 보험 | 근로자 | 사업주 | 비고 |
|------|--------|--------|------|
| 국민연금 | `보수월액 × 요율 / 2` | 동일 | 상한/하한 적용 |
| 건강보험 | `보수월액 × 요율 / 2` | 동일 | |
| 장기요양 | `(건강보험 전액) × 요율 / 2` | 동일 | 건강보험료 기반 |
| 고용보험 | `보수월액 × 근로자요율` | `보수월액 × 사업주요율` | 요율 다름 |
| 산재보험 | — | `보수월액 × 산재요율` | 사업주 전액 부담 |

### 보수월액 = 해당 월 지급항목(PAYMENT) 합계

### 국민연금 상/하한
- `InsuranceRates.pensionUpperLimit` 초과 시 → 상한액으로 계산
- `InsuranceRates.pensionLowerLimit` 미만 시 → 하한액으로 계산

### 산재보험 요율 결정 순서
1. `Employee.jobTypes.industrialAccidentRate` (사원의 업종별 요율)
2. 없으면 `InsuranceRates.industrialAccident` (기본 요율)

---

## 참고: 기존 Repository 추가 필요 메서드

### PayrollDetailsRepository.java
> 이미 payroll-ledger-code.md에 `findByPayrollRuns` 존재 — 추가 불필요

### PayrollRunsRepository.java
> 이미 payroll-ledger-code.md에 `findByCompany_CompanyIdAndPayYearMonth` 존재 — 추가 불필요
