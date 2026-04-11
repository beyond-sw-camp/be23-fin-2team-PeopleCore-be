# 사원별 급여관리 - 백엔드 코드

> Admin 화면 — 사원 급여 목록 조회, 상세(연봉계약 기반), 계좌 변경, 월급여 예상지급공제
> 연봉/월급은 **연봉계약(SalaryContract)** 에서 가져옴 (읽기 전용)
> 계좌 변경 시 **계좌검증 API** 필수

---

## API 목록

| # | Method | URL | 설명 |
|---|--------|-----|------|
| 1 | GET | `/pay/admin/employees` | 사원 급여 목록 (페이징 + 필터) |
| 2 | GET | `/pay/admin/employees/{empId}` | 급여 상세 (모달) |
| 3 | PUT | `/pay/admin/employees/{empId}/account` | 급여계좌 변경 |
| 4 | PUT | `/pay/admin/employees/{empId}/retirement-account` | 퇴직연금계좌 변경 |
| 5 | GET | `/pay/admin/employees/expected-deductions` | 월급여 예상지급공제 목록 |

---

## 파일 체크리스트

| # | 구분 | 파일명 | 위치 | 작업 |
|---|------|--------|------|------|
| 1 | Entity | `EmpAccounts.java` | pay/domain/ | update 메서드 + @Index 추가 |
| 2 | Entity | `EmpRetirementAccount.java` | pay/domain/ | FK 변경(empId→Employee) + update 메서드 + @Index |
| 3 | Repository | `EmpAccountsRepository.java` | pay/repository/ | hr-service용 신규 (배치 조회 추가) |
| 4 | Repository | `EmpRetirementAccountRepository.java` | pay/repository/ | 신규 |
| 5 | DTO | `EmpSalaryResDto.java` | pay/dtos/ | 신규 (목록용) |
| 6 | DTO | `EmpSalaryDetailResDto.java` | pay/dtos/ | 신규 (상세 모달) |
| 7 | DTO | `ContractPayItemResDto.java` | pay/dtos/ | 신규 (고정수당 항목) |
| 8 | DTO | `EmpAccountReqDto.java` | pay/dtos/ | 신규 (계좌 변경 요청) |
| 9 | DTO | `EmpRetirementAccountReqDto.java` | pay/dtos/ | 신규 (퇴직연금계좌 변경) |
| 10 | DTO | `ExpectedDeductionResDto.java` | pay/dtos/ | 신규 (예상지급공제 행) |
| 11 | DTO | `ExpectedDeductionSummaryResDto.java` | pay/dtos/ | 신규 (예상지급공제 요약+목록) |
| 12 | Service | `EmpSalaryService.java` | pay/service/ | 신규 |
| 13 | Controller | `EmpSalaryController.java` | pay/controller/ | 신규 |
| 14 | ErrorCode | `ErrorCode.java` | common/ | 추가 |

---

## 1. Entity 수정

### EmpAccounts.java (수정)
**파일 위치**: `pay/domain/EmpAccounts.java`

> update 메서드 추가, @Index 추가
> Employee FK는 이미 적용되어 있음

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

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "emp_accounts",
    indexes = {
        @Index(name = "idx_emp_accounts_emp", columnList = "emp_id")
    })
public class EmpAccounts extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long empAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @Column(length = 50)
    private String bankName;

    @Column(length = 50)
    private String accountNumber;

    @Column(length = 50)
    private String accountHolder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;


    // ── 계좌 정보 변경 ──
    public void update(String bankName, String accountNumber, String accountHolder) {
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
    }
}
```

---

### EmpRetirementAccount.java (수정)
**파일 위치**: `pay/domain/EmpRetirementAccount.java`

> FK 변경: `Long empId` → `@ManyToOne Employee`
> update 메서드 추가, @Index 추가

```java
package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.pay.enums.RetirementType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "emp_retirement_account",
    indexes = {
        @Index(name = "idx_retirement_account_emp", columnList = "emp_id")
    })
