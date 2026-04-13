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
| 7 | GET | `/pay/admin/payroll/{payrollRunId}/employees/{empId}/wage-info` | 일당/시급 기준 |
| 8 | GET | `/pay/admin/payroll/{payrollRunId}/employees/{empId}/approved-overtime` | 이달 승인된 전자결재 |
| 9 | POST | `/pay/admin/payroll/{payrollRunId}/employees/{empId}/apply-overtime/{otId}` | 전자결재 건별 적용 |
| 10 | POST | `/pay/admin/payroll/{payrollRunId}/employees/{empId}/apply-all-overtime` | 전자결재 전체 적용 |
| 10-1 | POST | `/pay/admin/payroll/calc-deductions` | 지급합계 기반 공제항목 실시간 계산 |
| 11 | GET | `/pay/admin/leave-allowance/year-end` | 연말 미사용 연차 산정 목록 |
| 12 | GET | `/pay/admin/leave-allowance/resigned` | 퇴직자 연차 정산 목록 |
| 13 | POST | `/pay/admin/leave-allowance/calculate` | 수당 산정 (대상자 선택) |
| 14 | PUT | `/pay/admin/leave-allowance/{allowanceId}/unused-days` | 미사용일수 수정 |
| 15 | POST | `/pay/admin/leave-allowance/apply-to-payroll` | 급여대장 반영 |

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
| 11 | Entity | `OvertimeRequest.java` | attendance/entity/ | otTypeFlag(Integer, 비트마스크) 필드 추가 |
| 12 | Class | `OtTypeFlag.java` | attendance/entity/ | 신규 (비트마스크 상수 + 헬퍼) |
| 13 | Repository | `OvertimeRequestRepository.java` | attendance/repository/ | 신규 |
| 14 | Entity | `PayrollDetails.java` | pay/domain/ | otId 필드 추가 |
| 15 | DTO | `WageInfoResDto.java` | pay/dtos/ | 신규 |
| 16 | DTO | `ApprovedOvertimeResDto.java` | pay/dtos/ | 신규 |
| 17 | Entity | `LeaveAllowance.java` | pay/domain/ | 신규 |
| 18 | Enum | `AllowanceType.java` | pay/enums/ | 신규 |
| 19 | Enum | `AllowanceStatus.java` | pay/enums/ | 신규 |
| 20 | Repository | `LeaveAllowanceRepository.java` | pay/repository/ | 신규 |
| 21 | Repository | `VacationRemainderRepository.java` | vacation/repository/ | 신규 |
| 22 | DTO | `LeaveAllowanceSummaryResDto.java` | pay/dtos/ | 신규 |
| 23 | DTO | `LeaveAllowanceResDto.java` | pay/dtos/ | 신규 |
| 24 | Service | `LeaveAllowanceService.java` | pay/service/ | 신규 |
| 25 | Controller | `LeaveAllowanceController.java` | pay/controller/ | 신규 |

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

---

# 보완 1: 사원별 급여 상세 — 전자결재 연동 + 일당/시급

> 개인별 급여 작성 시 우측 패널에 일당/시급 기준 + 이달 승인된 전자결재(연장/야간/휴일) 표시
> 적용 버튼 클릭 시 해당 수당이 지급항목에 자동 추가

---

## 7. OvertimeRequest 수정 (otTypeFlag 추가)
**파일 위치**: `attendance/entity/OvertimeRequest.java`

> 초과근무 유형을 비트마스크로 표현 (중첩 가산 지원)

```java
// ── 기존 필드에 추가 ──

/**
 * 초과 근무 유형 (비트마스크)
 * bit 0 (1) = 연장근로
 * bit 1 (2) = 야간근로
 * bit 2 (4) = 휴일근로
 * 예: 3 = 연장+야간, 7 = 연장+야간+휴일
 */
@Column(nullable = false)
private Integer otTypeFlag;
```

---

## 8. OtTypeFlag 비트마스크 상수 (신규)
**파일 위치**: `attendance/entity/OtTypeFlag.java`

```java
package com.peoplecore.attendance.entity;

/**
 * 초과근무 유형 비트마스크 상수
 * 중첩 가산을 위해 비트 OR 조합 사용
 */
public final class OtTypeFlag {

    public static final int OVERTIME = 1;   // 연장근로 (bit 0)
    public static final int NIGHT    = 2;   // 야간근로 (bit 1)
    public static final int HOLIDAY  = 4;   // 휴일근로 (bit 2)

    private OtTypeFlag() {}

    public static boolean hasOvertime(int flag) { return (flag & OVERTIME) != 0; }
    public static boolean hasNight(int flag)    { return (flag & NIGHT) != 0; }
    public static boolean hasHoliday(int flag)  { return (flag & HOLIDAY) != 0; }

    /** 비트마스크 → 한글 라벨 (예: "연장+야간") */
    public static String toLabel(int flag) {
        StringBuilder sb = new StringBuilder();
        if (hasOvertime(flag)) sb.append("연장");
        if (hasNight(flag))    { if (!sb.isEmpty()) sb.append("+"); sb.append("야간"); }
        if (hasHoliday(flag))  { if (!sb.isEmpty()) sb.append("+"); sb.append("휴일"); }
        return sb.isEmpty() ? "일반" : sb.toString();
    }
}
```

---

## 9. OvertimeRequestRepository (신규)
**파일 위치**: `attendance/repository/OvertimeRequestRepository.java`

```java
package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.entity.OtStatus;
import com.peoplecore.attendance.entity.OvertimeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, Long> {

    /**
     * 특정 사원의 해당 월 승인된 초과근무 조회
     */
    List<OvertimeRequest> findByEmpIdAndOtStatusAndOtDateBetween(
            Long empId,
            OtStatus otStatus,
            LocalDateTime startOfMonth,
            LocalDateTime endOfMonth
    );

    /**
     * 이미 급여대장에 적용된 초과근무 ID 조회 (중복 방지)
     */
    @Query("SELECT pd.otId FROM PayrollDetails pd " +
            "WHERE pd.payrollRuns.payrollRunId = :payrollRunId " +
            "AND pd.employee.empId = :empId " +
            "AND pd.otId IS NOT NULL")
    List<Long> findAppliedOtIds(
            @Param("payrollRunId") Long payrollRunId,
            @Param("empId") Long empId
    );
}
```

---

## 10. PayrollDetails 수정 (otId 추가)
**파일 위치**: `pay/domain/PayrollDetails.java`

> 전자결재 적용 추적용 필드 추가

```java
// ── 기존 필드에 추가 ──

/** 적용된 초과근무 ID (null이면 연봉계약 기반 항목) */
private Long otId;
```

---

## 11. WageInfoResDto (신규)
**파일 위치**: `pay/dtos/WageInfoResDto.java`

> 일당/시급 기준 패널 응답

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
public class WageInfoResDto {

    private Long hourlyWage;            // 시급 (통상임금 ÷ 209)
    private Long dailyWage;             // 일당 (시급 × 8)
    private Long overtimeHourlyWage;    // 가산 시급 (시급 × 1.5, 단일 유형 기준)
}
```

---

## 12. ApprovedOvertimeResDto (신규)
**파일 위치**: `pay/dtos/ApprovedOvertimeResDto.java`

> 이달 승인된 전자결재 패널 응답

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.attendance.entity.OtTypeFlag;
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
public class ApprovedOvertimeResDto {

    private List<OvertimeItemDto> items;
    private Long totalHours;            // 합계 시간
    private Long totalAmount;           // 합계 금액

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OvertimeItemDto {
        private Long otId;
        private Integer otTypeFlag;     // 비트마스크 (1=연장, 2=야간, 4=휴일)
        private String otTypeLabel;     // "연장+야간", "휴일" 등
        private BigDecimal premiumRate; // 적용 가산율 (예: 2.0)
        private LocalDate otDate;
        private Long hours;             // 실 근무시간 (시간 단위)
        private Long amount;            // 수당 금액
        private boolean applied;        // 이미 적용 여부
    }
}
```

---

## 13. PayrollService 보완 (전자결재 연동 메서드)
**파일 위치**: `pay/service/PayrollService.java`

> 기존 서비스에 아래 메서드들 추가 + 의존성 주입 추가

