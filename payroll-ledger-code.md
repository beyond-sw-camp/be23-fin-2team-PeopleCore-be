# 급여대장(작성) - 백엔드 코드

> Admin 화면 — 월별 급여 산정, 확정, 지급처리
> 흐름: **산정중(CALCULATING) → 확정(CONFIRMED) → 전자결재(APPROVED) → 지급완료(PAID)**
> 전자결재 연동은 추후 구현, 현재는 산정중→확정→지급완료까지

---

## API 목록

| # | Method | URL | 설명 |
|---|--------|-----|------|
| 1 | GET | `/pay/admin/payroll` | 급여대장 조회 (특정 월) |
| 2 | POST | `/pay/admin/payroll/create` | 급여 산정 생성 (연봉계약 기반) |
| 3 | POST | `/pay/admin/payroll/copy` | 전월 복사 |
| 4 | GET | `/pay/admin/payroll/{payrollRunId}/employees/{empId}` | 사원별 급여 상세 |
| 5 | PUT | `/pay/admin/payroll/{payrollRunId}/confirm` | 급여 확정 |
| 6 | PUT | `/pay/admin/payroll/{payrollRunId}/pay` | 지급 처리 |

---

## 파일 체크리스트

| # | 구분 | 파일명 | 위치 | 작업 |
|---|------|--------|------|------|
| 1 | Entity | `PayrollRuns.java` | pay/domain/ | 상태변경 메서드 + 합계 업데이트 메서드 추가 |
| 2 | Entity | `PayrollDetails.java` | pay/domain/ | FK 변경 + 스냅샷 필드 추가 |
| 3 | Repository | `PayrollRunsRepository.java` | pay/repository/ | 신규 |
| 4 | Repository | `PayrollDetailsRepository.java` | pay/repository/ | 신규 |
| 5 | DTO | `PayrollRunResDto.java` | pay/dtos/ | 신규 |
| 6 | DTO | `PayrollEmpResDto.java` | pay/dtos/ | 신규 |
| 7 | DTO | `PayrollEmpDetailResDto.java` | pay/dtos/ | 신규 |
| 8 | Service | `PayrollService.java` | pay/service/ | 신규 |
| 9 | Controller | `PayrollController.java` | pay/controller/ | 신규 |
| 10 | ErrorCode | `ErrorCode.java` | common/ | 추가 |

---

## 1. Entity 수정

### PayrollRuns.java (수정)
**파일 위치**: `pay/domain/PayrollRuns.java`

> 상태 변경 메서드 + 합계 업데이트 메서드 추가

```java
package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.pay.enums.PayrollStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "payroll_runs",
    indexes = {
        @Index(name = "idx_payroll_company_month", columnList = "company_id, pay_year_month")
    })
public class PayrollRuns {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payrollRunId;

    @Column(nullable = false, length = 7)
    private String payYearMonth;        // "2026-04"

    private Integer totalEmployees;     // 대상직원수
    private Long totalPay;              // 지급합계
    private Long totalDeduction;        // 공제합계
    private Long totalNetPay;           // 공제후 지급액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollStatus payrollStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    private LocalDate payDate;          // 지급일


    // ── 합계 갱신 ──
    public void updateTotals(Integer totalEmployees, Long totalPay, Long totalDeduction, Long totalNetPay) {
        this.totalEmployees = totalEmployees;
        this.totalPay = totalPay;
        this.totalDeduction = totalDeduction;
        this.totalNetPay = totalNetPay;
    }

    // ── 상태 변경: 확정 ──
    public void confirm() {
        if (this.payrollStatus != PayrollStatus.CALCULATING) {
            throw new IllegalStateException("산정중 상태에서만 확정 가능합니다.");
        }
        this.payrollStatus = PayrollStatus.CONFIRMED;
    }

    // ── 상태 변경: 승인 (전자결재 연동 후 사용) ──
    public void approve() {
        if (this.payrollStatus != PayrollStatus.CONFIRMED) {
            throw new IllegalStateException("확정 상태에서만 승인 가능합니다.");
        }
        this.payrollStatus = PayrollStatus.APPROVED;
    }

    // ── 상태 변경: 지급완료 ──
    public void markPaid(LocalDate payDate) {
        if (this.payrollStatus != PayrollStatus.CONFIRMED
                && this.payrollStatus != PayrollStatus.APPROVED) {
            throw new IllegalStateException("확정 또는 승인 상태에서만 지급처리 가능합니다.");
        }
        this.payrollStatus = PayrollStatus.PAID;
        this.payDate = payDate;
    }
}
```

