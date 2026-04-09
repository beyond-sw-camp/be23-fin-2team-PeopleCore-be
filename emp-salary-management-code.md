# 사원별 급여관리 - 백엔드 코드

> Admin 화면 — 사원별 연봉/월급/계좌 조회, 필터 검색, 엑셀 업로드/다운로드

---

## API 목록

| # | Method | URL | 설명 |
|---|--------|-----|------|
| 1 | GET | `/pay/admin/employees` | 사원별 급여 목록 조회 (연봉 탭) |
| 2 | GET | `/pay/admin/employees/{empId}` | 사원 급여 상세 조회 (모달) |
| 3 | PUT | `/pay/admin/employees/{empId}/account` | 급여 계좌 수정 |
| 4 | PUT | `/pay/admin/employees/{empId}/retirement-account` | 퇴직연금 계좌 수정 |

> 연봉/월급/고정수당 항목은 **읽기 전용** — 사원관리 > 연봉계약 탭에서 저장한 값을 그대로 표시

> 엑셀 업로드/다운로드는 별도 파트에서 다룸

---

## 파일 체크리스트

| # | 구분 | 파일명 | 위치 | 작업 |
|---|------|--------|------|------|
| 1 | Repository | `SalaryContractRepository.java` | salarycontract/repository/ | 신규 |
| 2 | Repository | `SalaryContractDetailRepository.java` | salarycontract/repository/ | 신규 |
| 3 | Repository | `EmpAccountsRepository.java` | pay/repository/ | 신규 |
| 4 | Repository | `EmpRetirementAccountRepository.java` | pay/repository/ | 신규 |
| 5 | DTO | `EmpSalaryResDto.java` | pay/dtos/ | 신규 |
| 6 | DTO | `EmpSalaryDetailResDto.java` | pay/dtos/ | 신규 |
| 7 | DTO | `ContractPayItemResDto.java` | pay/dtos/ | 신규 |
| 8 | DTO | `EmpAccountReqDto.java` | pay/dtos/ | 신규 |
| 9 | DTO | `EmpRetirementAccountReqDto.java` | pay/dtos/ | 신규 |
| 10 | Entity | `EmpAccounts.java` | pay/domain/ | FK 변경 + update |
| 11 | Entity | `EmpRetirementAccount.java` | pay/domain/ | FK 변경 + update |
| 12 | Service | `EmpSalaryService.java` | pay/service/ | 신규 |
| 13 | Controller | `EmpSalaryController.java` | pay/controller/ | 신규 |
| 14 | ErrorCode | `ErrorCode.java` | common/.../exception/ | 추가 |

---

## 1. Repository

### SalaryContractRepository.java (신규)
**파일 위치**: `salarycontract/repository/SalaryContractRepository.java`

> 연봉계약서 조회용. SalaryContract.employee가 Employee FK로 변경됨.

```java
package com.peoplecore.salarycontract.repository;

import com.peoplecore.salarycontract.domain.ContractStatus;
import com.peoplecore.salarycontract.domain.SalaryContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SalaryContractRepository extends JpaRepository<SalaryContract, Long> {

    // 특정 사원의 최신 서명완료 계약 조회
    Optional<SalaryContract> findTopByEmployee_EmpIdAndStatusOrderByContractYearDesc(
            Long empId, ContractStatus status);

    // 사원 ID 목록의 최신 서명완료 계약 배치 조회
    @Query("""
        SELECT sc FROM SalaryContract sc
        WHERE sc.employee.empId IN :empIds
          AND sc.status = :status
          AND sc.contractYear = (
              SELECT MAX(sc2.contractYear)
              FROM SalaryContract sc2
              WHERE sc2.employee.empId = sc.employee.empId
                AND sc2.status = :status
          )
    """)
    List<SalaryContract> findLatestSignedByEmpIds(
            @Param("empIds") List<Long> empIds,
            @Param("status") ContractStatus status);
}
```

---

### SalaryContractDetailRepository.java (신규)
**파일 위치**: `salarycontract/repository/SalaryContractDetailRepository.java`

> 연봉계약 상세 (고정수당 항목별 금액) 조회용