```java
// ── 의존성 추가 (생성자에 추가) ──
@Autowired private OvertimeRequestRepository overtimeRequestRepository;


// ══════════════════════════════════════════════════
//  일당/시급 기준 조회
// ══════════════════════════════════════════════════
public WageInfoResDto getWageInfo(UUID companyId, Long payrollRunId, Long empId) {

    findPayrollRun(companyId, payrollRunId);  // 권한 체크

    // 최신 연봉계약의 통상임금(월)
    SalaryContract contract = salaryContractRepository
            .findTopByEmployee_EmpIdAndStatusOrderByContractYearDesc(empId, ContractStatus.SIGNED)
            .orElseThrow(() -> new CustomException(ErrorCode.SALARY_CONTRACT_NOT_FOUND));

    long monthlySalary = contract.getTotalAmount()
            .divide(BigDecimal.valueOf(12), 0, RoundingMode.FLOOR)
            .longValue();

    // 시급 = 통상임금(월) ÷ 209
    long hourlyWage = Math.round((double) monthlySalary / 209);
    // 일당 = 시급 × 8
    long dailyWage = hourlyWage * 8;
    // 가산 시급 = 시급 × 1.5 (단일 유형 기준)
    long overtimeHourlyWage = Math.round(hourlyWage * 1.5);

    return WageInfoResDto.builder()
            .hourlyWage(hourlyWage)
            .dailyWage(dailyWage)
            .overtimeHourlyWage(overtimeHourlyWage)
            .build();
}


// ══════════════════════════════════════════════════
//  이달 승인된 전자결재 조회
// ══════════════════════════════════════════════════
public ApprovedOvertimeResDto getApprovedOvertime(UUID companyId, Long payrollRunId, Long empId) {

    PayrollRuns run = findPayrollRun(companyId, payrollRunId);

    // 해당 월 범위 계산
    YearMonth ym = YearMonth.parse(run.getPayYearMonth(), DateTimeFormatter.ofPattern("yyyy-MM"));
    LocalDateTime startOfMonth = ym.atDay(1).atStartOfDay();
    LocalDateTime endOfMonth = ym.atEndOfMonth().atTime(23, 59, 59);

    // 승인된 초과근무 조회
    List<OvertimeRequest> approvedList = overtimeRequestRepository
            .findByEmpIdAndOtStatusAndOtDateBetween(empId, OtStatus.APPROVED, startOfMonth, endOfMonth);

    // 이미 적용된 otId 목록
    List<Long> appliedOtIds = overtimeRequestRepository.findAppliedOtIds(payrollRunId, empId);

    // 시급 조회
    WageInfoResDto wageInfo = getWageInfo(companyId, payrollRunId, empId);

    long totalHours = 0L;
    long totalAmount = 0L;
    List<ApprovedOvertimeResDto.OvertimeItemDto> items = new ArrayList<>();

    for (OvertimeRequest ot : approvedList) {
        // 실제 시간 우선, 없으면 계획 시간
        LocalDateTime start = ot.getOtActStart() != null ? ot.getOtActStart() : ot.getOtPlanStart();
        LocalDateTime end = ot.getOtActEnd() != null ? ot.getOtActEnd() : ot.getOtPlanEnd();

        long hours = java.time.Duration.between(start, end).toHours();

        // 비트마스크 기반 가산율 계산 (고정 0.5 × 유형 수)
        double premiumRate = calcPremiumRate(ot.getOtTypeFlag());
        long amount = Math.round(wageInfo.getHourlyWage() * premiumRate * hours);

        totalHours += hours;
        totalAmount += amount;

        items.add(ApprovedOvertimeResDto.OvertimeItemDto.builder()
                .otId(ot.getOtId())
                .otTypeFlag(ot.getOtTypeFlag())
                .otTypeLabel(OtTypeFlag.toLabel(ot.getOtTypeFlag()))
                .premiumRate(BigDecimal.valueOf(premiumRate))
                .otDate(ot.getOtDate().toLocalDate())
                .hours(hours)
                .amount(amount)
                .applied(appliedOtIds.contains(ot.getOtId()))
                .build());
    }

    return ApprovedOvertimeResDto.builder()
            .items(items)
            .totalHours(totalHours)
            .totalAmount(totalAmount)
            .build();
}


// ══════════════════════════════════════════════════
//  전자결재 건별 적용 → PayrollDetails 추가
// ══════════════════════════════════════════════════
@Transactional
public void applyOvertime(UUID companyId, Long payrollRunId, Long empId, Long otId) {

    PayrollRuns run = findPayrollRun(companyId, payrollRunId);

    if (run.getPayrollStatus() != PayrollStatus.CALCULATING) {
        throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
    }

    // 중복 체크
    List<Long> appliedOtIds = overtimeRequestRepository.findAppliedOtIds(payrollRunId, empId);
    if (appliedOtIds.contains(otId)) {
        throw new CustomException(ErrorCode.OVERTIME_ALREADY_APPLIED);
    }

    OvertimeRequest ot = overtimeRequestRepository.findById(otId)
            .orElseThrow(() -> new CustomException(ErrorCode.OVERTIME_NOT_FOUND));

    // 시급 + 가산율 계산
    WageInfoResDto wageInfo = getWageInfo(companyId, payrollRunId, empId);
    LocalDateTime start = ot.getOtActStart() != null ? ot.getOtActStart() : ot.getOtPlanStart();
    LocalDateTime end = ot.getOtActEnd() != null ? ot.getOtActEnd() : ot.getOtPlanEnd();
    long hours = java.time.Duration.between(start, end).toHours();

    double premiumRate = calcPremiumRate(ot.getOtTypeFlag());
    long amount = Math.round(wageInfo.getHourlyWage() * premiumRate * hours);

    // 비트마스크에서 대표 법정수당 항목 결정 (우선순위: 휴일 > 야간 > 연장)
    LegalCalcType legalType = resolvePrimaryLegalType(ot.getOtTypeFlag());
    PayItems payItem = payItemsRepository
            .findByCompany_CompanyIdAndIsLegalTrueAndLegalCalcType(companyId, legalType)
            .orElseThrow(() -> new CustomException(ErrorCode.PAY_ITEM_NOT_FOUND));

    Employee emp = employeeRepository.findById(empId)
            .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

    String label = OtTypeFlag.toLabel(ot.getOtTypeFlag());

    // PayrollDetails 생성
    PayrollDetails detail = PayrollDetails.builder()
            .payrollRuns(run)
            .employee(emp)
            .payItemId(payItem.getPayItemId())
            .payItemName(payItem.getPayItemName())
            .payItemType(PayItemType.PAYMENT)
            .amount(amount)
            .otId(otId)
            .memo(label + " " + hours + "시간 (" + premiumRate + "배)")
            .company(run.getCompany())
            .build();
    payrollDetailsRepository.save(detail);

    // 합계 갱신
    recalculateTotals(run);
}


// ══════════════════════════════════════════════════
//  전자결재 전체 적용
// ══════════════════════════════════════════════════
@Transactional
public void applyAllOvertime(UUID companyId, Long payrollRunId, Long empId) {

    PayrollRuns run = findPayrollRun(companyId, payrollRunId);

    if (run.getPayrollStatus() != PayrollStatus.CALCULATING) {
        throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
    }

    ApprovedOvertimeResDto overtime = getApprovedOvertime(companyId, payrollRunId, empId);

    for (ApprovedOvertimeResDto.OvertimeItemDto item : overtime.getItems()) {
        if (!item.isApplied()) {
            applyOvertime(companyId, payrollRunId, empId, item.getOtId());
        }
    }
}


// ── 헬퍼: PayrollRuns 합계 재계산 ──
private void recalculateTotals(PayrollRuns run) {
    List<PayrollDetails> allDetails = payrollDetailsRepository.findByPayrollRuns(run);

    long totalPay = allDetails.stream()
            .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
            .mapToLong(PayrollDetails::getAmount).sum();
    long totalDeduction = allDetails.stream()
            .filter(d -> d.getPayItemType() == PayItemType.DEDUCTION)
            .mapToLong(PayrollDetails::getAmount).sum();

    int empCount = (int) allDetails.stream()
            .map(d -> d.getEmployee().getEmpId())
            .distinct().count();

    run.updateTotals(empCount, totalPay, totalDeduction, totalPay - totalDeduction);
}


// ── 헬퍼: 비트마스크 → 총 가산율 계산 (고정 0.5 × 유형 수) ──
// 예: otTypeFlag=3(연장+야간) → 1.0 + 0.5 + 0.5 = 2.0배
//     otTypeFlag=7(연장+야간+휴일) → 1.0 + 0.5 + 0.5 + 0.5 = 2.5배
private double calcPremiumRate(int otTypeFlag) {
    double rate = 1.0;
    if (OtTypeFlag.hasOvertime(otTypeFlag)) rate += 0.5;
    if (OtTypeFlag.hasNight(otTypeFlag))    rate += 0.5;
    if (OtTypeFlag.hasHoliday(otTypeFlag))  rate += 0.5;
    return rate;
}


// ── 헬퍼: 비트마스크 → 대표 법정수당 항목 결정 (PayItems 조회용) ──
// 복수 유형 중첩 시 우선순위: 휴일 > 야간 > 연장
private LegalCalcType resolvePrimaryLegalType(int otTypeFlag) {
    if (OtTypeFlag.hasHoliday(otTypeFlag))  return LegalCalcType.HOLIDAY;
    if (OtTypeFlag.hasNight(otTypeFlag))    return LegalCalcType.NIGHT;
    return LegalCalcType.OVERTIME;
}
```

---

## 14. PayItemsRepository 추가 쿼리
**파일 위치**: `pay/repository/PayItemsRepository.java`

```java
// ── 법정수당 항목 조회 (isLegal + legalCalcType) ──
Optional<PayItems> findByCompany_CompanyIdAndIsLegalTrueAndLegalCalcType(
        UUID companyId, LegalCalcType legalCalcType
);
```

---

## 15. PayrollController 보완
**파일 위치**: `pay/controller/PayrollController.java`

> 기존 컨트롤러에 아래 API 추가