---

### PayrollDetails.java (수정)
**파일 위치**: `pay/domain/PayrollDetails.java`

> FK 변경 (payrollRunId → PayrollRuns, empId → Employee, payItemId → PayItems)
> 스냅샷 필드 추가 (payItemName, payItemType)

```java
package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.pay.enums.PayItemType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Table(name = "payroll_details",
    indexes = {
        @Index(name = "idx_payroll_detail_run", columnList = "payroll_run_id"),
        @Index(name = "idx_payroll_detail_emp", columnList = "payroll_run_id, emp_id")
    })
public class PayrollDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payrollDetailsId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRuns payrollRuns;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private Long payItemId;             // 원본 항목 참조용

    // ── 스냅샷 필드 (산정 시점의 값 보존) ──
    @Column(length = 100, nullable = false)
    private String payItemName;         // 산정 시점 항목명

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayItemType payItemType;    // PAYMENT / DEDUCTION

    // 항목별 금액
    @Column(nullable = false)
    private Long amount;

    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
}
```

---

## 2. Repository

### PayrollRunsRepository.java (신규)
**파일 위치**: `pay/repository/PayrollRunsRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayrollRuns;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PayrollRunsRepository extends JpaRepository<PayrollRuns, Long> {

    // 회사 + 연월 조회
    Optional<PayrollRuns> findByCompany_CompanyIdAndPayYearMonth(UUID companyId, String payYearMonth);

    // 해당 월 급여대장 존재 여부
    boolean existsByCompany_CompanyIdAndPayYearMonth(UUID companyId, String payYearMonth);

    // 회사 + ID 조회
    Optional<PayrollRuns> findByPayrollRunIdAndCompany_CompanyId(Long payrollRunId, UUID companyId);
}
```

---

### PayrollDetailsRepository.java (신규)
**파일 위치**: `pay/repository/PayrollDetailsRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayrollDetails;
import com.peoplecore.pay.domain.PayrollRuns;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollDetailsRepository extends JpaRepository<PayrollDetails, Long> {

    // 특정 급여대장의 전체 상세 조회
    List<PayrollDetails> findByPayrollRuns(PayrollRuns payrollRuns);

    // 특정 급여대장 + 특정 사원의 상세 조회
    List<PayrollDetails> findByPayrollRunsAndEmployee_EmpId(PayrollRuns payrollRuns, Long empId);

    // 급여항목 사용 여부 체크 (삭제 시)
    boolean existsByPayItemId(Long payItemId);
}
```

---

## 3. DTO

### PayrollRunResDto.java (신규)
**파일 위치**: `pay/dtos/PayrollRunResDto.java`

> 급여대장 전체 조회 응답 (상단 요약 + 사원별 목록)

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.PayrollRuns;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollRunResDto {

    // 급여대장 요약 (상단 카드)
    private Long payrollRunId;
    private String payYearMonth;
    private String payrollStatus;
    private Integer totalEmployees;
    private Long totalPay;
    private Long totalDeduction;
    private Long totalNetPay;
    private Long unpaidAmount;          // 미지급 급여
    private LocalDate payDate;

    // 사원별 목록
    private List<PayrollEmpResDto> employees;

    public static PayrollRunResDto fromEntity(PayrollRuns run, List<PayrollEmpResDto> employees) {
        Long unpaid = run.getPayrollStatus().name().equals("PAID") ? 0L :
                (run.getTotalNetPay() != null ? run.getTotalNetPay() : 0L);

        return PayrollRunResDto.builder()
                .payrollRunId(run.getPayrollRunId())
                .payYearMonth(run.getPayYearMonth())
                .payrollStatus(run.getPayrollStatus().name())
                .totalEmployees(run.getTotalEmployees())
                .totalPay(run.getTotalPay())
                .totalDeduction(run.getTotalDeduction())
                .totalNetPay(run.getTotalNetPay())
                .unpaidAmount(unpaid)
                .payDate(run.getPayDate())
                .employees(employees)
                .build();
    }
}
```

---

### PayrollEmpResDto.java (신규)
**파일 위치**: `pay/dtos/PayrollEmpResDto.java`

> 급여대장 사원 행 (목록)

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollEmpResDto {

    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private String empType;
    private String status;          // 산정중, 확정 등 (PayrollRuns 상태 따라감)

    private Long totalPay;          // 지급합계
    private Long totalDeduction;    // 공제합계
    private Long netPay;            // 공제 후
    private Long unpaid;            // 미지급
}
```

