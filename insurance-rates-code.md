# 사회보험요율표 - 전체 백엔드 코드

## 설계 요약

- `InsuranceRates`: 기존 구조 유지 + `employmentInsuranceEmployer` 추가
- `InsuranceJobTypes`: `industrialAccidentRate` 추가 (BaseTimeEntity 미상속)
- `InsuranceSettlement`: Long → @ManyToOne FK 변경
- 국민연금/건강/장기요양/고용보험(근로자) → DB에서 직접 관리 (코드X)
- 고용보험(사업주) → SuperAdmin 수정 가능
- 산재보험 → 업종별 CRUD (추가/수정/토글/삭제)

---

## 1. Entity

### InsuranceRates.java (수정)
**파일 위치**: `pay/domain/InsuranceRates.java`

```java
package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "insurance_rates",
    indexes = {
        @Index(name = "idx_rates_company_year", columnList = "company_id, year")
    })
public class InsuranceRates extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long insuranceRatesId;

//    적용연도
    @Column(nullable = false)
    private Integer year;

//    국민연금요율 (근로자 = 사업주 동일)
    @Column(precision = 5, scale = 4)
    private BigDecimal nationalPension;

//    건강보험요율 (근로자 = 사업주 동일)
    @Column(precision = 5, scale = 4)
    private BigDecimal healthInsurance;

//    장기요양보험요율 (건강보험의 %)
    @Column(precision = 5, scale = 4)
    private BigDecimal longTermCare;

//    고용보험요율 (근로자)
    @Column(precision = 5, scale = 4)
    private BigDecimal employmentInsurance;

//    고용보험요율 (사업주) ← 추가: 회사에서 수정 가능
    @Column(precision = 5, scale = 4)
    private BigDecimal employmentInsuranceEmployer;

//    산재보험요율 (기본업종)
    @Column(precision = 5, scale = 4)
    private BigDecimal industrialAccident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurance_job_types", nullable = false)
    private InsuranceJobTypes jobTypes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

//    보험요율 유효시작일
    @Column(nullable = false)
    private LocalDate validFrom;

//    보험요율 유효종료일
    private LocalDate validTo;

//    국민연금 상한/하한액
    @Column(nullable = false)
    private Long pensionUpperLimit;
    @Column(nullable = false)
    private Long pensionLowerLimit;


    // ── 회사(SuperAdmin)가 수정: 고용보험 사업주 요율 ──
    public void updateEmployerRate(BigDecimal employmentInsuranceEmployer) {
        this.employmentInsuranceEmployer = employmentInsuranceEmployer;
    }
}
```

---

### InsuranceJobTypes.java (수정)
**파일 위치**: `pay/domain/InsuranceJobTypes.java`

> BaseTimeEntity 미상속

```java
package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "insurance_job_types",
    indexes = {
        @Index(name = "idx_job_types_company", columnList = "company_id")
    })
public class InsuranceJobTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long jobTypesId;

    @Column(nullable = false, length = 50)
    private String name;

    private String description;

//    산재보험요율 ← 추가
    @Column(precision = 5, scale = 4)
    private BigDecimal industrialAccidentRate;

    @Builder.Default
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;


    public void update(String name, String description, BigDecimal industrialAccidentRate) {
        this.name = name;
        this.description = description;
        this.industrialAccidentRate = industrialAccidentRate;
    }

    public void toggleActive() {
        this.isActive = !this.isActive;
    }
}
```

---

### InsuranceSettlement.java (수정 - FK 변경)
**파일 위치**: `pay/domain/InsuranceSettlement.java`

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
        @Index(name = "idx_settlement_emp_month", columnList = "emp_id, pay_year_month"),
        @Index(name = "idx_settlement_payroll_run", columnList = "payroll_run_id")
    })
public class InsuranceSettlement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long settlementId;

    @Column(nullable = false, length = 7)
    private String payYearMonth;

    @Column(nullable = false)
    private Long baseSalary;