```java
/**
 * 일당/시급 기준 조회
 * GET /pay/admin/payroll/{payrollRunId}/employees/{empId}/wage-info
 */
@GetMapping("/{payrollRunId}/employees/{empId}/wage-info")
public ResponseEntity<WageInfoResDto> getWageInfo(
        @RequestHeader("X-User-Company") UUID companyId,
        @PathVariable Long payrollRunId,
        @PathVariable Long empId) {
    return ResponseEntity.ok(
            payrollService.getWageInfo(companyId, payrollRunId, empId));
}

/**
 * 이달 승인된 전자결재 조회
 * GET /pay/admin/payroll/{payrollRunId}/employees/{empId}/approved-overtime
 */
@GetMapping("/{payrollRunId}/employees/{empId}/approved-overtime")
public ResponseEntity<ApprovedOvertimeResDto> getApprovedOvertime(
        @RequestHeader("X-User-Company") UUID companyId,
        @PathVariable Long payrollRunId,
        @PathVariable Long empId) {
    return ResponseEntity.ok(
            payrollService.getApprovedOvertime(companyId, payrollRunId, empId));
}

/**
 * 전자결재 건별 적용
 * POST /pay/admin/payroll/{payrollRunId}/employees/{empId}/apply-overtime/{otId}
 */
@PostMapping("/{payrollRunId}/employees/{empId}/apply-overtime/{otId}")
public ResponseEntity<Void> applyOvertime(
        @RequestHeader("X-User-Company") UUID companyId,
        @PathVariable Long payrollRunId,
        @PathVariable Long empId,
        @PathVariable Long otId) {
    payrollService.applyOvertime(companyId, payrollRunId, empId, otId);
    return ResponseEntity.ok().build();
}

/**
 * 전자결재 전체 적용
 * POST /pay/admin/payroll/{payrollRunId}/employees/{empId}/apply-all-overtime
 */
@PostMapping("/{payrollRunId}/employees/{empId}/apply-all-overtime")
public ResponseEntity<Void> applyAllOvertime(
        @RequestHeader("X-User-Company") UUID companyId,
        @PathVariable Long payrollRunId,
        @PathVariable Long empId) {
    payrollService.applyAllOvertime(companyId, payrollRunId, empId);
    return ResponseEntity.ok().build();
}

/**
 * 지급합계 기반 공제항목 실시간 계산
 * POST /pay/admin/payroll/calc-deductions
 *
 * 프론트에서 지급항목 입력할 때마다 호출 → 공제항목 금액을 실시간 갱신
 */
@PostMapping("/calc-deductions")
public ResponseEntity<CalcDeductionResDto> calcDeductions(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestBody CalcDeductionReqDto request) {
    return ResponseEntity.ok(
            payrollService.calcDeductions(companyId, request));
}
```

---

## 15-1. 공제 실시간 계산 DTO + Service

### CalcDeductionReqDto (신규)
**파일 위치**: `pay/dtos/CalcDeductionReqDto.java`

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CalcDeductionReqDto {

    private Long totalPay;          // 지급합계 (지급항목 전체 합)
    private Long empId;             // 사원 ID (부양가족수, 세율옵션 조회용)
}
```

### CalcDeductionResDto (신규)
**파일 위치**: `pay/dtos/CalcDeductionResDto.java`

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
public class CalcDeductionResDto {

    // 4대보험 (근로자 부담분)
    private Long nationalPension;       // 국민연금
    private Long healthInsurance;       // 건강보험
    private Long longTermCare;          // 장기요양보험
    private Long employmentInsurance;   // 고용보험

    // 세금
    private Long incomeTax;             // 근로소득세
    private Long localIncomeTax;        // 근로지방소득세

    // 합계
    private Long totalDeduction;        // 공제합계
    private Long netPay;                // 공제 후 지급액
}
```

### PayrollService에 추가
**파일 위치**: `pay/service/PayrollService.java`

```java
// ── 의존성 추가 ──
@Autowired private InsuranceRatesRepository insuranceRatesRepository;
@Autowired private TaxWithholdingService taxWithholdingService;


// ══════════════════════════════════════════════════
//  지급합계 기반 공제항목 실시간 계산
//  프론트에서 지급항목 수정할 때마다 호출
// ══════════════════════════════════════════════════
public CalcDeductionResDto calcDeductions(UUID companyId, CalcDeductionReqDto request) {

    long totalPay = request.getTotalPay();

    // 해당 연도 보험요율 조회
    int currentYear = LocalDate.now().getYear();
    InsuranceRates rates = insuranceRatesRepository
            .findByCompany_CompanyIdAndYear(companyId, currentYear)
            .orElseThrow(() -> new CustomException(ErrorCode.INSURANCE_RATES_NOT_FOUND));

    // ── 4대보험 (근로자 부담분) ──

    // 국민연금 = 보수월액 × 요율 ÷ 2 (상/하한 적용)
    long pensionBase = totalPay;
    if (pensionBase > rates.getPensionUpperLimit()) pensionBase = rates.getPensionUpperLimit();
    if (pensionBase < rates.getPensionLowerLimit()) pensionBase = rates.getPensionLowerLimit();
    long pension = Math.round(pensionBase * rates.getNationalPension().doubleValue() / 2);

    // 건강보험 = 보수월액 × 요율 ÷ 2
    long health = Math.round(totalPay * rates.getHealthInsurance().doubleValue() / 2);

    // 장기요양보험 = 건강보험(전액) × 요율 ÷ 2
    long healthTotal = Math.round(totalPay * rates.getHealthInsurance().doubleValue());
    long ltc = Math.round(healthTotal * rates.getLongTermCare().doubleValue() / 2);

    // 고용보험 = 보수월액 × 근로자요율
    long employment = Math.round(totalPay * rates.getEmploymentInsurance().doubleValue());

    // ── 소득세 ──
    Employee emp = employeeRepository.findById(request.getEmpId())
            .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

    TaxWithholdingResDto tax = taxWithholdingService.getTax(
            currentYear, totalPay, emp.getDependentsCount());

    long incomeTax = tax.getIncomeTax();
    long localIncomeTax = tax.getLocalIncomeTax();

    // ── 합계 ──
    long totalDeduction = pension + health + ltc + employment + incomeTax + localIncomeTax;

    return CalcDeductionResDto.builder()
            .nationalPension(pension)
            .healthInsurance(health)
            .longTermCare(ltc)
            .employmentInsurance(employment)
            .incomeTax(incomeTax)
            .localIncomeTax(localIncomeTax)
            .totalDeduction(totalDeduction)
            .netPay(totalPay - totalDeduction)
            .build();
}
```

---

## 16. ErrorCode 추가 (전자결재 연동)

```java
// 전자결재 연동
OVERTIME_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 초과근무 신청을 찾을 수 없습니다."),
OVERTIME_ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 급여대장에 적용된 초과근무입니다."),
SALARY_CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "서명 완료된 연봉계약을 찾을 수 없습니다."),
PAY_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 법정수당 급여항목을 찾을 수 없습니다."),
```

---

# 보완 2: 연차수당 산정

> 급여정책 → 법정수당산정에 연차수당(LEAVE)이 활성화되어 있을 때 사용
> 탭1: 연말 미사용 연차 산정 (12월, 재직 사원)
> 탭2: 퇴직자 연차 정산 (퇴직월, 퇴직 사원)
> 산정 공식: 일 통상임금 = 통상임금(월) ÷ 209 × 8, 연차수당 = 미사용연차일수 × 일 통상임금

---

## 17. AllowanceType / AllowanceStatus Enum (신규)
**파일 위치**: `pay/enums/AllowanceType.java`, `pay/enums/AllowanceStatus.java`

```java
package com.peoplecore.pay.enums;

public enum AllowanceType {
    YEAR_END,       // 연말 미사용 연차
    RESIGNED        // 퇴직자 연차 정산
}
```

```java
package com.peoplecore.pay.enums;

public enum AllowanceStatus {
    PENDING,        // 미산정 (대상자)
    CALCULATED,     // 산정완료
    APPLIED         // 급여반영
}
```

---

## 18. LeaveAllowance Entity (신규)
**파일 위치**: `pay/domain/LeaveAllowance.java`

```java
package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.pay.enums.AllowanceStatus;
import com.peoplecore.pay.enums.AllowanceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "leave_allowance",
    indexes = {
        @Index(name = "idx_leave_allowance_company_year", columnList = "company_id, year, allowance_type"),
        @Index(name = "idx_leave_allowance_emp", columnList = "emp_id, year")
    })
public class LeaveAllowance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long allowanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private Integer year;                   // 기준연도

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AllowanceType allowanceType;    // YEAR_END / RESIGNED

    private Long normalMonthlySalary;       // 통상임금(월)
    private Long dailyWage;                 // 일 통상임금

    @Column(precision = 5, scale = 1)
    private BigDecimal totalLeaveDays;      // 부여일수

    @Column(precision = 5, scale = 1)
    private BigDecimal usedLeaveDays;       // 사용일수

    @Column(precision = 5, scale = 1)
    private BigDecimal unusedLeaveDays;     // 미사용일수 (수정 가능)

    private Long allowanceAmount;           // 산정금액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AllowanceStatus status = AllowanceStatus.PENDING;

    private Long appliedPayrollRunId;       // 반영된 급여대장 ID
    private String appliedMonth;            // 반영월 (yyyy-MM)

    private LocalDate resignDate;           // 퇴직일 (퇴직자용)


    // ── 수당 산정 ──
    public void calculate(Long normalMonthlySalary, Long dailyWage,
                          BigDecimal totalDays, BigDecimal usedDays,
                          BigDecimal unusedDays, Long amount) {
        this.normalMonthlySalary = normalMonthlySalary;
        this.dailyWage = dailyWage;
        this.totalLeaveDays = totalDays;
        this.usedLeaveDays = usedDays;
        this.unusedLeaveDays = unusedDays;
        this.allowanceAmount = amount;
        this.status = AllowanceStatus.CALCULATED;
    }

    // ── 미사용일수 수정 (관리자 조정) ──
    public void updateUnusedDays(BigDecimal unusedDays, Long dailyWage) {
        this.unusedLeaveDays = unusedDays;
        this.allowanceAmount = unusedDays.multiply(BigDecimal.valueOf(dailyWage))
                .longValue();
    }

    // ── 급여대장 반영 완료 ──
    public void markApplied(Long payrollRunId, String appliedMonth) {
        this.appliedPayrollRunId = payrollRunId;
        this.appliedMonth = appliedMonth;
        this.status = AllowanceStatus.APPLIED;
    }
}
```