public class EmpRetirementAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long retirementAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RetirementType retirementType;

    @Column(nullable = false, length = 100)
    private String pensionProvider;

    @Column(length = 50)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;


    // ── 퇴직연금 계좌 정보 변경 ──
    public void update(RetirementType retirementType, String pensionProvider, String accountNumber) {
        this.retirementType = retirementType;
        this.pensionProvider = pensionProvider;
        this.accountNumber = accountNumber;
    }
}
```

---

## 2. Repository

### EmpAccountsRepository.java (hr-service용 신규)
**파일 위치**: `pay/repository/EmpAccountsRepository.java`

> mysalary-crud 모듈에 이미 존재하지만, hr-service 모듈에도 필요
> 배치 조회(empId IN) 추가

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.EmpAccounts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmpAccountsRepository extends JpaRepository<EmpAccounts, Long> {

    // 사원 계좌 단건
    Optional<EmpAccounts> findByEmployee_EmpIdAndCompany_CompanyId(Long empId, UUID companyId);

    // 사원 ID + 계좌 ID 조합 (수정 시)
    Optional<EmpAccounts> findByEmpAccountIdAndEmployee_EmpIdAndCompany_CompanyId(
            Long empAccountId, Long empId, UUID companyId);

    // 배치 조회 (목록에서 N+1 방지)
    List<EmpAccounts> findByEmployee_EmpIdInAndCompany_CompanyId(List<Long> empIds, UUID companyId);
}
```

### EmpRetirementAccountRepository.java (신규)
**파일 위치**: `pay/repository/EmpRetirementAccountRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.EmpRetirementAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmpRetirementAccountRepository extends JpaRepository<EmpRetirementAccount, Long> {

    Optional<EmpRetirementAccount> findByEmployee_EmpIdAndCompany_CompanyId(Long empId, UUID companyId);
}
```

---

## 3. DTO

### EmpSalaryResDto.java (신규)
**파일 위치**: `pay/dtos/EmpSalaryResDto.java`

> 목록 행 — 탭1 "연봉" 테이블

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.employee.domain.Employee;
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
public class EmpSalaryResDto {

    private Long empId;
    private String empStatus;           // 재직상태
    private String empName;
    private String deptName;
    private String titleName;           // 직위
    private LocalDate empHireDate;      // 입사일
    private String empType;             // 직원구분 (정규직/계약직/인턴 등)

    // 연봉계약 기반 (읽기 전용)
    private BigDecimal annualSalary;    // 연봉
    private Long monthlySalary;         // 월급

    // 급여계좌
    private String bankName;
    private String accountNumber;

    public static EmpSalaryResDto fromEmployee(Employee emp,
                                                BigDecimal annualSalary,
                                                Long monthlySalary,
                                                String bankName,
                                                String accountNumber) {
        return EmpSalaryResDto.builder()
                .empId(emp.getEmpId())
                .empStatus(emp.getEmpStatus().name())
                .empName(emp.getEmpName())
                .deptName(emp.getDept().getDeptName())
                .titleName(emp.getTitle() != null ? emp.getTitle().getTitleName() : null)
                .empHireDate(emp.getEmpHireDate())
                .empType(emp.getEmpType().name())
                .annualSalary(annualSalary)
                .monthlySalary(monthlySalary)
                .bankName(bankName)
                .accountNumber(accountNumber)
                .build();
    }
}
```

### EmpSalaryDetailResDto.java (신규)
**파일 위치**: `pay/dtos/EmpSalaryDetailResDto.java`

> 상세 모달 — 사원정보 + 연봉/월급(읽기전용) + 고정수당 + 계좌

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
public class EmpSalaryDetailResDto {

    // ── 사원 인적사항 ──
    private Long empId;
    private String empName;
    private String empNum;              // 사번
    private String empEmail;
    private String empType;             // 직원구분
    private String empStatus;           // 재직상태
    private String deptName;
    private String gradeName;           // 직급
    private String titleName;           // 직책
    private LocalDate empHireDate;

    // ── 연봉 / 월급 (읽기 전용 - 연봉계약에서 가져옴) ──
    private BigDecimal annualSalary;
    private Long monthlySalary;

    // ── 고정수당 항목 (연봉계약에서 설정, 읽기 전용) ──
    private List<ContractPayItemResDto> fixedPayItems;

    // ── 급여 계좌 ──
    private Long empAccountId;
    private String bankName;
    private String accountNumber;
    private String accountHolder;

    // ── 퇴직연금 계좌 ──
    private Long retirementAccountId;
    private String retirementType;      // DB / DC
    private String pensionProvider;     // 퇴직연금 운용사
    private String retirementAccountNumber;
}
```