//    국민연금
    @Column(nullable = false)
    private Long pensionEmployee;
    @Column(nullable = false)
    private Long pensionEmployer;

//    건강보험
    @Column(nullable = false)
    private Long healthEmployee;
    @Column(nullable = false)
    private Long healthEmployer;

//    장기요양보험
    @Column(nullable = false)
    private Long ltcEmployee;
    @Column(nullable = false)
    private Long ltcEmployer;

//    고용보험
    @Column(nullable = false)
    private Long employmentEmployee;
    @Column(nullable = false)
    private Long employmentEmployer;

//    산재보험
    @Column(nullable = false)
    private Long industrialEmployer;

    @Column(nullable = false)
    private Long totalEmployee;
    @Column(nullable = false)
    private Long totalEmployer;
    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private Boolean isApplied;
    private LocalDateTime appliedAt;

    // ── FK 연관관계 ──

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRuns payrollRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurance_rates_id", nullable = false)
    private InsuranceRates insuranceRates;
}
```

---

## 2. Repository

### InsuranceRatesRepository.java (신규)
**파일 위치**: `pay/repository/InsuranceRatesRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.InsuranceRates;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InsuranceRatesRepository extends JpaRepository<InsuranceRates, Long> {

    // 회사의 특정 연도 요율
    Optional<InsuranceRates> findByCompany_CompanyIdAndYear(UUID companyId, Integer year);

    // 회사의 전체 연도 요율 (최신순)
    List<InsuranceRates> findByCompany_CompanyIdOrderByYearDesc(UUID companyId);
}
```

---

### InsuranceJobTypesRepository.java (수정)
**파일 위치**: `pay/repository/InsuranceJobTypesRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.InsuranceJobTypes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InsuranceJobTypesRepository extends JpaRepository<InsuranceJobTypes, Long> {

    Optional<InsuranceJobTypes> findByCompany_CompanyIdAndName(UUID companyId, String name);

    // 중복 검사용
    boolean existsByCompany_CompanyIdAndName(UUID companyId, String name);

    // 회사의 전체 업종 목록
    List<InsuranceJobTypes> findByCompany_CompanyIdOrderByJobTypesIdAsc(UUID companyId);

    // 특정 업종 (회사 검증 포함)
    Optional<InsuranceJobTypes> findByJobTypesIdAndCompany_CompanyId(Long jobTypesId, UUID companyId);
}
```

---

## 3. DTO

### InsuranceRatesResDto.java (신규)
**파일 위치**: `pay/dtos/InsuranceRatesResDto.java`

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.InsuranceRates;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceRatesResDto {

    private Long insuranceRatesId;
    private Integer year;
    private BigDecimal nationalPension;
    private BigDecimal healthInsurance;
    private BigDecimal longTermCare;
    private BigDecimal employmentInsurance;
    private BigDecimal employmentInsuranceEmployer;
    private BigDecimal industrialAccident;
    private String jobTypeName;
    private Long pensionUpperLimit;
    private Long pensionLowerLimit;
    private LocalDate validFrom;
    private LocalDate validTo;
    private LocalDateTime updatedAt;

    public static InsuranceRatesResDto fromEntity(InsuranceRates r) {
        return InsuranceRatesResDto.builder()
                .insuranceRatesId(r.getInsuranceRatesId())
                .year(r.getYear())
                .nationalPension(r.getNationalPension())
                .healthInsurance(r.getHealthInsurance())
                .longTermCare(r.getLongTermCare())
                .employmentInsurance(r.getEmploymentInsurance())
                .employmentInsuranceEmployer(r.getEmploymentInsuranceEmployer())
                .industrialAccident(r.getIndustrialAccident())
                .jobTypeName(r.getJobTypes().getName())
                .pensionUpperLimit(r.getPensionUpperLimit())
                .pensionLowerLimit(r.getPensionLowerLimit())
                .validFrom(r.getValidFrom())
                .validTo(r.getValidTo())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
```