---

## 19. LeaveAllowanceRepository (신규)
**파일 위치**: `pay/repository/LeaveAllowanceRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.LeaveAllowance;
import com.peoplecore.pay.enums.AllowanceStatus;
import com.peoplecore.pay.enums.AllowanceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LeaveAllowanceRepository extends JpaRepository<LeaveAllowance, Long> {

    // 연도 + 유형별 목록 (JOIN FETCH)
    @Query("SELECT la FROM LeaveAllowance la " +
           "JOIN FETCH la.employee e " +
           "JOIN FETCH e.dept " +
           "JOIN FETCH e.grade " +
           "WHERE la.company.companyId = :companyId " +
           "AND la.year = :year " +
           "AND la.allowanceType = :type " +
           "ORDER BY e.empName")
    List<LeaveAllowance> findAllByCompanyAndYearAndType(
            @Param("companyId") UUID companyId,
            @Param("year") Integer year,
            @Param("type") AllowanceType type
    );

    // 특정 사원 중복 체크
    boolean existsByCompany_CompanyIdAndEmployee_EmpIdAndYearAndAllowanceType(
            UUID companyId, Long empId, Integer year, AllowanceType type
    );

    // 상태별 카운트
    @Query("SELECT COUNT(la) FROM LeaveAllowance la " +
           "WHERE la.company.companyId = :companyId " +
           "AND la.year = :year " +
           "AND la.allowanceType = :type " +
           "AND la.status = :status")
    long countByStatus(
            @Param("companyId") UUID companyId,
            @Param("year") Integer year,
            @Param("type") AllowanceType type,
            @Param("status") AllowanceStatus status
    );

    // 선택된 ID 목록으로 조회
    List<LeaveAllowance> findByAllowanceIdInAndCompany_CompanyId(List<Long> ids, UUID companyId);
}
```

---

## 20. VacationRemainderRepository (신규)
**파일 위치**: `vacation/repository/VacationRemainderRepository.java`

```java
package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.VacationRemainder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VacationRemainderRepository extends JpaRepository<VacationRemainder, Long> {

    // 사원의 해당 연도 연차 잔여 조회
    Optional<VacationRemainder> findByCompanyIdAndEmpIdAndVacRemYear(
            UUID companyId, Long empId, Integer year
    );
}
```

---

## 21. LeaveAllowanceSummaryResDto (신규)
**파일 위치**: `pay/dtos/LeaveAllowanceSummaryResDto.java`

> 연차수당 산정 목록 상단 요약 + 사원 리스트

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
public class LeaveAllowanceSummaryResDto {

    private Integer totalTarget;            // 대상자 수
    private Integer calculatedCount;        // 산정 완료 수
    private Integer appliedCount;           // 급여 반영 수
    private Long totalAllowanceAmount;      // 총 산정액

    private List<LeaveAllowanceResDto> employees;
}
```

---

## 22. LeaveAllowanceResDto (신규)
**파일 위치**: `pay/dtos/LeaveAllowanceResDto.java`

> 사원별 연차수당 행

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.LeaveAllowance;
import com.peoplecore.pay.enums.AllowanceStatus;
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
public class LeaveAllowanceResDto {

    private Long allowanceId;
    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private LocalDate hireDate;
    private LocalDate resignDate;           // 퇴직자용 (nullable)

    private Long normalMonthlySalary;       // 통상임금(월)
    private Long dailyWage;                 // 일 통상임금

    private BigDecimal totalLeaveDays;      // 부여일수
    private BigDecimal usedLeaveDays;       // 사용일수
    private BigDecimal unusedLeaveDays;     // 미사용일수 (수정 가능)

    private Long allowanceAmount;           // 산정금액
    private AllowanceStatus status;         // PENDING / CALCULATED / APPLIED
    private String appliedMonth;            // 반영월

    public static LeaveAllowanceResDto fromEntity(LeaveAllowance la) {
        return LeaveAllowanceResDto.builder()
                .allowanceId(la.getAllowanceId())
                .empId(la.getEmployee().getEmpId())
                .empName(la.getEmployee().getEmpName())
                .deptName(la.getEmployee().getDept().getDeptName())
                .gradeName(la.getEmployee().getGrade().getGradeName())
                .hireDate(la.getEmployee().getEmpHireDate())
                .resignDate(la.getResignDate())
                .normalMonthlySalary(la.getNormalMonthlySalary())
                .dailyWage(la.getDailyWage())
                .totalLeaveDays(la.getTotalLeaveDays())
                .usedLeaveDays(la.getUsedLeaveDays())
                .unusedLeaveDays(la.getUnusedLeaveDays())
                .allowanceAmount(la.getAllowanceAmount())
                .status(la.getStatus())
                .appliedMonth(la.getAppliedMonth())
                .build();
    }
}
```

---

## 23. LeaveAllowanceService (신규)
**파일 위치**: `pay/service/LeaveAllowanceService.java`