```java
package com.peoplecore.salarycontract.repository;

import com.peoplecore.salarycontract.domain.SalaryContractDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryContractDetailRepository extends JpaRepository<SalaryContractDetail, Long> {

    // 특정 계약의 상세 항목 조회
    List<SalaryContractDetail> findByContractId(Long contractId);
}
```

---

### EmpAccountsRepository.java (신규)
**파일 위치**: `pay/repository/EmpAccountsRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.EmpAccounts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmpAccountsRepository extends JpaRepository<EmpAccounts, Long> {

    // 특정 사원 계좌 조회
    Optional<EmpAccounts> findByEmployee_EmpIdAndCompany_CompanyId(Long empId, UUID companyId);

    // 사원 ID 목록의 계좌 배치 조회
    List<EmpAccounts> findByEmployee_EmpIdInAndCompany_CompanyId(List<Long> empIds, UUID companyId);
}
```

---

### EmpRetirementAccountRepository.java (신규)
**파일 위치**: `pay/repository/EmpRetirementAccountRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.EmpRetirementAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmpRetirementAccountRepository extends JpaRepository<EmpRetirementAccount, Long> {

    // 특정 사원 퇴직연금 계좌 조회
    Optional<EmpRetirementAccount> findByEmployee_EmpIdAndCompany_CompanyId(Long empId, UUID companyId);
}
```

---

## 2. DTO

### EmpSalaryResDto.java (신규)
**파일 위치**: `pay/dtos/EmpSalaryResDto.java`

> 사원별 급여관리 목록 (연봉 탭) 응답 DTO

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpSalaryResDto {

    // 사원 정보
    private Long empId;
    private String empName;
    private String empStatus;       // ACTIVE → 재직, ON_LEAVE → 휴직, RESIGNED → 퇴직
    private String deptName;
    private String gradeName;       // 직위 (Grade)
    private LocalDate empHireDate;
    private String empType;         // FULL → 정규직, CONTRACT → 계약직, DISPATCHED → 파견직

    // 연봉 정보 (SalaryContract)
    private BigDecimal annualSalary;    // 연봉
    private BigDecimal monthlySalary;   // 월급 (연봉 / 12)

    // 계좌 정보 (EmpAccounts)
    private String bankName;
    private String accountNumber;

    // 연봉으로부터 월급 계산
    public void calculateMonthlySalary() {
        if (this.annualSalary != null) {
            this.monthlySalary = this.annualSalary
                    .divide(BigDecimal.valueOf(12), 0, RoundingMode.FLOOR);
        }
    }
}
```

---

### EmpSalaryDetailResDto.java (신규)
**파일 위치**: `pay/dtos/EmpSalaryDetailResDto.java`

> 사원명 클릭 시 모달 응답 DTO — 연봉/고정수당은 읽기전용, 계좌만 수정 가능

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpSalaryDetailResDto {

    // 사원 기본 정보 (읽기전용)
    private Long empId;
    private String empName;
    private String empNum;
    private String empEmail;
    private String empStatus;
    private String deptName;
    private String gradeName;
    private String titleName;
    private LocalDate empHireDate;
    private String empType;

    // 연봉 계약 정보 (읽기전용 — 연봉계약서에서 가져옴)
    private BigDecimal annualSalary;
    private BigDecimal monthlySalary;

    // 고정수당 항목 (읽기전용 — 연봉계약 상세에서 가져옴)
    private List<ContractPayItemResDto> fixedPayItems;

    // 급여 계좌 정보 (수정 가능)
    private Long empAccountId;
    private String bankName;
    private String accountNumber;

    // 퇴직연금 계좌 정보 (수정 가능)
    private Long retirementAccountId;
    private String retirementBankName;
    private String retirementAccountNumber;

    // 세금 관련 (Employee에서)
    private Integer dependentsCount;
    private Integer taxRateOption;      // 80, 100, 120
    private String retirementType;      // severance, DB, DC

    public void calculateMonthlySalary() {
        if (this.annualSalary != null) {
            this.monthlySalary = this.annualSalary
                    .divide(BigDecimal.valueOf(12), 0, RoundingMode.FLOOR);
        }
    }
}
```

---

### ContractPayItemResDto.java (신규)
**파일 위치**: `pay/dtos/ContractPayItemResDto.java`