---

### InsuranceRatesEmployerReqDto.java (신규)
**파일 위치**: `pay/dtos/InsuranceRatesEmployerReqDto.java`

```java
package com.peoplecore.pay.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceRatesEmployerReqDto {

    @NotNull(message = "고용보험 사업주 요율은 필수입니다.")
    @DecimalMin(value = "0.0001", message = "요율은 0보다 커야 합니다.")
    private BigDecimal employmentInsuranceEmployer;
}
```

---

### InsuranceJobTypesReqDto.java (신규)
**파일 위치**: `pay/dtos/InsuranceJobTypesReqDto.java`

```java
package com.peoplecore.pay.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceJobTypesReqDto {

    @NotBlank(message = "업종명은 필수입니다.")
    private String name;

    private String description;

    @NotNull(message = "산재보험요율은 필수입니다.")
    @DecimalMin(value = "0.0001", message = "요율은 0보다 커야 합니다.")
    private BigDecimal industrialAccidentRate;
}
```

---

### InsuranceJobTypesResDto.java (신규)
**파일 위치**: `pay/dtos/InsuranceJobTypesResDto.java`

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.InsuranceJobTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceJobTypesResDto {

    private Long jobTypesId;
    private String name;
    private String description;
    private BigDecimal industrialAccidentRate;
    private Boolean isActive;

    public static InsuranceJobTypesResDto fromEntity(InsuranceJobTypes j) {
        return InsuranceJobTypesResDto.builder()
                .jobTypesId(j.getJobTypesId())
                .name(j.getName())
                .description(j.getDescription())
                .industrialAccidentRate(j.getIndustrialAccidentRate())
                .isActive(j.getIsActive())
                .build();
    }
}
```

---

## 4. Service

### InsuranceRatesService.java (신규)
**파일 위치**: `pay/service/InsuranceRatesService.java`

```java
package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.InsuranceJobTypes;
import com.peoplecore.pay.domain.InsuranceRates;
import com.peoplecore.pay.dtos.InsuranceRatesEmployerReqDto;
import com.peoplecore.pay.dtos.InsuranceRatesResDto;
import com.peoplecore.pay.repository.InsuranceJobTypesRepository;
import com.peoplecore.pay.repository.InsuranceRatesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InsuranceRatesService {

    private final InsuranceRatesRepository insuranceRatesRepository;
    private final InsuranceJobTypesRepository insuranceJobTypesRepository;
    private final CompanyRepository companyRepository;

    @Autowired
    public InsuranceRatesService(InsuranceRatesRepository insuranceRatesRepository,
                                 InsuranceJobTypesRepository insuranceJobTypesRepository,
                                 CompanyRepository companyRepository) {
        this.insuranceRatesRepository = insuranceRatesRepository;
        this.insuranceJobTypesRepository = insuranceJobTypesRepository;
        this.companyRepository = companyRepository;
    }


    // ── 전체 연도 보험요율 목록 (최신순) ──
    public List<InsuranceRatesResDto> getAllRates(UUID companyId) {
        return insuranceRatesRepository.findByCompany_CompanyIdOrderByYearDesc(companyId)
                .stream()
                .map(InsuranceRatesResDto::fromEntity)
                .toList();
    }

    // ── 특정 연도 보험요율 조회 ──
    public InsuranceRatesResDto getRatesByYear(UUID companyId, Integer year) {
        InsuranceRates rates = findByCompanyAndYear(companyId, year);
        return InsuranceRatesResDto.fromEntity(rates);
    }

    // ── 고용보험 사업주 요율 수정 (SuperAdmin) ──
    @Transactional
    public InsuranceRatesResDto updateEmployerRate(UUID companyId, Integer year,
                                                   InsuranceRatesEmployerReqDto reqDto) {
        InsuranceRates rates = findByCompanyAndYear(companyId, year);
        rates.updateEmployerRate(reqDto.getEmploymentInsuranceEmployer());
        return InsuranceRatesResDto.fromEntity(rates);
    }


    // ── 회사 생성 시 기본 보험요율 세팅 ──
    @Transactional
    public void initDefault(Company company) {
        int currentYear = LocalDate.now().getYear();

        InsuranceJobTypes defaultJobType = insuranceJobTypesRepository
                .findByCompany_CompanyIdAndName(company.getCompanyId(), "기본업종")
                .orElseThrow(() -> new CustomException(ErrorCode.INSURANCE_JOB_TYPE_NOT_FOUND));

        InsuranceRates defaultRates = InsuranceRates.builder()
                .company(company)
                .year(currentYear)
                .nationalPension(new BigDecimal("0.0450"))
                .healthInsurance(new BigDecimal("0.03545"))
                .longTermCare(new BigDecimal("0.1295"))
                .employmentInsurance(new BigDecimal("0.0090"))
                .employmentInsuranceEmployer(new BigDecimal("0.0090"))
                .industrialAccident(new BigDecimal("0.0070"))
                .jobTypes(defaultJobType)
                .validFrom(LocalDate.of(currentYear, 1, 1))
                .pensionUpperLimit(6_170_000L)
                .pensionLowerLimit(390_000L)
                .build();

        insuranceRatesRepository.save(defaultRates);
    }


    private InsuranceRates findByCompanyAndYear(UUID companyId, Integer year) {
        return insuranceRatesRepository.findByCompany_CompanyIdAndYear(companyId, year)
                .orElseThrow(() -> new CustomException(ErrorCode.INSURANCE_RATES_NOT_FOUND));
    }
}
```

---

### InsuranceJobTypesService.java (수정 - 기존 교체)
**파일 위치**: `pay/service/InsuranceJobTypesService.java`

```java
package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.InsuranceJobTypes;
import com.peoplecore.pay.dtos.InsuranceJobTypesReqDto;
import com.peoplecore.pay.dtos.InsuranceJobTypesResDto;
import com.peoplecore.pay.repository.InsuranceJobTypesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InsuranceJobTypesService {

    private final InsuranceJobTypesRepository insuranceJobTypesRepository;
    private final CompanyRepository companyRepository;

    @Autowired
    public InsuranceJobTypesService(InsuranceJobTypesRepository insuranceJobTypesRepository,
                                    CompanyRepository companyRepository) {
        this.insuranceJobTypesRepository = insuranceJobTypesRepository;
        this.companyRepository = companyRepository;
    }


    // ── 산재보험 업종 목록 조회 ──
    public List<InsuranceJobTypesResDto> getJobTypes(UUID companyId) {
        return insuranceJobTypesRepository
                .findByCompany_CompanyIdOrderByJobTypesIdAsc(companyId)
                .stream()
                .map(InsuranceJobTypesResDto::fromEntity)
                .toList();
    }

    // ── 산재보험 업종 추가 ──
    @Transactional
    public InsuranceJobTypesResDto createJobType(UUID companyId, InsuranceJobTypesReqDto reqDto) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        if (insuranceJobTypesRepository.existsByCompany_CompanyIdAndName(companyId, reqDto.getName())) {
            throw new CustomException(ErrorCode.INSURANCE_JOB_TYPE_DUPLICATE);
        }

        InsuranceJobTypes jobType = InsuranceJobTypes.builder()
                .company(company)
                .name(reqDto.getName())
                .description(reqDto.getDescription())
                .industrialAccidentRate(reqDto.getIndustrialAccidentRate())
                .isActive(true)
                .build();

        return InsuranceJobTypesResDto.fromEntity(insuranceJobTypesRepository.save(jobType));
    }

    // ── 산재보험 업종 수정 (요율, 업종명, 설명) ──
    @Transactional
    public InsuranceJobTypesResDto updateJobType(UUID companyId, Long jobTypesId,
                                                 InsuranceJobTypesReqDto reqDto) {
        InsuranceJobTypes jobType = findByIdAndCompany(jobTypesId, companyId);

        jobType.update(reqDto.getName(), reqDto.getDescription(),
                       reqDto.getIndustrialAccidentRate());

        return InsuranceJobTypesResDto.fromEntity(jobType);
    }

    // ── 산재보험 업종 사용여부 토글 ──
    @Transactional
    public InsuranceJobTypesResDto toggleActive(UUID companyId, Long jobTypesId) {
        InsuranceJobTypes jobType = findByIdAndCompany(jobTypesId, companyId);

        jobType.toggleActive();

        return InsuranceJobTypesResDto.fromEntity(jobType);
    }

    // ── 산재보험 업종 삭제 ──
    @Transactional
    public void deleteJobType(UUID companyId, Long jobTypesId) {
        InsuranceJobTypes jobType = findByIdAndCompany(jobTypesId, companyId);

        insuranceJobTypesRepository.delete(jobType);
    }


    // ── 회사 생성 시 기본 업종 세팅 ──
    @Transactional
    public void initDefault(Company company) {
        insuranceJobTypesRepository.save(
                InsuranceJobTypes.builder()
                        .company(company)
                        .name("기본업종")
                        .description("일반 사무직")
                        .industrialAccidentRate(new BigDecimal("0.0070"))
                        .isActive(true)
                        .build()
        );
    }


    private InsuranceJobTypes findByIdAndCompany(Long jobTypesId, UUID companyId) {
        return insuranceJobTypesRepository
                .findByJobTypesIdAndCompany_CompanyId(jobTypesId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.INSURANCE_JOB_TYPE_NOT_FOUND));
    }
}
```

---

## 5. Controller

### InsuranceRatesController.java (신규)
**파일 위치**: `pay/controller/InsuranceRatesController.java`

```java
package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.service.InsuranceJobTypesService;
import com.peoplecore.pay.service.InsuranceRatesService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/superadmin/insurance")
@RoleRequired({"HR_SUPER_ADMIN"})
public class InsuranceRatesController {