```java
package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.LeaveAllowance;
import com.peoplecore.pay.domain.PayItems;
import com.peoplecore.pay.domain.PayrollDetails;
import com.peoplecore.pay.domain.PayrollRuns;
import com.peoplecore.pay.dtos.LeaveAllowanceResDto;
import com.peoplecore.pay.dtos.LeaveAllowanceSummaryResDto;
import com.peoplecore.pay.enums.*;
import com.peoplecore.pay.repository.LeaveAllowanceRepository;
import com.peoplecore.pay.repository.PayItemsRepository;
import com.peoplecore.pay.repository.PayrollDetailsRepository;
import com.peoplecore.pay.repository.PayrollRunsRepository;
import com.peoplecore.salarycontract.domain.ContractStatus;
import com.peoplecore.salarycontract.domain.SalaryContract;
import com.peoplecore.salarycontract.repository.SalaryContractRepository;
import com.peoplecore.vacation.entity.VacationRemainder;
import com.peoplecore.vacation.repository.VacationRemainderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LeaveAllowanceService {

    @Autowired private LeaveAllowanceRepository leaveAllowanceRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private SalaryContractRepository salaryContractRepository;
    @Autowired private VacationRemainderRepository vacationRemainderRepository;
    @Autowired private PayItemsRepository payItemsRepository;
    @Autowired private PayrollRunsRepository payrollRunsRepository;
    @Autowired private PayrollDetailsRepository payrollDetailsRepository;


    // ══════════════════════════════════════════════════
    //  연말 미사용 연차 산정 목록
    // ══════════════════════════════════════════════════
    public LeaveAllowanceSummaryResDto getYearEndList(UUID companyId, Integer year) {
        return buildSummary(companyId, year, AllowanceType.YEAR_END);
    }


    // ══════════════════════════════════════════════════
    //  퇴직자 연차 정산 목록
    // ══════════════════════════════════════════════════
    public LeaveAllowanceSummaryResDto getResignedList(UUID companyId, Integer year) {
        return buildSummary(companyId, year, AllowanceType.RESIGNED);
    }


    // ══════════════════════════════════════════════════
    //  수당 산정 (선택된 대상자)
    // ══════════════════════════════════════════════════
    @Transactional
    public void calculate(UUID companyId, Integer year, AllowanceType type, List<Long> empIds) {

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        // 법정수당 항목(LEAVE) 존재 확인
        payItemsRepository.findByCompany_CompanyIdAndIsLegalTrueAndLegalCalcType(companyId, LegalCalcType.LEAVE)
                .orElseThrow(() -> new CustomException(ErrorCode.LEAVE_ALLOWANCE_NOT_ENABLED));

        for (Long empId : empIds) {
            Employee emp = employeeRepository.findById(empId)
                    .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

            // 이미 산정된 경우 스킵
            if (leaveAllowanceRepository.existsByCompany_CompanyIdAndEmployee_EmpIdAndYearAndAllowanceType(
                    companyId, empId, year, type)) {
                continue;
            }

            // 통상임금(월) = 연봉 ÷ 12
            SalaryContract contract = salaryContractRepository
                    .findTopByEmployee_EmpIdAndStatusOrderByContractYearDesc(empId, ContractStatus.SIGNED)
                    .orElse(null);
            if (contract == null) continue;

            long monthlySalary = contract.getTotalAmount()
                    .divide(BigDecimal.valueOf(12), 0, RoundingMode.FLOOR)
                    .longValue();

            // 일 통상임금 = 통상임금(월) ÷ 209 × 8
            long dailyWage = Math.round((double) monthlySalary / 209 * 8);

            // 연차 잔여 조회
            VacationRemainder remainder = vacationRemainderRepository
                    .findByCompanyIdAndEmpIdAndVacRemYear(companyId, empId, year)
                    .orElse(null);

            BigDecimal totalDays = remainder != null ? remainder.getVacRemTotalDay() : BigDecimal.ZERO;
            BigDecimal usedDays = remainder != null ? remainder.getVacRemUsedDay() : BigDecimal.ZERO;
            BigDecimal unusedDays = totalDays.subtract(usedDays);

            // 미사용 0 이하이면 스킵
            if (unusedDays.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 산정금액 = 미사용일수 × 일 통상임금
            long amount = unusedDays.multiply(BigDecimal.valueOf(dailyWage)).longValue();

            LeaveAllowance allowance = LeaveAllowance.builder()
                    .company(company)
                    .employee(emp)
                    .year(year)
                    .allowanceType(type)
                    .resignDate(type == AllowanceType.RESIGNED ? emp.getEmpResign() : null)
                    .status(AllowanceStatus.PENDING)
                    .build();

            allowance.calculate(monthlySalary, dailyWage, totalDays, usedDays, unusedDays, amount);
            leaveAllowanceRepository.save(allowance);
        }
    }


    // ══════════════════════════════════════════════════
    //  미사용일수 수정 (관리자 조정)
    // ══════════════════════════════════════════════════
    @Transactional
    public void updateUnusedDays(UUID companyId, Long allowanceId, BigDecimal unusedDays) {

        LeaveAllowance la = leaveAllowanceRepository.findById(allowanceId)
                .filter(a -> a.getCompany().getCompanyId().equals(companyId))
                .orElseThrow(() -> new CustomException(ErrorCode.LEAVE_ALLOWANCE_NOT_FOUND));

        if (la.getStatus() == AllowanceStatus.APPLIED) {
            throw new CustomException(ErrorCode.LEAVE_ALLOWANCE_ALREADY_APPLIED);
        }

        la.updateUnusedDays(unusedDays, la.getDailyWage());
    }


    // ══════════════════════════════════════════════════
    //  급여대장 반영 (선택된 대상자)
    // ══════════════════════════════════════════════════
    @Transactional
    public void applyToPayroll(UUID companyId, List<Long> allowanceIds) {

        List<LeaveAllowance> allowances = leaveAllowanceRepository
                .findByAllowanceIdInAndCompany_CompanyId(allowanceIds, companyId);

        // 법정수당(LEAVE) 항목
        PayItems leavePayItem = payItemsRepository
                .findByCompany_CompanyIdAndIsLegalTrueAndLegalCalcType(companyId, LegalCalcType.LEAVE)
                .orElseThrow(() -> new CustomException(ErrorCode.LEAVE_ALLOWANCE_NOT_ENABLED));

        for (LeaveAllowance la : allowances) {
            if (la.getStatus() == AllowanceStatus.APPLIED) continue;
            if (la.getStatus() != AllowanceStatus.CALCULATED) {
                throw new CustomException(ErrorCode.LEAVE_ALLOWANCE_NOT_CALCULATED);
            }

            // 반영 대상 월 결정
            String targetMonth = resolveTargetMonth(la);

            // 해당 월 급여대장 조회 (없으면 에러)
            PayrollRuns run = payrollRunsRepository
                    .findByCompany_CompanyIdAndPayYearMonth(companyId, targetMonth)
                    .orElseThrow(() -> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));

            if (run.getPayrollStatus() != PayrollStatus.CALCULATING) {
                throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
            }

            // PayrollDetails 추가
            PayrollDetails detail = PayrollDetails.builder()
                    .payrollRuns(run)
                    .employee(la.getEmployee())
                    .payItemId(leavePayItem.getPayItemId())
                    .payItemName(leavePayItem.getPayItemName())
                    .payItemType(PayItemType.PAYMENT)
                    .amount(la.getAllowanceAmount())
                    .memo("연차수당 (" + la.getUnusedLeaveDays() + "일)")
                    .company(la.getCompany())
                    .build();
            payrollDetailsRepository.save(detail);

            // 급여대장 합계 재계산
            recalculateTotals(run);

            // 반영 완료 처리
            la.markApplied(run.getPayrollRunId(), targetMonth);
        }
    }


    // ── 헬퍼: 반영 대상 월 결정 ──
    private String resolveTargetMonth(LeaveAllowance la) {
        if (la.getAllowanceType() == AllowanceType.YEAR_END) {
            // 연말 미사용 → 해당연도 12월
            return la.getYear() + "-12";
        } else {
            // 퇴직자 → 퇴직월
            if (la.getResignDate() == null) {
                throw new CustomException(ErrorCode.LEAVE_ALLOWANCE_NO_RESIGN_DATE);
            }
            return la.getResignDate().getYear() + "-"
                    + String.format("%02d", la.getResignDate().getMonthValue());
        }
    }


    // ── 헬퍼: 요약 DTO 빌드 ──
    private LeaveAllowanceSummaryResDto buildSummary(UUID companyId, Integer year, AllowanceType type) {

        List<LeaveAllowance> list = leaveAllowanceRepository
                .findAllByCompanyAndYearAndType(companyId, year, type);

        // 아직 LeaveAllowance 엔티티가 없는 대상자도 보여줘야 함
        // → 최초 조회 시 대상 사원을 PENDING 상태로 자동 생성
        if (list.isEmpty()) {
            createPendingRecords(companyId, year, type);
            list = leaveAllowanceRepository.findAllByCompanyAndYearAndType(companyId, year, type);
        }

        List<LeaveAllowanceResDto> employees = list.stream()
                .map(LeaveAllowanceResDto::fromEntity)
                .toList();

        long calculatedCount = list.stream()
                .filter(la -> la.getStatus() == AllowanceStatus.CALCULATED
                           || la.getStatus() == AllowanceStatus.APPLIED)
                .count();

        long appliedCount = list.stream()
                .filter(la -> la.getStatus() == AllowanceStatus.APPLIED)
                .count();

        long totalAmount = list.stream()
                .filter(la -> la.getAllowanceAmount() != null)
                .mapToLong(LeaveAllowance::getAllowanceAmount)
                .sum();

        return LeaveAllowanceSummaryResDto.builder()
                .totalTarget(list.size())
                .calculatedCount((int) calculatedCount)
                .appliedCount((int) appliedCount)
                .totalAllowanceAmount(totalAmount)
                .employees(employees)
                .build();
    }


    // ── 헬퍼: 대상 사원 PENDING 레코드 자동 생성 ──
    @Transactional
    private void createPendingRecords(UUID companyId, Integer year, AllowanceType type) {

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        List<Employee> targets;

        if (type == AllowanceType.YEAR_END) {
            // 재직/휴직 사원 (퇴직자 제외)
            targets = employeeRepository
                    .findByCompany_CompanyIdAndEmpStatusInAndDeleteAtIsNull(
                            companyId, List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE));
        } else {
            // 해당 연도 퇴직 사원
            targets = employeeRepository
                    .findByCompany_CompanyIdAndEmpStatusAndDeleteAtIsNull(
                            companyId, EmpStatus.RESIGNED)
                    .stream()
                    .filter(e -> e.getEmpResign() != null && e.getEmpResign().getYear() == year)
                    .toList();
        }

        for (Employee emp : targets) {
            if (leaveAllowanceRepository.existsByCompany_CompanyIdAndEmployee_EmpIdAndYearAndAllowanceType(
                    companyId, emp.getEmpId(), year, type)) {
                continue;
            }

            LeaveAllowance allowance = LeaveAllowance.builder()
                    .company(company)
                    .employee(emp)
                    .year(year)
                    .allowanceType(type)
                    .resignDate(type == AllowanceType.RESIGNED ? emp.getEmpResign() : null)
                    .status(AllowanceStatus.PENDING)
                    .build();
            leaveAllowanceRepository.save(allowance);
        }
    }


    // ── 헬퍼: 급여대장 합계 재계산 (PayrollService와 동일 로직) ──
    private void recalculateTotals(PayrollRuns run) {
        List<PayrollDetails> allDetails = payrollDetailsRepository.findByPayrollRuns(run);

        long totalPay = allDetails.stream()
                .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
                .mapToLong(PayrollDetails::getAmount).sum();
        long totalDeduction = allDetails.stream()
                .filter(d -> d.getPayItemType() == PayItemType.DEDUCTION)
                .mapToLong(PayrollDetails::getAmount).sum();

        int empCount = (int) allDetails.stream()
                .map(d -> d.getEmployee().getEmpId())
                .distinct().count();

        run.updateTotals(empCount, totalPay, totalDeduction, totalPay - totalDeduction);
    }
}
```

---

## 24. LeaveAllowanceController (신규)
**파일 위치**: `pay/controller/LeaveAllowanceController.java`