> 연봉계약 상세의 고정수당 항목 (식대, 교통비, 직책수당 등)

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
    private String payItemName;     // 식대, 교통비, 직책수당 등
    private Integer amount;         // 항목별 금액
}
```

---

### EmpAccountReqDto.java (신규)
**파일 위치**: `pay/dtos/EmpAccountReqDto.java`

> 사원 계좌 수정 요청 DTO

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

    @NotBlank(message = "은행명을 입력해주세요.")
    private String bankName;

    @NotBlank(message = "계좌번호를 입력해주세요.")
    private String accountNumber;

    private String accountHolder;
}
```

---

### EmpRetirementAccountReqDto.java (신규)
**파일 위치**: `pay/dtos/EmpRetirementAccountReqDto.java`

> 퇴직연금 계좌 수정 요청 DTO

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmpRetirementAccountReqDto {

    private String pensionProvider;     // 퇴직연금 운용사 (은행)
    private String accountNumber;       // 퇴직연금 계좌번호
}
```

---

## 3. Service

### EmpSalaryService.java (신규)
**파일 위치**: `pay/service/EmpSalaryService.java`

> 핵심 전략: Employee 페이징 조회 → empId 목록으로 SalaryContract, EmpAccounts 배치 조회 → 조합
> N+1 없이 총 3번의 쿼리로 해결

```java
package com.peoplecore.pay.service;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.EmpAccounts;
import com.peoplecore.pay.domain.EmpRetirementAccount;
import com.peoplecore.pay.domain.PayItems;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.repository.EmpAccountsRepository;
import com.peoplecore.pay.repository.EmpRetirementAccountRepository;
import com.peoplecore.pay.repository.PayItemsRepository;
import com.peoplecore.salarycontract.domain.ContractStatus;
import com.peoplecore.salarycontract.domain.SalaryContract;
import com.peoplecore.salarycontract.domain.SalaryContractDetail;
import com.peoplecore.salarycontract.repository.SalaryContractDetailRepository;
import com.peoplecore.salarycontract.repository.SalaryContractRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EmpSalaryService {

    private final EmployeeRepository employeeRepository;
    private final SalaryContractRepository salaryContractRepository;
    private final SalaryContractDetailRepository salaryContractDetailRepository;
    private final EmpAccountsRepository empAccountsRepository;
    private final EmpRetirementAccountRepository empRetirementAccountRepository;
    private final PayItemsRepository payItemsRepository;

    @Autowired
    public EmpSalaryService(EmployeeRepository employeeRepository,
                            SalaryContractRepository salaryContractRepository,
                            SalaryContractDetailRepository salaryContractDetailRepository,
                            EmpAccountsRepository empAccountsRepository,
                            EmpRetirementAccountRepository empRetirementAccountRepository,
                            PayItemsRepository payItemsRepository) {
        this.employeeRepository = employeeRepository;
        this.salaryContractRepository = salaryContractRepository;
        this.salaryContractDetailRepository = salaryContractDetailRepository;
        this.empAccountsRepository = empAccountsRepository;
        this.empRetirementAccountRepository = empRetirementAccountRepository;
        this.payItemsRepository = payItemsRepository;
    }


    // ── 사원별 급여 목록 조회 (연봉 탭) ──
    public Page<EmpSalaryResDto> getEmpSalaryList(UUID companyId,
                                                   EmpStatus empStatus,
                                                   Long deptId,
                                                   String keyword,
                                                   Pageable pageable) {

        // 1) Employee 목록 조회 (기존 QueryDSL 활용)
        Page<Employee> empPage = employeeRepository.findAllwithFilter(
                keyword, deptId, null, empStatus, null, pageable);

        if (empPage.isEmpty()) {
            return Page.empty(pageable);
        }

        // 2) 해당 페이지의 empId 목록 추출
        List<Long> empIds = empPage.getContent().stream()
                .map(Employee::getEmpId)
                .toList();

        // 3) SalaryContract 배치 조회 (최신 서명완료 계약)
        Map<Long, SalaryContract> contractMap = salaryContractRepository
                .findLatestSignedByEmpIds(empIds, ContractStatus.SIGNED)
                .stream()
                .collect(Collectors.toMap(sc -> sc.getEmployee().getEmpId(), Function.identity()));

        // 4) EmpAccounts 배치 조회
        Map<Long, EmpAccounts> accountMap = empAccountsRepository
                .findByEmployee_EmpIdInAndCompany_CompanyId(empIds, companyId)
                .stream()
                .collect(Collectors.toMap(a -> a.getEmployee().getEmpId(), Function.identity()));

        // 5) 조합
        List<EmpSalaryResDto> dtoList = empPage.getContent().stream()
                .map(emp -> {
                    SalaryContract contract = contractMap.get(emp.getEmpId());
                    EmpAccounts account = accountMap.get(emp.getEmpId());

                    EmpSalaryResDto dto = EmpSalaryResDto.builder()
                            .empId(emp.getEmpId())
                            .empName(emp.getEmpName())
                            .empStatus(emp.getEmpStatus().name())
                            .deptName(emp.getDept().getDeptName())
                            .gradeName(emp.getGrade().getGradeName())
                            .empHireDate(emp.getEmpHireDate())
                            .empType(emp.getEmpType().name())
                            .annualSalary(contract != null ? contract.getTotalAmount() : null)
                            .bankName(account != null ? account.getBankName() : null)
                            .accountNumber(account != null ? account.getAccountNumber() : null)
                            .build();

                    dto.calculateMonthlySalary();
                    return dto;
                })
                .toList();

        return new PageImpl<>(dtoList, pageable, empPage.getTotalElements());
    }


    // ── 사원 급여 상세 조회 (모달) ──
    public EmpSalaryDetailResDto getEmpSalaryDetail(UUID companyId, Long empId) {

        Employee emp = employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // 최신 서명완료 연봉계약
        SalaryContract contract = salaryContractRepository
                .findTopByEmployee_EmpIdAndStatusOrderByContractYearDesc(empId, ContractStatus.SIGNED)
                .orElse(null);

        // 고정수당 항목 (연봉계약 상세에서)
        List<ContractPayItemResDto> fixedPayItems = buildFixedPayItems(contract, companyId);

        // 급여 계좌
        EmpAccounts account = empAccountsRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId)
                .orElse(null);

        // 퇴직연금 계좌
        EmpRetirementAccount retAccount = empRetirementAccountRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId)
                .orElse(null);

        EmpSalaryDetailResDto dto = EmpSalaryDetailResDto.builder()
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .empNum(emp.getEmpNum())
                .empEmail(emp.getEmpEmail())
                .empStatus(emp.getEmpStatus().name())
                .deptName(emp.getDept().getDeptName())
                .gradeName(emp.getGrade().getGradeName())
                .titleName(emp.getTitle() != null ? emp.getTitle().getTitleName() : null)
                .empHireDate(emp.getEmpHireDate())
                .empType(emp.getEmpType().name())
                // 연봉 (읽기전용)
                .annualSalary(contract != null ? contract.getTotalAmount() : null)
                // 고정수당 (읽기전용)
                .fixedPayItems(fixedPayItems)
                // 급여 계좌
                .empAccountId(account != null ? account.getEmpAccountId() : null)
                .bankName(account != null ? account.getBankName() : null)
                .accountNumber(account != null ? account.getAccountNumber() : null)
                // 퇴직연금 계좌
                .retirementAccountId(retAccount != null ? retAccount.getRetirementAccountId() : null)
                .retirementBankName(retAccount != null ? retAccount.getPensionProvider() : null)
                .retirementAccountNumber(retAccount != null ? retAccount.getAccountNumber() : null)
                // 세금 관련
                .dependentsCount(emp.getDependentsCount())
                .taxRateOption(emp.getTaxRateOption())
                .retirementType(emp.getRetirementType().name())
                .build();

        dto.calculateMonthlySalary();
        return dto;
    }


    // ── 급여 계좌 수정 ──
    @Transactional
    public void updateEmpAccount(UUID companyId, Long empId, EmpAccountReqDto reqDto) {

        employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        EmpAccounts account = empAccountsRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMP_ACCOUNT_NOT_FOUND));

        account.update(reqDto.getBankName(), reqDto.getAccountNumber(), reqDto.getAccountHolder());
    }


    // ── 퇴직연금 계좌 수정 ──
    @Transactional
    public void updateRetirementAccount(UUID companyId, Long empId, EmpRetirementAccountReqDto reqDto) {

        employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        EmpRetirementAccount retAccount = empRetirementAccountRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.RETIREMENT_ACCOUNT_NOT_FOUND));

        retAccount.update(reqDto.getPensionProvider(), reqDto.getAccountNumber());
    }


    // ── 고정수당 항목 조합 (계약상세 + 급여항목명) ──
    private List<ContractPayItemResDto> buildFixedPayItems(SalaryContract contract, UUID companyId) {
        if (contract == null) {
            return Collections.emptyList();
        }

        List<SalaryContractDetail> details = salaryContractDetailRepository
                .findByContractId(contract.getContractId());

        if (details.isEmpty()) {
            return Collections.emptyList();
        }

        // payItemId → PayItems 매핑 (항목명 조회)
        List<Long> payItemIds = details.stream()
                .map(SalaryContractDetail::getPayItemId)
                .toList();

        Map<Long, PayItems> payItemMap = payItemsRepository
                .findByPayItemIdInAndCompany_CompanyId(payItemIds, companyId)
                .stream()
                .collect(Collectors.toMap(PayItems::getPayItemId, Function.identity()));

        return details.stream()
                .map(d -> {
                    PayItems item = payItemMap.get(d.getPayItemId());
                    return ContractPayItemResDto.builder()
                            .payItemId(d.getPayItemId())
                            .payItemName(item != null ? item.getPayItemName() : "알 수 없는 항목")
                            .amount(d.getAmount())
                            .build();
                })
                .toList();
    }
}
```

---

## 4. Controller

### EmpSalaryController.java (신규)
**파일 위치**: `pay/controller/EmpSalaryController.java`

```java
package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.pay.dtos.EmpAccountReqDto;
import com.peoplecore.pay.dtos.EmpRetirementAccountReqDto;
import com.peoplecore.pay.dtos.EmpSalaryDetailResDto;
import com.peoplecore.pay.dtos.EmpSalaryResDto;
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

    private final EmpSalaryService empSalaryService;

    @Autowired
    public EmpSalaryController(EmpSalaryService empSalaryService) {
        this.empSalaryService = empSalaryService;
    }


    //    사원별 급여 목록 조회 (연봉 탭)
    @GetMapping
    public ResponseEntity<Page<EmpSalaryResDto>> getEmpSalaryList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false) EmpStatus empStatus,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                empSalaryService.getEmpSalaryList(companyId, empStatus, deptId, keyword, pageable));
    }

    //    사원 급여 상세 조회 (모달)
    @GetMapping("/{empId}")
    public ResponseEntity<EmpSalaryDetailResDto> getEmpSalaryDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId) {
        return ResponseEntity.ok(empSalaryService.getEmpSalaryDetail(companyId, empId));
    }

    //    급여 계좌 수정
    @PutMapping("/{empId}/account")
    public ResponseEntity<Void> updateEmpAccount(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId,
            @RequestBody @Valid EmpAccountReqDto reqDto) {
        empSalaryService.updateEmpAccount(companyId, empId, reqDto);
        return ResponseEntity.ok().build();
    }

    //    퇴직연금 계좌 수정
    @PutMapping("/{empId}/retirement-account")
    public ResponseEntity<Void> updateRetirementAccount(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId,
            @RequestBody @Valid EmpRetirementAccountReqDto reqDto) {
        empSalaryService.updateRetirementAccount(companyId, empId, reqDto);
        return ResponseEntity.ok().build();
    }
}
```

---

## 5. Entity 수정

### EmpAccounts.java — FK 변경 + update 메서드 추가
**파일 위치**: `pay/domain/EmpAccounts.java`

> `Long empId` → `Employee FK`로 변경 + update 메서드 추가

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

    public void update(String bankName, String accountNumber, String accountHolder) {
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
    }
}
```