    private final InsuranceRatesService insuranceRatesService;
    private final InsuranceJobTypesService insuranceJobTypesService;

    @Autowired
    public InsuranceRatesController(InsuranceRatesService insuranceRatesService,
                                    InsuranceJobTypesService insuranceJobTypesService) {
        this.insuranceRatesService = insuranceRatesService;
        this.insuranceJobTypesService = insuranceJobTypesService;
    }


    // ═══════════════════════════════════════════
    //  공통 보험요율 (국민연금/건강/장기요양/고용)
    // ═══════════════════════════════════════════

    //    전체 연도 보험요율 목록 (최신순)
    @GetMapping("/rates")
    public ResponseEntity<List<InsuranceRatesResDto>> getAllRates(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(insuranceRatesService.getAllRates(companyId));
    }

    //    특정 연도 보험요율 조회
    @GetMapping("/rates/{year}")
    public ResponseEntity<InsuranceRatesResDto> getRatesByYear(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Integer year) {
        return ResponseEntity.ok(insuranceRatesService.getRatesByYear(companyId, year));
    }

    //    고용보험 사업주 요율 수정
    @PutMapping("/rates/{year}/employer")
    public ResponseEntity<InsuranceRatesResDto> updateEmployerRate(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Integer year,
            @RequestBody @Valid InsuranceRatesEmployerReqDto reqDto) {
        return ResponseEntity.ok(insuranceRatesService.updateEmployerRate(companyId, year, reqDto));
    }