---

### PayrollEmpDetailResDto.java (신규)
**파일 위치**: `pay/dtos/PayrollEmpDetailResDto.java`

> 사원명 클릭 시 항목별 상세

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
public class PayrollEmpDetailResDto {

    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private String empType;

    private List<PayrollItemDto> paymentItems;      // 지급 항목 목록
    private List<PayrollItemDto> deductionItems;    // 공제 항목 목록

    private Long totalPay;
    private Long totalDeduction;
    private Long netPay;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayrollItemDto {
        private Long payItemId;
        private String payItemName;     // 스냅샷 항목명
        private Long amount;
    }
}
```

---

## 4. Service

### PayrollService.java (신규)
**파일 위치**: `pay/service/PayrollService.java`

```java
package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.PayItems;
import com.peoplecore.pay.domain.PayrollDetails;
import com.peoplecore.pay.domain.PayrollRuns;
import com.peoplecore.pay.dtos.PayrollEmpDetailResDto;
import com.peoplecore.pay.dtos.PayrollEmpResDto;
import com.peoplecore.pay.dtos.PayrollRunResDto;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.enums.PayrollStatus;
import com.peoplecore.pay.repository.PayItemsRepository;
import com.peoplecore.pay.repository.PayrollDetailsRepository;
import com.peoplecore.pay.repository.PayrollRunsRepository;
import com.peoplecore.salarycontract.domain.ContractStatus;
import com.peoplecore.salarycontract.domain.SalaryContract;
import com.peoplecore.salarycontract.domain.SalaryContractDetail;
import com.peoplecore.salarycontract.repository.SalaryContractDetailRepository;
import com.peoplecore.salarycontract.repository.SalaryContractRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PayrollService {

    private final PayrollRunsRepository payrollRunsRepository;
    private final PayrollDetailsRepository payrollDetailsRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final SalaryContractRepository salaryContractRepository;
    private final SalaryContractDetailRepository salaryContractDetailRepository;
    private final PayItemsRepository payItemsRepository;

    @Autowired
    public PayrollService(PayrollRunsRepository payrollRunsRepository,
                          PayrollDetailsRepository payrollDetailsRepository,
                          EmployeeRepository employeeRepository,
                          CompanyRepository companyRepository,
                          SalaryContractRepository salaryContractRepository,
                          SalaryContractDetailRepository salaryContractDetailRepository,
                          PayItemsRepository payItemsRepository) {
        this.payrollRunsRepository = payrollRunsRepository;
        this.payrollDetailsRepository = payrollDetailsRepository;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.salaryContractRepository = salaryContractRepository;
        this.salaryContractDetailRepository = salaryContractDetailRepository;
        this.payItemsRepository = payItemsRepository;
    }


    // ══════════════════════════════════════════════════
    //  급여대장 조회 (특정 월)
    // ══════════════════════════════════════════════════
    public PayrollRunResDto getPayroll(UUID companyId, String payYearMonth) {

        PayrollRuns run = payrollRunsRepository
                .findByCompany_CompanyIdAndPayYearMonth(companyId, payYearMonth)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));

        List<PayrollDetails> allDetails = payrollDetailsRepository.findByPayrollRuns(run);

        // 사원별 그룹핑
        Map<Long, List<PayrollDetails>> detailsByEmp = allDetails.stream()
                .collect(Collectors.groupingBy(d -> d.getEmployee().getEmpId()));

        List<PayrollEmpResDto> empList = detailsByEmp.entrySet().stream()
                .map(entry -> {
                    Employee emp = entry.getValue().get(0).getEmployee();
                    List<PayrollDetails> details = entry.getValue();

                    long pay = details.stream()
                            .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
                            .mapToLong(PayrollDetails::getAmount).sum();
                    long deduction = details.stream()
                            .filter(d -> d.getPayItemType() == PayItemType.DEDUCTION)
                            .mapToLong(PayrollDetails::getAmount).sum();

                    return PayrollEmpResDto.builder()
                            .empId(emp.getEmpId())
                            .empName(emp.getEmpName())
                            .deptName(emp.getDept().getDeptName())
                            .gradeName(emp.getGrade().getGradeName())
                            .empType(emp.getEmpType().name())
                            .status(run.getPayrollStatus().name())
                            .totalPay(pay)
                            .totalDeduction(deduction)
                            .netPay(pay - deduction)
                            .unpaid(run.getPayrollStatus() == PayrollStatus.PAID ? 0L : pay - deduction)
                            .build();
                })
                .toList();

        return PayrollRunResDto.fromEntity(run, empList);
    }


    // ══════════════════════════════════════════════════
    //  급여 산정 생성 (연봉계약 기반)
    // ══════════════════════════════════════════════════
    @Transactional
    public PayrollRunResDto createPayroll(UUID companyId, String payYearMonth) {

        // 중복 체크
        if (payrollRunsRepository.existsByCompany_CompanyIdAndPayYearMonth(companyId, payYearMonth)) {
            throw new CustomException(ErrorCode.PAYROLL_ALREADY_EXISTS);
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        // 재직 + 휴직 사원 목록 (RESIGNED 제외)
        List<Employee> employees = employeeRepository.findAllwithFilter(
                null, null, null, null, null,
                org.springframework.data.domain.Pageable.unpaged()).getContent()
                .stream()
                .filter(e -> e.getEmpStatus() != EmpStatus.RESIGNED)
                .filter(e -> e.getCompany().getCompanyId().equals(companyId))
                .toList();

        // PayrollRuns 생성
        PayrollRuns run = PayrollRuns.builder()
                .payYearMonth(payYearMonth)
                .payrollStatus(PayrollStatus.CALCULATING)
                .company(company)
                .totalEmployees(employees.size())
                .totalPay(0L)
                .totalDeduction(0L)
                .totalNetPay(0L)
                .build();
        payrollRunsRepository.save(run);

        long totalPay = 0L;
        long totalDeduction = 0L;

        for (Employee emp : employees) {
            // 최신 서명완료 연봉계약 조회
            SalaryContract contract = salaryContractRepository
                    .findTopByEmployee_EmpIdAndStatusOrderByContractYearDesc(
                            emp.getEmpId(), ContractStatus.SIGNED)
                    .orElse(null);

            if (contract == null) continue;

            // 계약 상세 항목 조회
            List<SalaryContractDetail> contractDetails = salaryContractDetailRepository
                    .findByContractId(contract.getContractId());

            // payItemId → PayItems 매핑 (스냅샷용)
            List<Long> payItemIds = contractDetails.stream()
                    .map(SalaryContractDetail::getPayItemId)
                    .toList();
            Map<Long, PayItems> payItemMap = payItemsRepository
                    .findByPayItemIdInAndCompany_CompanyId(payItemIds, companyId)
                    .stream()
                    .collect(Collectors.toMap(PayItems::getPayItemId, Function.identity()));

            for (SalaryContractDetail detail : contractDetails) {
                PayItems payItem = payItemMap.get(detail.getPayItemId());
                if (payItem == null) continue;

                PayrollDetails payrollDetail = PayrollDetails.builder()
                        .payrollRuns(run)
                        .employee(emp)
                        .payItemId(payItem.getPayItemId())
                        .payItemName(payItem.getPayItemName())      // 스냅샷
                        .payItemType(payItem.getPayItemType())      // 스냅샷
                        .amount(detail.getAmount().longValue())
                        .company(company)
                        .build();
                payrollDetailsRepository.save(payrollDetail);

                if (payItem.getPayItemType() == PayItemType.PAYMENT) {
                    totalPay += detail.getAmount();
                } else {
                    totalDeduction += detail.getAmount();
                }
            }
        }

        // 합계 갱신
        run.updateTotals(employees.size(), totalPay, totalDeduction, totalPay - totalDeduction);

        return getPayroll(companyId, payYearMonth);
    }


    // ══════════════════════════════════════════════════
    //  전월 복사
    // ══════════════════════════════════════════════════
    @Transactional
    public PayrollRunResDto copyFromPreviousMonth(UUID companyId, String payYearMonth) {

        // 중복 체크
        if (payrollRunsRepository.existsByCompany_CompanyIdAndPayYearMonth(companyId, payYearMonth)) {
            throw new CustomException(ErrorCode.PAYROLL_ALREADY_EXISTS);
        }

        // 전월 계산
        YearMonth current = YearMonth.parse(payYearMonth, DateTimeFormatter.ofPattern("yyyy-MM"));
        String prevMonth = current.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));

        PayrollRuns prevRun = payrollRunsRepository
                .findByCompany_CompanyIdAndPayYearMonth(companyId, prevMonth)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYROLL_PREV_NOT_FOUND));

        Company company = prevRun.getCompany();

        // 신규 PayrollRuns 생성
        PayrollRuns newRun = PayrollRuns.builder()
                .payYearMonth(payYearMonth)
                .payrollStatus(PayrollStatus.CALCULATING)
                .company(company)
                .totalEmployees(prevRun.getTotalEmployees())
                .totalPay(prevRun.getTotalPay())
                .totalDeduction(prevRun.getTotalDeduction())
                .totalNetPay(prevRun.getTotalNetPay())
                .build();
        payrollRunsRepository.save(newRun);

        // 전월 상세 복사
        List<PayrollDetails> prevDetails = payrollDetailsRepository.findByPayrollRuns(prevRun);

        for (PayrollDetails prev : prevDetails) {
            // 퇴직자 제외
            if (prev.getEmployee().getEmpStatus() == EmpStatus.RESIGNED) continue;

            PayrollDetails copy = PayrollDetails.builder()
                    .payrollRuns(newRun)
                    .employee(prev.getEmployee())
                    .payItemId(prev.getPayItemId())
                    .payItemName(prev.getPayItemName())          // 전월 스냅샷 그대로 복사
                    .payItemType(prev.getPayItemType())
                    .amount(prev.getAmount())
                    .company(company)
                    .build();
            payrollDetailsRepository.save(copy);
        }

        return getPayroll(companyId, payYearMonth);
    }


    // ══════════════════════════════════════════════════
    //  사원별 급여 상세 조회
    // ══════════════════════════════════════════════════
    public PayrollEmpDetailResDto getEmpPayrollDetail(UUID companyId, Long payrollRunId, Long empId) {

        PayrollRuns run = findPayrollRun(companyId, payrollRunId);

        List<PayrollDetails> details = payrollDetailsRepository
                .findByPayrollRunsAndEmployee_EmpId(run, empId);

        if (details.isEmpty()) {
            throw new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND);
        }

        Employee emp = details.get(0).getEmployee();

        List<PayrollEmpDetailResDto.PayrollItemDto> paymentItems = details.stream()
                .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
                .map(d -> PayrollEmpDetailResDto.PayrollItemDto.builder()
                        .payItemId(d.getPayItemId())
                        .payItemName(d.getPayItemName())
                        .amount(d.getAmount())
                        .build())
                .toList();

        List<PayrollEmpDetailResDto.PayrollItemDto> deductionItems = details.stream()
                .filter(d -> d.getPayItemType() == PayItemType.DEDUCTION)
                .map(d -> PayrollEmpDetailResDto.PayrollItemDto.builder()
                        .payItemId(d.getPayItemId())
                        .payItemName(d.getPayItemName())
                        .amount(d.getAmount())
                        .build())
                .toList();

        long totalPay = paymentItems.stream().mapToLong(PayrollEmpDetailResDto.PayrollItemDto::getAmount).sum();
        long totalDeduction = deductionItems.stream().mapToLong(PayrollEmpDetailResDto.PayrollItemDto::getAmount).sum();

        return PayrollEmpDetailResDto.builder()
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .deptName(emp.getDept().getDeptName())
                .gradeName(emp.getGrade().getGradeName())
                .empType(emp.getEmpType().name())
                .paymentItems(paymentItems)
                .deductionItems(deductionItems)
                .totalPay(totalPay)
                .totalDeduction(totalDeduction)
                .netPay(totalPay - totalDeduction)
                .build();
    }


    // ══════════════════════════════════════════════════
    //  급여 확정
    // ══════════════════════════════════════════════════
    @Transactional
    public void confirmPayroll(UUID companyId, Long payrollRunId) {
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);
        run.confirm();
    }


    // ══════════════════════════════════════════════════
    //  지급 처리
    // ══════════════════════════════════════════════════
    @Transactional
    public void processPayment(UUID companyId, Long payrollRunId) {
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);
        run.markPaid(LocalDate.now());
    }


    // ── 공통: PayrollRuns 조회 ──
    private PayrollRuns findPayrollRun(UUID companyId, Long payrollRunId) {
        return payrollRunsRepository
                .findByPayrollRunIdAndCompany_CompanyId(payrollRunId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));
    }
}
```

---

## 5. Controller

### PayrollController.java (신규)
**파일 위치**: `pay/controller/PayrollController.java`

```java
package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.PayrollEmpDetailResDto;
import com.peoplecore.pay.dtos.PayrollRunResDto;
import com.peoplecore.pay.service.PayrollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/pay/admin/payroll")
@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
public class PayrollController {