```java
package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.LeaveAllowanceSummaryResDto;
import com.peoplecore.pay.enums.AllowanceType;
import com.peoplecore.pay.service.LeaveAllowanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/admin/leave-allowance")
@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
public class LeaveAllowanceController {

    @Autowired
    private LeaveAllowanceService leaveAllowanceService;


    /**
     * [탭1] 연말 미사용 연차 산정 목록
     * GET /pay/admin/leave-allowance/year-end?year=2026
     */
    @GetMapping("/year-end")
    public ResponseEntity<LeaveAllowanceSummaryResDto> getYearEndList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam Integer year) {
        return ResponseEntity.ok(
                leaveAllowanceService.getYearEndList(companyId, year));
    }

    /**
     * [탭2] 퇴직자 연차 정산 목록
     * GET /pay/admin/leave-allowance/resigned?year=2026
     */
    @GetMapping("/resigned")
    public ResponseEntity<LeaveAllowanceSummaryResDto> getResignedList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam Integer year) {
        return ResponseEntity.ok(
                leaveAllowanceService.getResignedList(companyId, year));
    }

    /**
     * 수당 산정 (선택된 사원)
     * POST /pay/admin/leave-allowance/calculate?year=2026&type=YEAR_END
     * Body: [1, 2, 3]  (empId 목록)
     */
    @PostMapping("/calculate")
    public ResponseEntity<Void> calculate(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam Integer year,
            @RequestParam AllowanceType type,
            @RequestBody List<Long> empIds) {
        leaveAllowanceService.calculate(companyId, year, type, empIds);
        return ResponseEntity.ok().build();
    }

    /**
     * 미사용일수 수정
     * PUT /pay/admin/leave-allowance/{allowanceId}/unused-days?days=5.0
     */
    @PutMapping("/{allowanceId}/unused-days")
    public ResponseEntity<Void> updateUnusedDays(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long allowanceId,
            @RequestParam BigDecimal days) {
        leaveAllowanceService.updateUnusedDays(companyId, allowanceId, days);
        return ResponseEntity.ok().build();
    }

    /**
     * 급여대장 반영 (선택된 산정건)
     * POST /pay/admin/leave-allowance/apply-to-payroll
     * Body: [1, 2, 3]  (allowanceId 목록)
     */
    @PostMapping("/apply-to-payroll")
    public ResponseEntity<Void> applyToPayroll(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody List<Long> allowanceIds) {
        leaveAllowanceService.applyToPayroll(companyId, allowanceIds);
        return ResponseEntity.ok().build();
    }
}
```

---

## 25. EmployeeRepository 추가 쿼리

```java
// 퇴직 사원 조회 (연차수당 - 퇴직자용)
List<Employee> findByCompany_CompanyIdAndEmpStatusAndDeleteAtIsNull(
        UUID companyId, EmpStatus empStatus
);
```

---

## 26. ErrorCode 추가 (연차수당)

```java
// 연차수당
LEAVE_ALLOWANCE_NOT_ENABLED(HttpStatus.BAD_REQUEST, "연차수당 법정수당 항목이 설정되어 있지 않습니다."),
LEAVE_ALLOWANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 연차수당 산정 건을 찾을 수 없습니다."),
LEAVE_ALLOWANCE_NOT_CALCULATED(HttpStatus.BAD_REQUEST, "산정이 완료되지 않은 건은 급여대장에 반영할 수 없습니다."),
LEAVE_ALLOWANCE_ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 급여대장에 반영된 건입니다."),
LEAVE_ALLOWANCE_NO_RESIGN_DATE(HttpStatus.BAD_REQUEST, "퇴직일이 설정되지 않은 사원입니다."),
```

---

## 프론트 연동 참고 (보완분)

### 일당/시급 기준 조회
```
GET /pay/admin/payroll/1/employees/1/wage-info
Headers: X-User-Company: {companyId}

Response:
{
  "hourlyWage": 14354,
  "dailyWage": 114832,
  "overtimeHourlyWage": 21531
}
```

> 프론트에서 표시 예시:
> - 시급: 14,354원
> - 일당 (8h): 114,832원
> - 연장/야간/휴일(1.5배): 21,531원/h

### 이달 승인된 전자결재 조회
```
GET /pay/admin/payroll/1/employees/1/approved-overtime
Headers: X-User-Company: {companyId}

Response:
{
  "items": [
    {
      "otId": 10,
      "otTypeFlag": 1,
      "otTypeLabel": "연장",
      "premiumRate": 1.5,
      "otDate": "2026-04-05",
      "hours": 2,
      "amount": 43062,
      "applied": false
    },
    {
      "otId": 11,
      "otTypeFlag": 3,
      "otTypeLabel": "연장+야간",
      "premiumRate": 2.0,
      "otDate": "2026-04-18",
      "hours": 2,
      "amount": 57416,
      "applied": false
    },
    {
      "otId": 12,
      "otTypeFlag": 4,
      "otTypeLabel": "휴일",
      "premiumRate": 1.5,
      "otDate": "2026-04-20",
      "hours": 4,
      "amount": 86124,
      "applied": false
    }
  ],
  "totalHours": 8,
  "totalAmount": 186602
}
```

### 건별 적용
```
POST /pay/admin/payroll/1/employees/1/apply-overtime/10
Headers: X-User-Company: {companyId}
```

### 전체 적용
```
POST /pay/admin/payroll/1/employees/1/apply-all-overtime
Headers: X-User-Company: {companyId}
```

### 연말 미사용 연차 목록
```
GET /pay/admin/leave-allowance/year-end?year=2026
Headers: X-User-Company: {companyId}

Response:
{
  "totalTarget": 5,
  "calculatedCount": 0,
  "appliedCount": 0,
  "totalAllowanceAmount": 2116746,
  "employees": [
    {
      "allowanceId": 1,
      "empId": 1,
      "empName": "김민수",
      "deptName": "개발팀",
      "gradeName": "대리",
      "hireDate": "2022-03-02",
      "resignDate": null,
      "normalMonthlySalary": 3500000,
      "dailyWage": 133971,
      "totalLeaveDays": 15,
      "usedLeaveDays": 10,
      "unusedLeaveDays": 5,
      "allowanceAmount": 669855,
      "status": "CALCULATED",
      "appliedMonth": null
    }
  ]
}
```

### 수당 산정
```
POST /pay/admin/leave-allowance/calculate?year=2026&type=YEAR_END
Headers: X-User-Company: {companyId}
Body: [1, 2, 3, 4, 5]
```

### 미사용일수 수정
```
PUT /pay/admin/leave-allowance/1/unused-days?days=4.0
Headers: X-User-Company: {companyId}
```

### 급여대장 반영
```
POST /pay/admin/leave-allowance/apply-to-payroll
Headers: X-User-Company: {companyId}
Body: [1, 2, 3]
```

---

## 참고: 수당 계산 공식

### 비트마스크 가산율 계산
| otTypeFlag | 유형 | 가산율 계산 | 기본값 |
|------------|------|------------|--------|
| 1 | 연장 | 1.0 + 연장가산율 | 1.5배 |
| 2 | 야간 | 1.0 + 야간가산율 | 1.5배 |
| 3 | 연장+야간 | 1.0 + 연장가산율 + 야간가산율 | 2.0배 |
| 4 | 휴일 | 1.0 + 휴일가산율 | 1.5배 |
| 5 | 휴일+연장 | 1.0 + 휴일가산율 + 연장가산율 | 2.0배 |
| 6 | 휴일+야간 | 1.0 + 휴일가산율 + 야간가산율 | 2.0배 |
| 7 | 휴일+연장+야간 | 1.0 + 전부 | 2.5배 |

> 가산율 고정 0.5 (50%), 유형이 중첩될수록 0.5씩 추가

### 연차수당 산정 공식
| 항목 | 공식 |
|------|------|
| 통상임금(월) | 연봉 ÷ 12 |
| 일 통상임금 | 통상임금(월) ÷ 209 × 8 |
| 연차수당 | 미사용 연차일수 × 일 통상임금 |
| 시급 | 통상임금(월) ÷ 209 |

### 반영 시점
- **연말 미사용 연차**: 해당연도 12월 급여대장에 반영
- **퇴직자 연차 정산**: 퇴직월 급여대장에 반영

### 조건
- 급여정책 → 법정수당산정에 연차수당(`LegalCalcType.LEAVE`)이 활성화(PayItems에 등록)되어야 사용 가능
- 급여대장이 `CALCULATING` 상태일 때만 반영 가능


---

## 27. 입사일 기준 연차수당 산정 — 추가 코드

> 회사의 연차부여 정책(`VacationPolicy.policyBaseType`)에 따라 화면 분기:
> - `FISCAL` (회계년도 기준) → 기존 연말 미사용 연차 산정 (§23 getYearEndList 그대로)
> - `HIRE` (입사일 기준) → 매월 입사 기념일 도래 사원 대상 산정 (이 섹션에서 추가)
> - `RESIGNED` (퇴직자) → 기존 퇴직자 정산 (정책 무관, 그대로)

### API 추가

| # | Method | URL | 설명 |
|---|--------|-----|------|
| 16 | GET | `/pay/admin/leave-allowance/policy-type` | 회사 연차정책 타입 조회 (FISCAL/HIRE) |
| 17 | GET | `/pay/admin/leave-allowance/anniversary?yearMonth=2026-04` | 입사일 기준 연차수당 대상자 목록 |

### 파일 체크리스트 추가