    // ═══════════════════════════════════════════
    //  산재보험 업종 관리
    // ═══════════════════════════════════════════

    //    산재보험 업종 목록 조회
    @GetMapping("/job-types")
    public ResponseEntity<List<InsuranceJobTypesResDto>> getJobTypes(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(insuranceJobTypesService.getJobTypes(companyId));
    }

    //    산재보험 업종 추가
    @PostMapping("/job-types")
    public ResponseEntity<InsuranceJobTypesResDto> createJobType(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody @Valid InsuranceJobTypesReqDto reqDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(insuranceJobTypesService.createJobType(companyId, reqDto));
    }

    //    산재보험 업종 수정 (요율, 업종명, 설명)
    @PutMapping("/job-types/{jobTypesId}")
    public ResponseEntity<InsuranceJobTypesResDto> updateJobType(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long jobTypesId,
            @RequestBody @Valid InsuranceJobTypesReqDto reqDto) {
        return ResponseEntity.ok(
                insuranceJobTypesService.updateJobType(companyId, jobTypesId, reqDto));
    }

    //    산재보험 업종 사용여부 토글
    @PatchMapping("/job-types/{jobTypesId}")
    public ResponseEntity<InsuranceJobTypesResDto> toggleJobTypeActive(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long jobTypesId) {
        return ResponseEntity.ok(
                insuranceJobTypesService.toggleActive(companyId, jobTypesId));
    }