---

### EmpRetirementAccount.java — FK 변경 + update 메서드 추가
**파일 위치**: `pay/domain/EmpRetirementAccount.java`

> `Long empId` → `Employee FK`로 변경 + update 메서드 추가

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

    public void update(String pensionProvider, String accountNumber) {
        this.pensionProvider = pensionProvider;
        this.accountNumber = accountNumber;
    }
}
```

---

## 6. ErrorCode 추가

**파일 위치**: `common/.../exception/ErrorCode.java`

```java
// 사원 계좌
EMP_ACCOUNT_NOT_FOUND(404, "사원 계좌 정보를 찾을 수 없습니다."),
RETIREMENT_ACCOUNT_NOT_FOUND(404, "퇴직연금 계좌 정보를 찾을 수 없습니다."),
```

---

## 주의사항

### PayItemsRepository 추가 메서드
기존 `PayItemsRepository`에 아래 메서드 추가 필요:

```java
List<PayItems> findByPayItemIdInAndCompany_CompanyId(List<Long> payItemIds, UUID companyId);
```

### FK 변경 반영 완료
- **SalaryContract**: `Long empId` → `@ManyToOne Employee employee` (FK 적용 완료, 이 문서에 반영됨)
- **EmpAccounts**: `Long empId` → `@ManyToOne Employee employee` (FK 적용 완료, 이 문서에 반영됨)

### EmployeeRepositoryCustom 보완
현재 `findAllwithFilter`에 companyId 파라미터가 없어서, 회사 필터가 적용되지 않는 상태입니다.
기존 QueryDSL에 companyId 조건 추가가 필요합니다:

```java
// EmployeeRepositoryCustom.java - 시그니처 변경
Page<Employee> findAllwithFilter(UUID companyId, String keyword, Long deptId,
    EmpType empType, EmpStatus empStatus, EmployeeSortField sortField, Pageable pageable);