    private final PayrollService payrollService;

    @Autowired
    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }


    //    급여대장 조회 (특정 월)
    @GetMapping
    public ResponseEntity<PayrollRunResDto> getPayroll(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam String payYearMonth) {
        return ResponseEntity.ok(payrollService.getPayroll(companyId, payYearMonth));
    }

    //    급여 산정 생성 (연봉계약 기반)
    @PostMapping("/create")
    public ResponseEntity<PayrollRunResDto> createPayroll(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam String payYearMonth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(payrollService.createPayroll(companyId, payYearMonth));
    }

    //    전월 복사
    @PostMapping("/copy")
    public ResponseEntity<PayrollRunResDto> copyFromPreviousMonth(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam String payYearMonth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(payrollService.copyFromPreviousMonth(companyId, payYearMonth));
    }

    //    사원별 급여 상세
    @GetMapping("/{payrollRunId}/employees/{empId}")
    public ResponseEntity<PayrollEmpDetailResDto> getEmpPayrollDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payrollRunId,
            @PathVariable Long empId) {
        return ResponseEntity.ok(
                payrollService.getEmpPayrollDetail(companyId, payrollRunId, empId));
    }

    //    급여 확정
    @PutMapping("/{payrollRunId}/confirm")
    public ResponseEntity<Void> confirmPayroll(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payrollRunId) {
        payrollService.confirmPayroll(companyId, payrollRunId);
        return ResponseEntity.ok().build();
    }

    //    지급 처리
    @PutMapping("/{payrollRunId}/pay")
    public ResponseEntity<Void> processPayment(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payrollRunId) {
        payrollService.processPayment(companyId, payrollRunId);
        return ResponseEntity.ok().build();
    }
}
```

---

## 6. ErrorCode 추가

**파일 위치**: `common/.../exception/ErrorCode.java`

```java
// 급여대장
PAYROLL_NOT_FOUND(404, "해당 월의 급여대장을 찾을 수 없습니다."),
PAYROLL_ALREADY_EXISTS(409, "해당 월의 급여대장이 이미 존재합니다."),
PAYROLL_PREV_NOT_FOUND(404, "전월 급여대장을 찾을 수 없습니다."),
PAYROLL_STATUS_INVALID(400, "현재 상태에서는 처리할 수 없습니다."),
```

---

## 프론트 연동 참고

### 급여대장 조회
```
GET /pay/admin/payroll?payYearMonth=2026-04
Headers: X-User-Company: {companyId}