    //    산재보험 업종 삭제
    @DeleteMapping("/job-types/{jobTypesId}")
    public ResponseEntity<Void> deleteJobType(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long jobTypesId) {
        insuranceJobTypesService.deleteJobType(companyId, jobTypesId);
        return ResponseEntity.noContent().build();
    }
}
```

---

## 6. ErrorCode 추가

**파일 위치**: `common/.../exception/ErrorCode.java`

```java
    // 사회보험요율
    INSURANCE_RATES_NOT_FOUND(404, "해당 연도의 보험요율을 찾을 수 없습니다."),
    INSURANCE_JOB_TYPE_DUPLICATE(409, "이미 존재하는 업종명입니다."),
```

> `INSURANCE_JOB_TYPE_NOT_FOUND`는 기존에 이미 있음

---

## 7. DDL (ERD용)

```sql
-- 업종 (산재보험)
CREATE TABLE insurance_job_types (
    job_types_id             BIGINT          NOT NULL AUTO_INCREMENT,
    name                     VARCHAR(50)     NOT NULL,
    description              VARCHAR(255),
    industrial_accident_rate DECIMAL(5,4),
    is_active                BIT             DEFAULT 1,
    company_id               BINARY(16)      NOT NULL,

    PRIMARY KEY (job_types_id),
    INDEX idx_job_types_company (company_id),
    CONSTRAINT fk_job_types_company
        FOREIGN KEY (company_id) REFERENCES company (company_id)
);


-- 사대보험요율
CREATE TABLE insurance_rates (
    insurance_rates_id              BIGINT       NOT NULL AUTO_INCREMENT,
    year                            INT          NOT NULL,
    national_pension                DECIMAL(5,4) COMMENT '국민연금요율',
    health_insurance                DECIMAL(5,4) COMMENT '건강보험요율',
    long_term_care                  DECIMAL(5,4) COMMENT '장기요양보험요율',
    employment_insurance            DECIMAL(5,4) COMMENT '고용보험 근로자',
    employment_insurance_employer   DECIMAL(5,4) COMMENT '고용보험 사업주',
    industrial_accident             DECIMAL(5,4) COMMENT '산재보험 기본업종',
    valid_from                      DATE         NOT NULL,
    valid_to                        DATE,
    pension_upper_limit             BIGINT       NOT NULL,
    pension_lower_limit             BIGINT       NOT NULL,
    insurance_job_types             BIGINT       NOT NULL,
    company_id                      BINARY(16)   NOT NULL,
    created_at                      DATETIME(6),
    updated_at                      DATETIME(6),

    PRIMARY KEY (insurance_rates_id),
    INDEX idx_rates_company_year (company_id, year),
    CONSTRAINT fk_rates_job_types
        FOREIGN KEY (insurance_job_types) REFERENCES insurance_job_types (job_types_id),
    CONSTRAINT fk_rates_company
        FOREIGN KEY (company_id) REFERENCES company (company_id)
);