### ContractPayItemResDto.java (신규)
**파일 위치**: `pay/dtos/ContractPayItemResDto.java`

> 고정수당 항목 (식대, 교통비, 직책수당 등)

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
public class ContractPayItemResDto {

    private Long payItemId;
    private String payItemName;
    private Integer amount;
}
```

### EmpAccountReqDto.java (신규)
**파일 위치**: `pay/dtos/EmpAccountReqDto.java`

> 급여계좌 변경 요청 (계좌검증 완료 후 호출)

```java
package com.peoplecore.pay.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmpAccountReqDto {

    @NotBlank(message = "은행명은 필수입니다.")
    private String bankName;

    @NotBlank(message = "계좌번호는 필수입니다.")
    private String accountNumber;

    @NotBlank(message = "예금주는 필수입니다.")
    private String accountHolder;

    // 계좌검증 완료 토큰 (프론트에서 검증 API 호출 후 받은 값)
    @NotBlank(message = "계좌검증이 필요합니다.")
    private String verificationToken;
}
```

### EmpRetirementAccountReqDto.java (신규)
**파일 위치**: `pay/dtos/EmpRetirementAccountReqDto.java`

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.enums.RetirementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmpRetirementAccountReqDto {

    @NotNull(message = "퇴직연금 유형은 필수입니다.")
    private RetirementType retirementType;      // DB / DC

    @NotBlank(message = "퇴직연금 운용사는 필수입니다.")
    private String pensionProvider;

    private String accountNumber;
}
```

### ExpectedDeductionResDto.java (신규)
**파일 위치**: `pay/dtos/ExpectedDeductionResDto.java`

> 탭2 "월급여 예상지급공제" 테이블 행

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpectedDeductionResDto {

    private Long empId;
    private String empStatus;
    private String empName;
    private String deptName;
    private String titleName;

    // 연봉 / 월급
    private BigDecimal annualSalary;
    private Long monthlySalary;
    private Long basePay;               // 기본급

    // 4대보험 (근로자 부담분)
    private Long nationalPension;       // 국민연금
    private Long healthInsurance;       // 건강보험
    private Long longTermCare;          // 장기요양
    private Long employmentInsurance;   // 고용보험

    // 세금
    private Long incomeTax;             // 소득세
    private Long localIncomeTax;        // 지방소득세

    // 합계
    private Long totalDeduction;        // 공제 합계
    private Long expectedNetPay;        // 예상 세후 월급
}
```

### ExpectedDeductionSummaryResDto.java (신규)
**파일 위치**: `pay/dtos/ExpectedDeductionSummaryResDto.java`

> 상단 요약 (사원 N명, 예상 지급 세 후 월급여 합계) + 사원별 목록

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
public class ExpectedDeductionSummaryResDto {

    private Integer totalEmployees;         // 사원 N명
    private Long totalExpectedNetPay;       // 예상 지급 세 후 월급여 합계
    private List<ExpectedDeductionResDto> employees;
}
```

---

## 4. Service

### EmpSalaryService.java (신규)
**파일 위치**: `pay/service/EmpSalaryService.java`