Response:
{
  "payrollRunId": 1,
  "payYearMonth": "2026-04",
  "payrollStatus": "CALCULATING",
  "totalEmployees": 6,
  "totalPay": 28500000,
  "totalDeduction": 4200000,
  "totalNetPay": 24300000,
  "unpaidAmount": 24300000,
  "payDate": null,
  "employees": [
    {
      "empId": 1,
      "empName": "김민수",
      "deptName": "개발팀",
      "gradeName": "대리",
      "empType": "FULL",
      "status": "CALCULATING",
      "totalPay": 4000000,
      "totalDeduction": 620000,
      "netPay": 3380000,
      "unpaid": 3380000
    }
  ]
}
```

### 상태 전이
```
산정중(CALCULATING) → 확정(CONFIRMED) → 승인(APPROVED, 전자결재) → 지급완료(PAID)
                                          ↑ 추후 구현
```

### 버튼 활성화 조건 (프론트)
- **전월 복사**: payrollStatus == null (해당 월 데이터 없을 때)
- **확정**: payrollStatus == CALCULATING
- **전자결재**: payrollStatus == CONFIRMED (추후)
- **지급처리**: payrollStatus == CONFIRMED 또는 APPROVED
- **대량이체 파일**: payrollStatus == PAID

---

## 참고: 전월 복사 vs 연봉계약 기반 생성 차이

| | 연봉계약 기반 (create) | 전월 복사 (copy) |
|---|---|---|
| 데이터 소스 | SalaryContractDetail | 전월 PayrollDetails |
| 스냅샷 | 현재 PayItems에서 항목명 가져옴 | 전월 스냅샷 그대로 복사 |
| 퇴직자 처리 | 계약이 없으면 자동 제외 | RESIGNED 상태면 제외 |
| 사용 시점 | 최초 산정 or 연봉계약 갱신 시 | 매월 반복 급여 처리 |