-- 정산보험
CREATE TABLE insurance_settlement (
    settlement_id       BIGINT       NOT NULL AUTO_INCREMENT,
    pay_year_month      VARCHAR(7)   NOT NULL,
    base_salary         BIGINT       NOT NULL,
    pension_employee    BIGINT       NOT NULL,
    pension_employer    BIGINT       NOT NULL,
    health_employee     BIGINT       NOT NULL,
    health_employer     BIGINT       NOT NULL,
    ltc_employee        BIGINT       NOT NULL,
    ltc_employer        BIGINT       NOT NULL,
    employment_employee BIGINT       NOT NULL,
    employment_employer BIGINT       NOT NULL,
    industrial_employer BIGINT       NOT NULL,
    total_employee      BIGINT       NOT NULL,
    total_employer      BIGINT       NOT NULL,
    total_amount        BIGINT       NOT NULL,
    is_applied          BIT          NOT NULL DEFAULT 0,
    applied_at          DATETIME(6),
    company_id          BINARY(16)   NOT NULL,
    emp_id              BIGINT       NOT NULL,
    payroll_run_id      BIGINT       NOT NULL,
    insurance_rates_id  BIGINT       NOT NULL,
    created_at          DATETIME(6),
    updated_at          DATETIME(6),

    PRIMARY KEY (settlement_id),
    INDEX idx_settlement_emp_month (emp_id, pay_year_month),
    INDEX idx_settlement_payroll_run (payroll_run_id),
    CONSTRAINT fk_settlement_company
        FOREIGN KEY (company_id) REFERENCES company (company_id),
    CONSTRAINT fk_settlement_employee
        FOREIGN KEY (emp_id) REFERENCES employee (emp_id),
    CONSTRAINT fk_settlement_payroll_run
        FOREIGN KEY (payroll_run_id) REFERENCES payroll_runs (payroll_run_id),
    CONSTRAINT fk_settlement_rates
        FOREIGN KEY (insurance_rates_id) REFERENCES insurance_rates (insurance_rates_id)
);
```

---

## 8. 연도 갱신용 SQL (운영 시 사용)

```sql
-- 매년 1월 정부 고시 후 DBA가 실행
-- 예시: 2027년 요율 갱신 (전체 회사 일괄)
UPDATE insurance_rates
SET national_pension = 0.0450,
    health_insurance = 0.03545,
    long_term_care   = 0.1295,
    employment_insurance = 0.0090,
    pension_upper_limit  = 6370000,
    pension_lower_limit  = 400000,
    valid_from = '2027-01-01',
    valid_to   = NULL,
    year = 2027
WHERE year = 2026;
```

---

## API 전체 요약

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/pay/superadmin/insurance/rates` | 전체 연도 요율 목록 |
| GET | `/pay/superadmin/insurance/rates/{year}` | 특정 연도 요율 조회 |
| PUT | `/pay/superadmin/insurance/rates/{year}/employer` | 고용보험 사업주 요율 수정 |
| GET | `/pay/superadmin/insurance/job-types` | 산재보험 업종 목록 |
| POST | `/pay/superadmin/insurance/job-types` | 산재보험 업종 추가 |
| PUT | `/pay/superadmin/insurance/job-types/{id}` | 산재보험 업종 수정 |
| PATCH | `/pay/superadmin/insurance/job-types/{id}` | 산재보험 사용여부 토글 |
| DELETE | `/pay/superadmin/insurance/job-types/{id}` | 산재보험 업종 삭제 |

---

## 회사 생성 시 호출 순서

```java
insuranceJobTypesService.initDefault(company);  // 1) 기본업종 먼저
insuranceRatesService.initDefault(company);      // 2) 요율 (기본업종 FK 필요)
```