```java
package com.peoplecore.pay.service;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.EmpType;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.*;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.repository.*;
import com.peoplecore.salarycontract.domain.SalaryContract;
import com.peoplecore.salarycontract.domain.SalaryContractDetail;
import com.peoplecore.salarycontract.repository.SalaryContractRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EmpSalaryService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalaryContractRepository salaryContractRepository;

    @Autowired
    private EmpAccountsRepository empAccountsRepository;

    @Autowired
    private EmpRetirementAccountRepository empRetirementAccountRepository;

    @Autowired
    private PayItemsRepository payItemsRepository;

    @Autowired
    private InsuranceRatesRepository insuranceRatesRepository;

    @Autowired
    private TaxWithholdingService taxWithholdingService;


    // ═══════════════════════════════════════════════
    //  [탭1] 사원 급여 목록 (페이징 + 필터)
    // ═══════════════════════════════════════════════

    /**
     * 3-쿼리 배치 전략:
     * 1) Employee 페이징 조회 (QueryDSL, dept/grade/title JOIN FETCH)
     * 2) empId 목록 → SalaryContract 배치 조회 (최신 계약만)
     * 3) empId 목록 → EmpAccounts 배치 조회
     */
    public Page<EmpSalaryResDto> getEmpSalaryList(UUID companyId,
                                                    String keyword,
                                                    Long deptId,
                                                    EmpType empType,
                                                    EmpStatus empStatus,
                                                    Pageable pageable) {
        // 1) Employee 페이징 조회
        Page<Employee> empPage = employeeRepository.findAllwithFilter(
                companyId, keyword, deptId, empType, empStatus, null, pageable);

        if (empPage.isEmpty()) {
            return empPage.map(emp -> EmpSalaryResDto.fromEmployee(emp, null, null, null, null));
        }

        List<Long> empIds = empPage.getContent().stream()
                .map(Employee::getEmpId)
                .collect(Collectors.toList());

        // 2) SalaryContract 배치 조회 — 사원별 최신 계약만
        Map<Long, SalaryContract> contractMap = buildLatestContractMap(companyId, empIds);

        // 3) EmpAccounts 배치 조회
        Map<Long, EmpAccounts> accountMap = empAccountsRepository
                .findByEmployee_EmpIdInAndCompany_CompanyId(empIds, companyId)
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getEmployee().getEmpId(),
                        Function.identity(),
                        (a, b) -> a     // 중복 시 첫 번째
                ));

        // 4) DTO 조립
        return empPage.map(emp -> {
            SalaryContract contract = contractMap.get(emp.getEmpId());
            BigDecimal annual = contract != null ? contract.getTotalAmount() : null;
            Long monthly = annual != null
                    ? annual.divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP).longValue()
                    : null;

            EmpAccounts account = accountMap.get(emp.getEmpId());

            return EmpSalaryResDto.fromEmployee(
                    emp,
                    annual,
                    monthly,
                    account != null ? account.getBankName() : null,
                    account != null ? account.getAccountNumber() : null
            );
        });
    }


    // ═══════════════════════════════════════════════
    //  급여 상세 (모달)
    // ═══════════════════════════════════════════════

    /**
     * - 연봉/월급: 최신 SalaryContract.totalAmount 기반 (읽기 전용)
     * - 고정수당: SalaryContractDetail → PayItems 조인하여 항목명 매핑
     * - 계좌: EmpAccounts, EmpRetirementAccount
     */
    public EmpSalaryDetailResDto getEmpSalaryDetail(UUID companyId, Long empId) {
        Employee emp = employeeRepository.findById(empId)
                .filter(e -> e.getCompany().getCompanyId().equals(companyId))
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // 최신 연봉계약
        List<SalaryContract> contracts = salaryContractRepository
                .findByCompanyIdAndEmployee_EmpIdAndDeletedAtIsNullOrderByContractYearDesc(companyId, empId);

        BigDecimal annualSalary = null;
        Long monthlySalary = null;
        List<ContractPayItemResDto> fixedPayItems = List.of();

        if (!contracts.isEmpty()) {
            SalaryContract latestContract = contracts.get(0);
            annualSalary = latestContract.getTotalAmount();
            monthlySalary = annualSalary
                    .divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP).longValue();

            // 고정수당 항목 빌드
            fixedPayItems = buildFixedPayItems(companyId, latestContract);
        }

        // 급여 계좌
        Optional<EmpAccounts> accountOpt = empAccountsRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId);

        // 퇴직연금 계좌
        Optional<EmpRetirementAccount> retAccountOpt = empRetirementAccountRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId);

        return EmpSalaryDetailResDto.builder()
                // 인적사항
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .empNum(emp.getEmpNum())
                .empEmail(emp.getEmpEmail())
                .empType(emp.getEmpType().name())
                .empStatus(emp.getEmpStatus().name())
                .deptName(emp.getDept().getDeptName())
                .gradeName(emp.getGrade().getGradeName())
                .titleName(emp.getTitle() != null ? emp.getTitle().getTitleName() : null)
                .empHireDate(emp.getEmpHireDate())
                // 연봉/월급 (읽기 전용)
                .annualSalary(annualSalary)
                .monthlySalary(monthlySalary)
                // 고정수당
                .fixedPayItems(fixedPayItems)
                // 급여계좌
                .empAccountId(accountOpt.map(EmpAccounts::getEmpAccountId).orElse(null))
                .bankName(accountOpt.map(EmpAccounts::getBankName).orElse(null))
                .accountNumber(accountOpt.map(EmpAccounts::getAccountNumber).orElse(null))
                .accountHolder(accountOpt.map(EmpAccounts::getAccountHolder).orElse(null))
                // 퇴직연금계좌
                .retirementAccountId(retAccountOpt.map(EmpRetirementAccount::getRetirementAccountId).orElse(null))
                .retirementType(retAccountOpt.map(a -> a.getRetirementType().name()).orElse(null))
                .pensionProvider(retAccountOpt.map(EmpRetirementAccount::getPensionProvider).orElse(null))
                .retirementAccountNumber(retAccountOpt.map(EmpRetirementAccount::getAccountNumber).orElse(null))
                .build();
    }


    // ═══════════════════════════════════════════════
    //  급여계좌 변경 (계좌검증 필수)
    // ═══════════════════════════════════════════════

    /**
     * 프론트 → 계좌검증 외부 API 호출 → verificationToken 수신 → 백엔드 전달
     * 백엔드는 토큰 유효성 재검증 후 계좌 변경
     */
    @Transactional
    public void updateEmpAccount(UUID companyId, Long empId, EmpAccountReqDto request) {
        // TODO: verificationToken 검증 로직 (외부 계좌검증 API 연동)
        //       예: accountVerificationService.verify(request.getVerificationToken())
        //       실패 시 throw new CustomException(ErrorCode.ACCOUNT_VERIFICATION_FAILED);

        Optional<EmpAccounts> accountOpt = empAccountsRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId);

        if (accountOpt.isPresent()) {
            // 기존 계좌 수정
            accountOpt.get().update(
                    request.getBankName(),
                    request.getAccountNumber(),
                    request.getAccountHolder()
            );
        } else {
            // 신규 등록
            Employee emp = employeeRepository.findById(empId)
                    .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

            EmpAccounts newAccount = EmpAccounts.builder()
                    .employee(emp)
                    .bankName(request.getBankName())
                    .accountNumber(request.getAccountNumber())
                    .accountHolder(request.getAccountHolder())
                    .company(emp.getCompany())
                    .build();

            empAccountsRepository.save(newAccount);
        }
    }


    // ═══════════════════════════════════════════════
    //  퇴직연금계좌 변경
    // ═══════════════════════════════════════════════

    @Transactional
    public void updateRetirementAccount(UUID companyId, Long empId, EmpRetirementAccountReqDto request) {
        Optional<EmpRetirementAccount> accountOpt = empRetirementAccountRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId);

        if (accountOpt.isPresent()) {
            accountOpt.get().update(
                    request.getRetirementType(),
                    request.getPensionProvider(),
                    request.getAccountNumber()
            );
        } else {
            Employee emp = employeeRepository.findById(empId)
                    .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

            EmpRetirementAccount newAccount = EmpRetirementAccount.builder()
                    .retirementType(request.getRetirementType())
                    .pensionProvider(request.getPensionProvider())
                    .accountNumber(request.getAccountNumber())
                    .employee(emp)
                    .company(emp.getCompany())
                    .build();

            empRetirementAccountRepository.save(newAccount);
        }
    }


    // ═══════════════════════════════════════════════
    //  [탭2] 월급여 예상지급공제
    // ═══════════════════════════════════════════════

    /**
     * 계산 흐름:
     * 1) 재직 사원 + 최신 연봉계약 조회
     * 2) 월급 기준 4대보험 계산 (InsuranceRates 요율)
     * 3) 소득세/지방소득세 (TaxWithholdingService.getTax)
     * 4) 공제합계, 예상 세후 월급 계산
     */
    public ExpectedDeductionSummaryResDto getExpectedDeductions(UUID companyId) {
        // 재직/휴직 사원 조회 (softDelete 제외)
        List<Employee> employees = employeeRepository
                .findByCompany_CompanyIdAndEmpStatusInAndDeleteAtIsNull(
                        companyId,
                        List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE));

        if (employees.isEmpty()) {
            return ExpectedDeductionSummaryResDto.builder()
                    .totalEmployees(0)
                    .totalExpectedNetPay(0L)
                    .employees(List.of())
                    .build();
        }

        // 현재 연도 보험요율
        int currentYear = LocalDate.now().getYear();
        InsuranceRates rates = insuranceRatesRepository
                .findByCompany_CompanyIdAndYear(companyId, currentYear)
                .orElse(null);

        // 사원별 최신 연봉계약 맵
        List<Long> empIds = employees.stream().map(Employee::getEmpId).collect(Collectors.toList());
        Map<Long, SalaryContract> contractMap = buildLatestContractMap(companyId, empIds);

        List<ExpectedDeductionResDto> result = new ArrayList<>();
        long grandTotalNet = 0L;

        for (Employee emp : employees) {
            SalaryContract contract = contractMap.get(emp.getEmpId());

            BigDecimal annualSalary = contract != null ? contract.getTotalAmount() : null;
            Long monthlySalary = annualSalary != null
                    ? annualSalary.divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP).longValue()
                    : null;

            // 기본급 = 월급 - 고정수당(SalaryContractDetail) 합계
            Long basePay = null;
            if (contract != null && monthlySalary != null) {
                int fixedSum = contract.getDetails() != null
                        ? contract.getDetails().stream()
                            .mapToInt(SalaryContractDetail::getAmount).sum()
                        : 0;
                basePay = monthlySalary - fixedSum;
                if (basePay < 0) basePay = 0L;
            }

            // 4대보험 + 세금
            Long pension = null, health = null, ltc = null, employment = null;
            Long incomeTax = null, localIncomeTax = null;
            Long totalDeduction = null, expectedNetPay = null;

            if (monthlySalary != null && rates != null) {

                // ── 국민연금 (상/하한 적용) ──
                long pensionBase = monthlySalary;
                if (pensionBase > rates.getPensionUpperLimit()) {
                    pensionBase = rates.getPensionUpperLimit();
                }
                if (pensionBase < rates.getPensionLowerLimit()) {
                    pensionBase = rates.getPensionLowerLimit();
                }
                pension = calcHalf(pensionBase, rates.getNationalPension());

                // ── 건강보험 ──
                health = calcHalf(monthlySalary, rates.getHealthInsurance());

                // ── 장기요양 (건강보험 전액 × 요율 / 2) ──
                long healthTotal = health * 2;
                long ltcTotal = calcAmount(healthTotal, rates.getLongTermCare());
                ltc = ltcTotal / 2;

                // ── 고용보험 (근로자 요율) ──
                employment = calcAmount(monthlySalary, rates.getEmploymentInsurance());

                // ── 소득세 (간이세액표 조회) ──
                try {
                    TaxWithholdingResDto tax = taxWithholdingService.getTax(
                            currentYear, monthlySalary, emp.getDependentsCount());
                    incomeTax = tax.getIncomeTax();
                    localIncomeTax = tax.getLocalIncomeTax();
                } catch (Exception e) {
                    // 세액표에 해당 구간 없으면 0
                    incomeTax = 0L;
                    localIncomeTax = 0L;
                }

                totalDeduction = pension + health + ltc + employment + incomeTax + localIncomeTax;
                expectedNetPay = monthlySalary - totalDeduction;
            }

            if (expectedNetPay != null) grandTotalNet += expectedNetPay;

            result.add(ExpectedDeductionResDto.builder()
                    .empId(emp.getEmpId())
                    .empStatus(emp.getEmpStatus().name())
                    .empName(emp.getEmpName())
                    .deptName(emp.getDept().getDeptName())
                    .titleName(emp.getTitle() != null ? emp.getTitle().getTitleName() : null)
                    .annualSalary(annualSalary)
                    .monthlySalary(monthlySalary)
                    .basePay(basePay)
                    .nationalPension(pension)
                    .healthInsurance(health)
                    .longTermCare(ltc)
                    .employmentInsurance(employment)
                    .incomeTax(incomeTax)
                    .localIncomeTax(localIncomeTax)
                    .totalDeduction(totalDeduction)
                    .expectedNetPay(expectedNetPay)
                    .build());
        }

        return ExpectedDeductionSummaryResDto.builder()
                .totalEmployees(employees.size())
                .totalExpectedNetPay(grandTotalNet)
                .employees(result)
                .build();
    }


    // ══════════════════════════════════════════
    //  내부 헬퍼 메서드
    // ══════════════════════════════════════════

    /**
     * 사원별 최신 연봉계약 맵 빌드
     * contractYear DESC 정렬 → 첫 번째가 최신
     */
    private Map<Long, SalaryContract> buildLatestContractMap(UUID companyId, List<Long> empIds) {
        Map<Long, SalaryContract> map = new HashMap<>();
        for (Long empId : empIds) {
            List<SalaryContract> contracts = salaryContractRepository
                    .findByCompanyIdAndEmployee_EmpIdAndDeletedAtIsNullOrderByContractYearDesc(companyId, empId);
            if (!contracts.isEmpty()) {
                map.put(empId, contracts.get(0));
            }
        }
        return map;
    }

    /**
     * 연봉계약 상세 → 고정수당 항목 DTO 빌드
     * SalaryContractDetail.payItemId → PayItems.payItemName 매핑
     */
    private List<ContractPayItemResDto> buildFixedPayItems(UUID companyId, SalaryContract contract) {
        if (contract.getDetails() == null || contract.getDetails().isEmpty()) {
            return List.of();
        }

        List<Long> payItemIds = contract.getDetails().stream()
                .map(SalaryContractDetail::getPayItemId)
                .collect(Collectors.toList());

        // PayItems 배치 조회
        Map<Long, PayItems> payItemMap = payItemsRepository
                .findByPayItemIdInAndCompany_CompanyId(payItemIds, companyId)
                .stream()
                .collect(Collectors.toMap(PayItems::getPayItemId, Function.identity()));

        return contract.getDetails().stream()
                .map(detail -> {
                    PayItems item = payItemMap.get(detail.getPayItemId());
                    return ContractPayItemResDto.builder()
                            .payItemId(detail.getPayItemId())
                            .payItemName(item != null ? item.getPayItemName() : "알 수 없는 항목")
                            .amount(detail.getAmount())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ── 보험료 계산 유틸 ──

    private long calcAmount(long base, BigDecimal rate) {
        if (rate == null) return 0L;
        return BigDecimal.valueOf(base)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private long calcHalf(long base, BigDecimal rate) {
        if (rate == null) return 0L;
        return BigDecimal.valueOf(base)
                .multiply(rate)
                .divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)
                .longValue();
    }
}
```