| # | 구분 | 파일명 | 위치 | 작업 |
|---|------|--------|------|------|
| 27 | Enum | `AllowanceType.java` | pay/enums/ | 수정 (FISCAL_YEAR, ANNIVERSARY 추가) |
| 28 | Repository | `VacationPolicyRepository.java` | vacation/repository/ | 신규 |
| 29 | DTO | `LeavePolicyTypeResDto.java` | pay/dtos/ | 신규 |
| 30 | Service | `LeaveAllowanceService.java` | pay/service/ | anniversary 메서드 추가 |
| 31 | Controller | `LeaveAllowanceController.java` | pay/controller/ | 엔드포인트 추가 |

---

### 27-1. AllowanceType enum 수정
**파일 위치**: `pay/enums/AllowanceType.java`

> 기존 YEAR_END → FISCAL_YEAR 리네임, ANNIVERSARY 추가

```java
package com.peoplecore.pay.enums;

public enum AllowanceType {
    FISCAL_YEAR,    // 회계년도 기준 (구 YEAR_END)
    ANNIVERSARY,    // 입사일 기준
    RESIGNED        // 퇴직자 정산
}
```

> ⚠️ 기존 코드에서 `AllowanceType.YEAR_END`를 쓰는 곳은 모두 `AllowanceType.FISCAL_YEAR`로 변경
> - §23 LeaveAllowanceService: `getYearEndList`, `createPendingRecords`, `resolveTargetMonth`
> - §24 LeaveAllowanceController: `/year-end` 엔드포인트 (URL은 유지, enum만 변경)

---

### 27-2. VacationPolicyRepository (신규)
**파일 위치**: `vacation/repository/VacationPolicyRepository.java`

```java
package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.VacationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VacationPolicyRepository extends JpaRepository<VacationPolicy, Long> {

    Optional<VacationPolicy> findByCompanyId(UUID companyId);
}
```

---

### 27-3. LeavePolicyTypeResDto (신규)
**파일 위치**: `pay/dtos/LeavePolicyTypeResDto.java`

> 프론트엔드가 탭을 분기하기 위한 정책 타입 응답

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
public class LeavePolicyTypeResDto {

    private String policyBaseType;          // "FISCAL" 또는 "HIRE"
    private String fiscalYearStart;         // 회계년도 시작일 (mm-dd), HIRE면 null
}
```

---

### 27-4. LeaveAllowanceService — 추가 메서드
**파일 위치**: `pay/service/LeaveAllowanceService.java`

> 기존 §23 Service에 아래 의존성 + 메서드 추가

#### 추가 의존성

```java
    @Autowired private VacationPolicyRepository vacationPolicyRepository;
```

#### 정책 타입 조회

```java
    // ══════════════════════════════════════════════════
    //  회사 연차정책 타입 조회
    // ══════════════════════════════════════════════════
    public LeavePolicyTypeResDto getPolicyType(UUID companyId) {
        VacationPolicy policy = vacationPolicyRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_POLICY_NOT_FOUND));

        return LeavePolicyTypeResDto.builder()
                .policyBaseType(policy.getPolicyBaseType().name())
                .fiscalYearStart(policy.getPolicyFiscalYearStart())
                .build();
    }
```

#### 입사일 기준 연차수당 목록

```java
    // ══════════════════════════════════════════════════
    //  입사일 기준 연차수당 대상자 목록
    // ══════════════════════════════════════════════════

    /**
     * yearMonth(예: "2026-04") 에 입사 기념일이 도래하는 사원들의
     * 연차수당 산정 현황을 반환한다.
     *
     * 입사 기념일 = empHireDate의 월(月)이 조회 월과 같은 사원
     * 예: 입사일 2022-04-15 → 매년 4월이 기념일
     */
    public LeaveAllowanceSummaryResDto getAnniversaryList(UUID companyId, String yearMonth) {

        int targetYear = Integer.parseInt(yearMonth.substring(0, 4));
        int targetMonth = Integer.parseInt(yearMonth.substring(5, 7));

        List<LeaveAllowance> list = leaveAllowanceRepository
                .findAllByCompanyAndYearAndType(companyId, targetYear, AllowanceType.ANNIVERSARY);

        // 해당 월 기념일 대상자만 필터
        List<LeaveAllowance> filtered = list.stream()
                .filter(la -> la.getEmployee().getEmpHireDate() != null
                        && la.getEmployee().getEmpHireDate().getMonthValue() == targetMonth)
                .toList();

        // 최초 조회 시 PENDING 레코드 자동 생성
        if (filtered.isEmpty()) {
            createAnniversaryPendingRecords(companyId, targetYear, targetMonth);
            list = leaveAllowanceRepository
                    .findAllByCompanyAndYearAndType(companyId, targetYear, AllowanceType.ANNIVERSARY);
            filtered = list.stream()
                    .filter(la -> la.getEmployee().getEmpHireDate() != null
                            && la.getEmployee().getEmpHireDate().getMonthValue() == targetMonth)
                    .toList();
        }

        List<LeaveAllowanceResDto> employees = filtered.stream()
                .map(LeaveAllowanceResDto::fromEntity)
                .toList();

        long calculatedCount = filtered.stream()
                .filter(la -> la.getStatus() == AllowanceStatus.CALCULATED
                           || la.getStatus() == AllowanceStatus.APPLIED)
                .count();

        long appliedCount = filtered.stream()
                .filter(la -> la.getStatus() == AllowanceStatus.APPLIED)
                .count();

        long totalAmount = filtered.stream()
                .filter(la -> la.getAllowanceAmount() != null)
                .mapToLong(LeaveAllowance::getAllowanceAmount)
                .sum();

        return LeaveAllowanceSummaryResDto.builder()
                .totalTarget(filtered.size())
                .calculatedCount((int) calculatedCount)
                .appliedCount((int) appliedCount)
                .totalAllowanceAmount(totalAmount)
                .employees(employees)
                .build();
    }
```

#### 입사일 기준 PENDING 레코드 생성

```java
    // ── 헬퍼: 입사일 기준 대상 사원 PENDING 레코드 생성 ──
    @Transactional
    private void createAnniversaryPendingRecords(UUID companyId, int year, int month) {

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        // 재직/휴직 사원 중 입사 월이 대상 월인 사원
        List<Employee> targets = employeeRepository
                .findByCompany_CompanyIdAndEmpStatusInAndDeleteAtIsNull(
                        companyId, List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE))
                .stream()
                .filter(e -> e.getEmpHireDate() != null
                        && e.getEmpHireDate().getMonthValue() == month)
                .toList();

        for (Employee emp : targets) {
            if (leaveAllowanceRepository.existsByCompany_CompanyIdAndEmployee_EmpIdAndYearAndAllowanceType(
                    companyId, emp.getEmpId(), year, AllowanceType.ANNIVERSARY)) {
                continue;
            }

            LeaveAllowance allowance = LeaveAllowance.builder()
                    .company(company)
                    .employee(emp)
                    .year(year)
                    .allowanceType(AllowanceType.ANNIVERSARY)
                    .status(AllowanceStatus.PENDING)
                    .build();
            leaveAllowanceRepository.save(allowance);
        }
    }
```

#### calculate 메서드 — ANNIVERSARY 분기 추가

> 기존 §23의 `calculate` 메서드에서 VacationRemainder 조회 부분을 분기 처리

```java
    /**
     * 수당 산정 (기존 calculate 메서드 수정)
     *
     * ANNIVERSARY 타입일 때:
     * - 미사용일수 = VacationRemainder에서 직전 연차기간(입사기념일~입사기념일) 잔여
     * - vacRemYear 기준: 현재 기준연도 - 1 (만료되는 기간의 잔여)
     */
    @Transactional
    public void calculate(UUID companyId, Integer year, AllowanceType type, List<Long> empIds) {

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        payItemsRepository.findByCompany_CompanyIdAndIsLegalTrueAndLegalCalcType(companyId, LegalCalcType.LEAVE)
                .orElseThrow(() -> new CustomException(ErrorCode.LEAVE_ALLOWANCE_NOT_ENABLED));

        for (Long empId : empIds) {
            Employee emp = employeeRepository.findById(empId)
                    .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

            if (leaveAllowanceRepository.existsByCompany_CompanyIdAndEmployee_EmpIdAndYearAndAllowanceType(
                    companyId, empId, year, type)) {
                continue;
            }

            // 통상임금(월) = 연봉 ÷ 12
            SalaryContract contract = salaryContractRepository
                    .findTopByEmployee_EmpIdAndStatusOrderByContractYearDesc(empId, ContractStatus.SIGNED)
                    .orElse(null);
            if (contract == null) continue;

            long monthlySalary = contract.getTotalAmount()
                    .divide(BigDecimal.valueOf(12), 0, RoundingMode.FLOOR)
                    .longValue();

            // 일 통상임금 = 통상임금(월) ÷ 209 × 8
            long dailyWage = Math.round((double) monthlySalary / 209 * 8);

            // ── 연차 잔여 조회 (정책별 분기) ──
            BigDecimal totalDays;
            BigDecimal usedDays;
            BigDecimal unusedDays;

            if (type == AllowanceType.ANNIVERSARY) {
                // 입사일 기준: 직전 연차기간의 잔여일수
                // 입사기념일 도래 시 만료되는 기간 = year - 1년차
                int lookupYear = year - 1;
                VacationRemainder remainder = vacationRemainderRepository
                        .findByCompanyIdAndEmpIdAndVacRemYear(companyId, empId, lookupYear)
                        .orElse(null);

                totalDays = remainder != null ? remainder.getVacRemTotalDay() : BigDecimal.ZERO;
                usedDays = remainder != null ? remainder.getVacRemUsedDay() : BigDecimal.ZERO;
                unusedDays = totalDays.subtract(usedDays);
            } else {
                // 회계년도 기준 / 퇴직자: 해당 연도 잔여
                VacationRemainder remainder = vacationRemainderRepository
                        .findByCompanyIdAndEmpIdAndVacRemYear(companyId, empId, year)
                        .orElse(null);

                totalDays = remainder != null ? remainder.getVacRemTotalDay() : BigDecimal.ZERO;
                usedDays = remainder != null ? remainder.getVacRemUsedDay() : BigDecimal.ZERO;
                unusedDays = totalDays.subtract(usedDays);
            }

            if (unusedDays.compareTo(BigDecimal.ZERO) <= 0) continue;

            long amount = unusedDays.multiply(BigDecimal.valueOf(dailyWage)).longValue();

            LeaveAllowance allowance = LeaveAllowance.builder()
                    .company(company)
                    .employee(emp)
                    .year(year)
                    .allowanceType(type)
                    .resignDate(type == AllowanceType.RESIGNED ? emp.getEmpResign() : null)
                    .status(AllowanceStatus.PENDING)
                    .build();

            allowance.calculate(monthlySalary, dailyWage, totalDays, usedDays, unusedDays, amount);
            leaveAllowanceRepository.save(allowance);
        }
    }