// EmployeeRepositoryImpl.java - where절에 추가
.where(
    qEmployee.company.companyId.eq(companyId),  // ← 추가
    keywordContains(keyword),
    deptEq(deptId),
    empTypeEq(empType),
    empStatusEq(empStatus)
)
```

---

## 프론트 연동 참고

### 목록 조회 (GET)
```
GET /pay/admin/employees?empStatus=ACTIVE&deptId=1&keyword=김민수&page=0&size=20
Headers: X-User-Company: {companyId}

Response:
{
  "content": [
    {
      "empId": 1,
      "empName": "김민수",
      "empStatus": "ACTIVE",
      "deptName": "개발팀",
      "gradeName": "대리",
      "empHireDate": "2022-03-02",
      "empType": "FULL",
      "annualSalary": 48000000,
      "monthlySalary": 4000000,
      "bankName": "국민은행",
      "accountNumber": "123-456-789"
    }
  ],
  "totalElements": 8,
  "totalPages": 1
}
```

### 상세 조회 — 모달 (GET)
```
GET /pay/admin/employees/1
Headers: X-User-Company: {companyId}

Response:
{
  "empId": 1,
  "empName": "김민수",
  "empNum": "PC2024001",
  "empEmail": "pc2024001@peoplecore.kr",
  "empStatus": "ACTIVE",
  "deptName": "개발팀",
  "gradeName": "대리",
  "titleName": "팀원",
  "empHireDate": "2022-03-02",
  "empType": "FULL",
  "annualSalary": 48000000,           ← 읽기전용 (연봉계약서)
  "monthlySalary": 4000000,           ← 읽기전용 (연봉/12)
  "fixedPayItems": [                  ← 읽기전용 (연봉계약 상세)
    { "payItemId": 1, "payItemName": "식대", "amount": 200000 },
    { "payItemId": 2, "payItemName": "교통비", "amount": 100000 },
    { "payItemId": 3, "payItemName": "직책수당", "amount": 0 }
  ],
  "empAccountId": 1,                  ← 수정 가능
  "bankName": "국민은행",
  "accountNumber": "123-456-789",
  "retirementAccountId": 1,           ← 수정 가능
  "retirementBankName": null,
  "retirementAccountNumber": null,
  "dependentsCount": 1,
  "taxRateOption": 100,
  "retirementType": "DC"
}
```

### 필터 파라미터
- `empStatus`: `ACTIVE` / `ON_LEAVE` / `RESIGNED` / null(전체)
- `deptId`: 부서 ID / null(전체)
- `keyword`: 사원명 검색 / null

### empStatus, empType 한글 매핑 (프론트)
```
ACTIVE → 재직    |  FULL → 정규직
ON_LEAVE → 휴직  |  CONTRACT → 계약직
RESIGNED → 퇴직  |  DISPATCHED → 파견직
```