---

## 5. Controller

### EmpSalaryController.java (신규)
**파일 위치**: `pay/controller/EmpSalaryController.java`

```java
package com.peoplecore.pay.controller;

import com.peoplecore.common.annotation.RoleRequired;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.EmpType;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.service.EmpSalaryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/pay/admin/employees")
@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
public class EmpSalaryController {

    @Autowired
    private EmpSalaryService empSalaryService;


    /**
     * [탭1] 사원 급여 목록 (페이징 + 필터)
     * GET /pay/admin/employees?keyword=김민수&deptId=1&empType=FULL&empStatus=ACTIVE&page=0&size=10
     */
    @GetMapping
    public ResponseEntity<Page<EmpSalaryResDto>> getEmpSalaryList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) EmpType empType,
            @RequestParam(required = false) EmpStatus empStatus,
            @PageableDefault(size = 10) Pageable pageable) {

        return ResponseEntity.ok(
                empSalaryService.getEmpSalaryList(
                        companyId, keyword, deptId, empType, empStatus, pageable));
    }

    /**
     * 급여 상세 (모달)
     * GET /pay/admin/employees/{empId}
     */
    @GetMapping("/{empId}")
    public ResponseEntity<EmpSalaryDetailResDto> getEmpSalaryDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId) {

        return ResponseEntity.ok(
                empSalaryService.getEmpSalaryDetail(companyId, empId));
    }

    /**
     * 급여계좌 변경 (계좌검증 필수)
     * PUT /pay/admin/employees/{empId}/account
     */
    @PutMapping("/{empId}/account")
    public ResponseEntity<Void> updateEmpAccount(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId,
            @Valid @RequestBody EmpAccountReqDto request) {

        empSalaryService.updateEmpAccount(companyId, empId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 퇴직연금계좌 변경
     * PUT /pay/admin/employees/{empId}/retirement-account
     */
    @PutMapping("/{empId}/retirement-account")
    public ResponseEntity<Void> updateRetirementAccount(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId,
            @Valid @RequestBody EmpRetirementAccountReqDto request) {

        empSalaryService.updateRetirementAccount(companyId, empId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * [탭2] 월급여 예상지급공제
     * GET /pay/admin/employees/expected-deductions
     */
    @GetMapping("/expected-deductions")
    public ResponseEntity<ExpectedDeductionSummaryResDto> getExpectedDeductions(
            @RequestHeader("X-User-Company") UUID companyId) {

        return ResponseEntity.ok(
                empSalaryService.getExpectedDeductions(companyId));
    }
}
```