```

#### resolveTargetMonth — ANNIVERSARY 분기 추가

```java
    // ── 헬퍼: 반영 대상 월 결정 (수정) ──
    private String resolveTargetMonth(LeaveAllowance la) {
        if (la.getAllowanceType() == AllowanceType.FISCAL_YEAR) {
            // 회계년도 → 해당연도 12월
            return la.getYear() + "-12";

        } else if (la.getAllowanceType() == AllowanceType.ANNIVERSARY) {
            // 입사일 기준 → 입사 기념일 월
            if (la.getEmployee().getEmpHireDate() == null) {
                throw new CustomException(ErrorCode.EMPLOYEE_HIRE_DATE_NOT_FOUND);
            }
            int month = la.getEmployee().getEmpHireDate().getMonthValue();
            return la.getYear() + "-" + String.format("%02d", month);

        } else {
            // 퇴직자 → 퇴직월
            if (la.getResignDate() == null) {
                throw new CustomException(ErrorCode.LEAVE_ALLOWANCE_NO_RESIGN_DATE);
            }
            return la.getResignDate().getYear() + "-"
                    + String.format("%02d", la.getResignDate().getMonthValue());
        }
    }
```

---

### 27-5. LeaveAllowanceController — 엔드포인트 추가
**파일 위치**: `pay/controller/LeaveAllowanceController.java`

> 기존 §24 Controller에 아래 엔드포인트 추가

```java
    /**
     * 회사 연차정책 타입 조회 (프론트엔드 탭 분기용)
     * GET /pay/admin/leave-allowance/policy-type
     *
     * 응답: { "policyBaseType": "FISCAL", "fiscalYearStart": "01-01" }
     *       or { "policyBaseType": "HIRE", "fiscalYearStart": null }
     */
    @GetMapping("/policy-type")
    public ResponseEntity<LeavePolicyTypeResDto> getPolicyType(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(
                leaveAllowanceService.getPolicyType(companyId));
    }


    /**
     * [탭1-B] 입사일 기준 연차수당 대상자 목록
     * GET /pay/admin/leave-allowance/anniversary?yearMonth=2026-04
     *
     * → 해당 월에 입사 기념일이 도래하는 사원 목록
     */
    @GetMapping("/anniversary")
    public ResponseEntity<LeaveAllowanceSummaryResDto> getAnniversaryList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam String yearMonth) {
        return ResponseEntity.ok(
                leaveAllowanceService.getAnniversaryList(companyId, yearMonth));
    }
```

> 기존 `/year-end` 엔드포인트의 내부 호출도 YEAR_END → FISCAL_YEAR 변경:
```java
    // 기존 코드 수정
    @GetMapping("/year-end")
    public ResponseEntity<LeaveAllowanceSummaryResDto> getYearEndList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam Integer year) {
        return ResponseEntity.ok(
                leaveAllowanceService.getFiscalYearList(companyId, year));  // 메서드명 변경
    }
```

> 기존 Service의 `getYearEndList` → `getFiscalYearList`로 리네임:
```java
    public LeaveAllowanceSummaryResDto getFiscalYearList(UUID companyId, Integer year) {
        return buildSummary(companyId, year, AllowanceType.FISCAL_YEAR);
    }
```

---

### 27-6. ErrorCode 추가

```java
// ── 연차수당 (입사일 기준) ──
VACATION_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "연차부여 정책이 설정되지 않았습니다."),
EMPLOYEE_HIRE_DATE_NOT_FOUND(HttpStatus.BAD_REQUEST, "사원의 입사일 정보가 없습니다."),
```

---

### 프론트엔드 탭 분기 가이드

```
[페이지 로드]
1. GET /leave-allowance/policy-type
   └─ policyBaseType 확인

2. 탭 렌더링
   ├─ FISCAL → [탭1] 회계년도 미사용 연차 (GET /year-end?year=2026)
   ├─ HIRE   → [탭1] 입사일 기준 연차     (GET /anniversary?yearMonth=2026-04)
   └─ 공통   → [탭2] 퇴직자 연차 정산     (GET /resigned?year=2026)
```

### 회계년도 vs 입사일 기준 비교

| 구분 | 회계년도 (FISCAL) | 입사일 (HIRE) |
|------|-------------------|---------------|
| AllowanceType | `FISCAL_YEAR` | `ANNIVERSARY` |
| 조회 파라미터 | `year` (연도) | `yearMonth` (연월) |
| 대상자 | 전 사원 일괄 | 해당 월 입사 기념일 도래 사원 |
| 연차기간 | 1/1 ~ 12/31 | 입사일 ~ 입사일 다음해 |
| VacationRemainder 조회 | `vacRemYear = year` | `vacRemYear = year - 1` |
| 급여반영 대상월 | 해당연도 12월 | 입사 기념일 월 |
| 산정 시기 | 연말 1회 | 매월 (해당 월 대상자) |

nth=2026-04
     *
     * → 해당 월에 입사 기념일이 도래하는 사원 목록
     */
    @GetMapping("/anniversary")
    public ResponseEntity<LeaveAllowanceSummaryResDto> getAnniversaryList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam String yearMonth) {
        return ResponseEntity.ok(
                leaveAllowanceService.getAnniversaryList(companyId, yearMonth));
    }
```

> 기존 `/year-end` 엔드포인트의 내부 호출도 YEAR_END → FISCAL_YEAR 변경:
```java
    // 기존 코드 수정
    @GetMapping("/year-end")
    public ResponseEntity<LeaveAllowanceSummaryResDto> getYearEndList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam Integer year) {
        return ResponseEntity.ok(
                leaveAllowanceService.getFiscalYearList(companyId, year));  // 메서드명 변경
    }
```

> 기존 Service의 `getYearEndList` → `getFiscalYearList`로 리네임:
```java
    public LeaveAllowanceSummaryResDto getFiscalYearList(UUID companyId, Integer year) {
        return buildSummary(companyId, year, AllowanceType.FISCAL_YEAR);
    }
```

---

### 27-6. ErrorCode 추가

```java
// ── 연차수당 (입사일 기준) ──
VACATION_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "연차부여 정책이 설정되지 않았습니다."),
EMPLOYEE_HIRE_DATE_NOT_FOUND(HttpStatus.BAD_REQUEST, "사원의 입사일 정보가 없습니다."),
```

---

### 프론트엔드 탭 분기 가이드

```
[페이지 로드]
1. GET /leave-allowance/policy-type
   └─ policyBaseType 확인

2. 탭 렌더링
   ├─ FISCAL → [탭1] 회계년도 미사용 연차 (GET /year-end?year=2026)
   ├─ HIRE   → [탭1] 입사일 기준 연차     (GET /anniversary?yearMonth=2026-04)
   └─ 공통   → [탭2] 퇴직자 연차 정산     (GET /resigned?year=2026)
```

### 회계년도 vs 입사일 기준 비교

| 구분 | 회계년도 (FISCAL) | 입사일 (HIRE) |
|------|-------------------|---------------|
| AllowanceType | `FISCAL_YEAR` | `ANNIVERSARY` |
| 조회 파라미터 | `year` (연도) | `yearMonth` (연월) |
| 대상자 | 전 사원 일괄 | 해당 월 입사 기념일 도래 사원 |
| 연차기간 | 1/1 ~ 12/31 | 입사일 ~ 입사일 다음해 |
| VacationRemainder 조회 | `vacRemYear = year` | `vacRemYear = year - 1` |
| 급여반영 대상월 | 해당연도 12월 | 입사 기념일 월 |
| 산정 시기 | 연말 1회 | 매월 (해당 월 대상자) |