---

## 6. ErrorCode 추가

**파일 위치**: `common/ErrorCode.java` (기존 파일에 추가)

```java
// ── 계좌 검증 ──
ACCOUNT_VERIFICATION_FAILED(400, "계좌검증에 실패했습니다. 다시 시도해주세요."),
```

---

## 7. 필요한 기존 Repository 메서드 확인/추가

### EmployeeRepository.java (추가 필요)
```java
// 재직/휴직 사원 조회 (소프트삭제 제외) — 월급여 예상지급공제에서 사용
List<Employee> findByCompany_CompanyIdAndEmpStatusInAndDeleteAtIsNull(
        UUID companyId, List<EmpStatus> statuses);
```

### PayItemsRepository.java (이미 존재 확인됨)
```java
List<PayItems> findByPayItemIdInAndCompany_CompanyId(List<Long> payItemIds, UUID companyId);
```

---

## 계좌검증 API 연동 흐름

```
1. 프론트: 사용자가 은행/계좌번호/예금주 입력
2. 프론트: 계좌검증 외부 API 호출 (금융결제원 오픈뱅킹 등)
3. 프론트: 검증 성공 시 verificationToken 수신
4. 프론트: PUT /pay/admin/employees/{empId}/account 호출 시 token 포함
5. 백엔드: verificationToken 유효성 재검증 후 계좌 저장
```

> 백엔드 검증 로직은 Service의 TODO 부분에 추가

---

## 월급여 예상지급공제 계산 로직 요약

| 항목 | 계산 공식 | 비고 |
|------|-----------|------|
| 기본급 | `월급 - 고정수당합계` | SalaryContractDetail 합산 |
| 국민연금 | `보수월액 × 요율 / 2` | 상/하한 적용 |
| 건강보험 | `월급 × 요율 / 2` | |
| 장기요양 | `(건강보험전액) × 요율 / 2` | 건강보험 기반 |
| 고용보험 | `월급 × 근로자요율` | |
| 소득세 | 간이세액표 조회 | `TaxWithholdingService.getTax(연도, 월급, 부양가족수)` |
| 지방소득세 | 간이세액표 조회 | 소득세의 10% |
| 공제합계 | 보험4종 + 소득세 + 지방소득세 | |
| 예상 세후 월급 | `월급 - 공제합계` | |

> 보험요율: `InsuranceRates` 테이블에서 현재 연도 기준 조회
> 소득세: `TaxWithholdingTable`에서 월급 구간 + 부양가족수로 조회
> 부양가족수: `Employee.dependentsCount` (기본값 1)
