# 정산보험료 - 백엔드 코드 (최종본)

> Admin 화면 — 정산기간 기반 4대보험 정산 조회/생성
> 정산기간 내 PAID 급여대장 기반 보수총액 × 요율 계산
> 기공제액(정산기간 내 이미 공제한 누적액) 차감하여 차액(추가징수/환급) 산출
> 국민연금은 정산 대상 제외 (연 1회 기준소득월액 재산정으로 처리)
> 급여대장 반영 시 정산전용 PayItems (isSystem=true) 6종 사용
> 목록 조회 시 페이징 처리 (합계용 전체 조회 + 테이블용 페이징 분리)

---

## API 목록

| # | Method | URL | 설명 |
|---|--------|-----|------|
| 1 | GET | `/pay/insurance?fromYearMonth=&toYearMonth=&page=&size=` | 정산보험료 목록 조회 (페이징) |
| 2 | POST | `/pay/insurance/calculate` | 보험료 산정 (정산기간 기반) |
| 3 | GET | `/pay/insurance/{settlementId}` | 사원별 보험료 상세 (모달용) |
| 4 | POST | `/pay/insurance/apply-to-payroll` | 정산보험료 → 급여대장 일괄반영 |

---

## 파일 체크리스트

| # | 구분 | 파일명 | 위치 | 작업 |
|---|------|--------|------|------|
| 1 | Entity | `InsuranceSettlement.java` | pay/domain/ | 정산기간/기공제액/차액 필드 추가 |
| 2 | Entity | `PayItems.java` | pay/domain/ | isSystem 필드 추가, update/delete 보호 |
| 3 | Repository | `InsuranceSettlementRepository.java` | pay/repository/ | 기간조회/기간삭제/페이징 메서드 추가 |
| 4 | Repository | `PayrollRunsRepository.java` | pay/repository/ | 기간+상태 조회 메서드 추가 |
| 5 | Repository | `PayrollDetailsRepository.java` | pay/repository/ | 기공제액 집계 JPQL 추가 |
| 6 | Repository | `PayItemsRepository.java` | pay/repository/ | isSystem 조회 메서드 추가 |
| 7 | Projection | `InsuranceDeductionSummary.java` | pay/dtos/ | 기공제액 집계용 프로젝션 인터페이스 |
| 8 | DTO | `InsuranceSettlementSummaryResDto.java` | pay/dtos/ | 상단 요약 (페이징 포함) |
| 9 | DTO | `InsuranceSettlementResDto.java` | pay/dtos/ | 목록 행 (정산액/기공제액/차액/diffCategory) |
| 10 | DTO | `InsuranceSettlementDetailResDto.java` | pay/dtos/ | 상세 모달 (요율+직급+직책) |
| 11 | DTO | `InsuranceSettlementCalcReqDto.java` | pay/dtos/ | 보험료 산정 요청 |
| 12 | DTO | `InsuranceSettlementApplyReqDto.java` | pay/dtos/ | 급여반영 요청 (기간 기반) |
| 13 | Service | `InsuranceSettlementService.java` | pay/service/ | 전체 비즈니스 로직 (페이징 포함) |
| 14 | Controller | `InsuranceSettlementController.java` | pay/controller/ | API 엔드포인트 (Pageable) |
| 15 | ErrorCode | `ErrorCode.java` | common/ | 에러코드 추가 |

---

## 1. Entity

### InsuranceSettlement.java
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
                @Index(name = "idx_settlement_company_month", columnList = "company_id, pay_year_month"),
                @Index(name = "idx_settlement_payroll_run", columnList = "payroll_run_id"),
                @Index(name = "idx_settlement_emp", columnList = "emp_id, pay_year_month")
        })
public class InsuranceSettlement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long settlementId;

    @Column(nullable = false, length = 7)
    private String payYearMonth;    // 적용 연월 (정산결과 반영 대상월)

    @Column(nullable = false)
    private Long baseSalary;        // 보수월액(보수총액)

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
    private Long totalEmployee;     // 근로자 부담 합계
    @Column(nullable = false)
    private Long totalEmployer;     // 사업주 부담 합계
    @Column(nullable = false)
    private Long totalAmount;       // 전체 합계

    @Builder.Default
    @Column(nullable = false)
    private Boolean isApplied = false;  // 급여반영여부
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

    // ── 정산기간 ──
    @Column(nullable = false, length = 7)
    private String settlementFromMonth;    // 정산 시작월 (예: "2025-04")
    @Column(nullable = false, length = 7)
    private String settlementToMonth;      // 정산 종료월 (예: "2026-03")

    // ── 기공제액 (정산기간 내 급여대장에서 이미 공제한 누적액) ──
    @Column(nullable = false)
    private Long deductedPension;
    @Column(nullable = false)
    private Long deductedHealth;
    @Column(nullable = false)
    private Long deductedLtc;
    @Column(nullable = false)
    private Long deductedEmployment;
    @Column(nullable = false)
    private Long totalDeducted;

    // ── 차액 (정산액 - 기공제액) : 양수=추가징수, 음수=환급 ──
    @Column(nullable = false)
    private Long diffPension;
    @Column(nullable = false)
    private Long diffHealth;
    @Column(nullable = false)
    private Long diffLtc;
    @Column(nullable = false)
    private Long diffEmployment;
    @Column(nullable = false)
    private Long totalDiff;

    // ── 급여반영 처리 ──
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
                            Long industrialEmpr,
                            Long dedPension, Long dedHealth, Long dedLtc, Long dedEmployment) {
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
        this.totalEmployer = pensionEmpr + healthEmpr + ltcEmpr + employmentEmpr;
        this.totalAmount = this.totalEmployee + this.totalEmployer;

        this.deductedPension = dedPension;
        this.deductedHealth = dedHealth;
        this.deductedLtc = dedLtc;
        this.deductedEmployment = dedEmployment;
        this.totalDeducted = dedPension + dedHealth + dedLtc + dedEmployment;

        this.diffPension = pensionEmp - dedPension;
        this.diffHealth = healthEmp - dedHealth;
        this.diffLtc = ltcEmp - dedLtc;
        this.diffEmployment = employmentEmp - dedEmployment;
        this.totalDiff = this.totalEmployee - this.totalDeducted;
    }
}
```

### PayItems.java (isSystem 필드 추가)
**파일 위치**: `pay/domain/PayItems.java`

> 기존 PayItems 엔티티에 `isSystem` 필드 추가
> isSystem=true인 항목은 수정/삭제 불가 (정산전용 6종 보호)

```java
// ── 추가할 필드 ──
    @Builder.Default
    private Boolean isSystem = false;   // 시스템 자동생성 항목 (정산전용 등)

// ── update 메서드 수정 (isSystem 보호) ──
    public void update(String payItemName, Boolean isFixed, Boolean isTaxable,
                       Integer taxExemptLimit, PayItemCategory payItemCategory) {
        if (Boolean.TRUE.equals(this.isSystem)) {
            throw new CustomException(ErrorCode.SYSTEM_PAY_ITEM_NOT_EDITABLE);
        }
        this.payItemName = payItemName;
        this.isFixed = isFixed;
        this.isTaxable = isTaxable;
        this.taxExemptLimit = taxExemptLimit;
        this.payItemCategory = payItemCategory;
    }

// ── softDelete 메서드 수정 (isSystem 보호) ──
    public void softDelete() {
        if (Boolean.TRUE.equals(this.isSystem)) {
            throw new CustomException(ErrorCode.SYSTEM_PAY_ITEM_NOT_DELETABLE);
        }
        this.isDeleted = true;
        this.isActive = false;
    }
```

---

## 2. Repository

### InsuranceSettlementRepository.java
**파일 위치**: `pay/repository/InsuranceSettlementRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.InsuranceSettlement;
import com.peoplecore.pay.domain.PayrollRuns;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InsuranceSettlementRepository extends JpaRepository<InsuranceSettlement, Long> {

    // 회사 + 연월 조회 (JOIN FETCH로 N+1 방지)
    @Query("SELECT s FROM InsuranceSettlement s JOIN FETCH s.employee e JOIN FETCH e.dept " +
           "WHERE s.company.companyId = :companyId AND s.payYearMonth = :payYearMonth ORDER BY e.empName ASC")
    List<InsuranceSettlement> findAllWithEmployee(@Param("companyId") UUID companyId, @Param("payYearMonth") String payYearMonth);

    // 특정 급여대장의 전체 정산보험료
    List<InsuranceSettlement> findByPayrollRuns(PayrollRuns payrollRuns);

    // 특정 정산 상세 (단건 — 모달용, 직급/직책 JOIN FETCH)
    @Query("SELECT s FROM InsuranceSettlement s " +
           "JOIN FETCH s.employee e JOIN FETCH e.dept " +
           "JOIN FETCH e.grade JOIN FETCH e.title " +
           "JOIN FETCH s.insuranceRates " +
           "WHERE s.settlementId = :settlementId AND s.company.companyId = :companyId")
    Optional<InsuranceSettlement> findDetailById(@Param("settlementId") Long settlementId, @Param("companyId") UUID companyId);

    // 해당 급여대장에 대한 정산 존재 여부
    boolean existsByPayrollRuns(PayrollRuns payrollRuns);

    // 해당 월 정산 삭제 (재산정 시)
    void deleteByPayrollRuns(PayrollRuns payrollRuns);

    // 정산보험료 급여반영용 (ID 배치 조회)
    @Query("SELECT s FROM InsuranceSettlement s JOIN FETCH s.employee e JOIN FETCH s.company " +
           "WHERE s.settlementId IN :ids AND s.company.companyId = :companyId")
    List<InsuranceSettlement> findAllByIdsAndCompany(@Param("ids") List<Long> ids, @Param("companyId") UUID companyId);

    // 정산기간으로 전체 조회 (합계 계산용 — 페이징 X)
    @Query("SELECT s FROM InsuranceSettlement s JOIN FETCH s.employee e JOIN FETCH e.dept " +
           "WHERE s.company.companyId = :companyId " +
           "AND s.settlementFromMonth = :fromMonth AND s.settlementToMonth = :toMonth " +
           "ORDER BY e.empName ASC")
    List<InsuranceSettlement> findAllByPeriodForSummary(
            @Param("companyId") UUID companyId,
            @Param("fromMonth") String fromMonth,
            @Param("toMonth") String toMonth);

    // 정산기간으로 페이지 조회 (테이블용 — 페이징 O)
    // JOIN FETCH + Page 사용 시 countQuery 별도 지정 필수
    @Query(value = "SELECT s FROM InsuranceSettlement s JOIN FETCH s.employee e JOIN FETCH e.dept " +
                   "WHERE s.company.companyId = :companyId " +
                   "AND s.settlementFromMonth = :fromMonth AND s.settlementToMonth = :toMonth " +
                   "ORDER BY e.empName ASC",
           countQuery = "SELECT COUNT(s) FROM InsuranceSettlement s " +
                        "WHERE s.company.companyId = :companyId " +
                        "AND s.settlementFromMonth = :fromMonth AND s.settlementToMonth = :toMonth")
    Page<InsuranceSettlement> findPageByPeriod(
            @Param("companyId") UUID companyId,
            @Param("fromMonth") String fromMonth,
            @Param("toMonth") String toMonth,
            Pageable pageable);

    // 정산기간 존재여부
    boolean existsByCompany_CompanyIdAndSettlementFromMonthAndSettlementToMonth(UUID companyId, String fromMonth, String toMonth);

    // 정산기간 삭제 (재산정시)
    void deleteByCompany_CompanyIdAndSettlementFromMonthAndSettlementToMonth(UUID companyId, String fromMonth, String toMonth);
}
```

### PayrollRunsRepository.java (추가 메서드)
**파일 위치**: `pay/repository/PayrollRunsRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayrollRuns;
import com.peoplecore.pay.enums.PayrollStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollRunsRepository extends JpaRepository<PayrollRuns, Long> {

    Optional<PayrollRuns> findByCompany_CompanyIdAndPayYearMonth(UUID companyId, String payYearMonth);

    // 정산기간 내 지급완료 급여대장 조회 (기공제액 산출용)
    List<PayrollRuns> findByCompany_CompanyIdAndPayrollStatusAndPayYearMonthBetween(
            UUID companyId, PayrollStatus payrollStatus, String fromMonth, String toMonth);
}
```

### PayrollDetailsRepository.java (추가 메서드)
**파일 위치**: `pay/repository/PayrollDetailsRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayrollDetails;
import com.peoplecore.pay.domain.PayrollRuns;
import com.peoplecore.pay.dtos.InsuranceDeductionSummary;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.enums.PayrollStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PayrollDetailsRepository extends JpaRepository<PayrollDetails, Long> {

    boolean existsByPayItemId(Long payItemId);

    List<PayrollDetails> findByPayrollRuns(PayrollRuns payrollRuns);

    // 기공제액 집계: 사원별 + 항목별 공제 합산 (정산기간 내 PAID 급여대장)
    @Query("SELECT pd.employee.empId AS empId, pd.payItemName AS payItemName, SUM(pd.amount) AS totalAmount " +
            "FROM PayrollDetails pd " +
            "WHERE pd.company.companyId = :companyId " +
            "AND pd.payrollRuns.payrollStatus = :status " +
            "AND pd.payrollRuns.payYearMonth BETWEEN :fromMonth AND :toMonth " +
            "AND pd.payItemType = :itemType " +
            "AND pd.payItemName IN :itemNames " +
            "GROUP BY pd.employee.empId, pd.payItemName")
    List<InsuranceDeductionSummary> sumDeductionsByEmpAndItem(
            @Param("companyId") UUID companyId,
            @Param("status") PayrollStatus status,
            @Param("fromMonth") String fromMonth,
            @Param("toMonth") String toMonth,
            @Param("itemType") PayItemType itemType,
            @Param("itemNames") List<String> itemNames);
}
```

### PayItemsRepository.java (추가 메서드)
**파일 위치**: `pay/repository/PayItemsRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayItems;
import com.peoplecore.pay.enums.PayItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayItemsRepository extends JpaRepository<PayItems, Long> {

    Optional<PayItems> findByPayItemIdAndCompany_CompanyId(Long payItemId, UUID companyId);

    List<PayItems> findByPayItemIdInAndCompany_CompanyId(List<Long> payItemIds, UUID companyId);

    List<PayItems> findByCompany_CompanyIdAndPayItemTypeAndIsActiveTrueOrderBySortOrderAsc(UUID companyId, PayItemType payItemType);

    List<PayItems> findByCompany_CompanyIdAndPayItemTypeAndPayItemNameIn(UUID companyId, PayItemType payItemType, List<String> payItemNames);

    // 정산전용 PayItems 조회 (isSystem=true인 항목만)
    List<PayItems> findByCompany_CompanyIdAndPayItemNameInAndIsSystemTrue(UUID companyId, List<String> payItemNames);
}
```

---

## 3. Projection 인터페이스

### InsuranceDeductionSummary.java (신규)
**파일 위치**: `pay/dtos/InsuranceDeductionSummary.java`

> JPQL GROUP BY 결과를 받는 프로젝션 인터페이스
> AS 별칭과 getter 이름이 매칭되어 JPA가 자동 매핑

```java
package com.peoplecore.pay.dtos;

public interface InsuranceDeductionSummary {
    Long getEmpId();
    String getPayItemName();
    Long getTotalAmount();
}
```

---

## 4. DTO

### InsuranceSettlementSummaryResDto.java
**파일 위치**: `pay/dtos/InsuranceSettlementSummaryResDto.java`

> 상단 요약 카드 (전체 합계) + 페이징 정보 + 반영현황

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InsuranceSettlementSummaryResDto {

    private String settlementFromMonth;
    private String settlementToMonth;
    private Integer totalEmployees;

    // 반영 현황
    private Integer appliedCount;           // 반영완료 인원수
    private Long totalChargeAmount;         // 추가징수 총액 (diff > 0 합산)
    private Long totalRefundAmount;         // 환급 총액 (diff < 0 합산, 절대값)

    // 정산액 합산
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
    private Long grandTotalEmployee;        // 근로자 부담 합계
    private Long grandTotalEmployer;        // 사업주 부담 합계

    // 기공제액 합산
    private Long grandTotalDeducted;

    // 차액 합산
    private Long grandTotalDiff;

    // 사원별 목록 (페이징)
    private List<InsuranceSettlementResDto> settlements;

    // 페이징 정보
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
```

### InsuranceSettlementResDto.java
**파일 위치**: `pay/dtos/InsuranceSettlementResDto.java`

> 목록 행 — 사원별 보험료 요약 + diffCategory

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.InsuranceSettlement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InsuranceSettlementResDto {

    private Long settlementId;
    private Long empId;
    private String empName;
    private String deptName;
    private Long baseSalary;

    // 정산액
    private Long pensionEmployee;
    private Long healthEmployee;
    private Long ltcEmployee;
    private Long employmentEmployee;
    private Long totalEmployee;

    // 기공제액
    private Long deductedPension;
    private Long deductedHealth;
    private Long deductedLtc;
    private Long deductedEmployment;
    private Long totalDeducted;

    // 차액
    private Long diffPension;
    private Long diffHealth;
    private Long diffLtc;
    private Long diffEmployment;
    private Long totalDiff;

    // 차액 구분 (화면 표시용): "추가징수" / "환급" / "차액없음"
    private String diffCategory;

    private Boolean isApplied;

    public static InsuranceSettlementResDto fromEntity(InsuranceSettlement s) {
        long totalDiff = s.getTotalDiff();
        String category;
        if (totalDiff > 0) {
            category = "추가징수";
        } else if (totalDiff < 0) {
            category = "환급";
        } else {
            category = "차액없음";
        }

        return InsuranceSettlementResDto.builder()
                .settlementId(s.getSettlementId())
                .empId(s.getEmployee().getEmpId())
                .empName(s.getEmployee().getEmpName())
                .deptName(s.getEmployee().getDept().getDeptName())
                .baseSalary(s.getBaseSalary())
                // 정산액
                .pensionEmployee(s.getPensionEmployee())
                .healthEmployee(s.getHealthEmployee())
                .ltcEmployee(s.getLtcEmployee())
                .employmentEmployee(s.getEmploymentEmployee())
                .totalEmployee(s.getTotalEmployee())
                // 기공제액
                .deductedPension(s.getDeductedPension())
                .deductedHealth(s.getDeductedHealth())
                .deductedLtc(s.getDeductedLtc())
                .deductedEmployment(s.getDeductedEmployment())
                .totalDeducted(s.getTotalDeducted())
                // 차액
                .diffPension(s.getDiffPension())
                .diffHealth(s.getDiffHealth())
                .diffLtc(s.getDiffLtc())
                .diffEmployment(s.getDiffEmployment())
                .totalDiff(s.getTotalDiff())
                .diffCategory(category)
                .isApplied(s.getIsApplied())
                .build();
    }
}
```

### InsuranceSettlementDetailResDto.java
**파일 위치**: `pay/dtos/InsuranceSettlementDetailResDto.java`

> 상세 모달 — 요율정보 + 사업주 부담 + 직급/직책 포함

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
@AllArgsConstructor
@NoArgsConstructor
public class InsuranceSettlementDetailResDto {

    private Long settlementId;
    private String payYearMonth;
    private String settlementFromMonth;
    private String settlementToMonth;
    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;       // 직급명
    private String titleName;       // 직책명
    private Long baseSalary;

    // 요율정보 (표시용)
    private BigDecimal pensionRate;
    private BigDecimal healthRate;
    private BigDecimal ltcRate;
    private BigDecimal employmentRate;
    private BigDecimal employmentEmployerRate;
    private BigDecimal industrialRate;

    // 정산액
    private Long pensionEmployee;
    private Long pensionEmployer;
    private Long healthEmployee;
    private Long healthEmployer;
    private Long ltcEmployee;
    private Long ltcEmployer;
    private Long employmentEmployee;
    private Long employmentEmployer;
    private Long industrialEmployer;
    private Long totalEmployee;
    private Long totalEmployer;
    private Long totalAmount;

    // 기공제액
    private Long deductedPension;
    private Long deductedHealth;
    private Long deductedLtc;
    private Long deductedEmployment;
    private Long totalDeducted;

    // 차액
    private Long diffPension;
    private Long diffHealth;
    private Long diffLtc;
    private Long diffEmployment;
    private Long totalDiff;

    private Boolean isApplied;

    public static InsuranceSettlementDetailResDto fromEntity(InsuranceSettlement s) {
        return InsuranceSettlementDetailResDto.builder()
                .settlementId(s.getSettlementId())
                .payYearMonth(s.getPayYearMonth())
                .settlementFromMonth(s.getSettlementFromMonth())
                .settlementToMonth(s.getSettlementToMonth())
                .empId(s.getEmployee().getEmpId())
                .empName(s.getEmployee().getEmpName())
                .deptName(s.getEmployee().getDept().getDeptName())
                .gradeName(s.getEmployee().getGrade() != null ? s.getEmployee().getGrade().getGradeName() : null)
                .titleName(s.getEmployee().getTitle() != null ? s.getEmployee().getTitle().getTitleName() : null)
                .baseSalary(s.getBaseSalary())
                // 요율
                .pensionRate(s.getInsuranceRates().getNationalPension())
                .healthRate(s.getInsuranceRates().getHealthInsurance())
                .ltcRate(s.getInsuranceRates().getLongTermCare())
                .employmentRate(s.getInsuranceRates().getEmploymentInsurance())
                .employmentEmployerRate(s.getInsuranceRates().getEmploymentInsuranceEmployer())
                .industrialRate(s.getInsuranceRates().getIndustrialAccident())
                // 정산액
                .pensionEmployee(s.getPensionEmployee())
                .pensionEmployer(s.getPensionEmployer())
                .healthEmployee(s.getHealthEmployee())
                .healthEmployer(s.getHealthEmployer())
                .ltcEmployee(s.getLtcEmployee())
                .ltcEmployer(s.getLtcEmployer())
                .employmentEmployee(s.getEmploymentEmployee())
                .employmentEmployer(s.getEmploymentEmployer())
                .industrialEmployer(s.getIndustrialEmployer())
                .totalEmployee(s.getTotalEmployee())
                .totalEmployer(s.getTotalEmployer())
                .totalAmount(s.getTotalAmount())
                // 기공제액
                .deductedPension(s.getDeductedPension())
                .deductedHealth(s.getDeductedHealth())
                .deductedLtc(s.getDeductedLtc())
                .deductedEmployment(s.getDeductedEmployment())
                .totalDeducted(s.getTotalDeducted())
                // 차액
                .diffPension(s.getDiffPension())
                .diffHealth(s.getDiffHealth())
                .diffLtc(s.getDiffLtc())
                .diffEmployment(s.getDiffEmployment())
                .totalDiff(s.getTotalDiff())
                .isApplied(s.getIsApplied())
                .build();
    }
}
```

### InsuranceSettlementCalcReqDto.java
**파일 위치**: `pay/dtos/InsuranceSettlementCalcReqDto.java`

```java
package com.peoplecore.pay.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceSettlementCalcReqDto {

    @NotBlank(message = "정산 시작월을 선택해주세요")
    private String fromYearMonth;       // 예: "2025-04"

    @NotBlank(message = "정산 종료월을 선택해주세요")
    private String toYearMonth;         // 예: "2026-03"
}
```

### InsuranceSettlementApplyReqDto.java
**파일 위치**: `pay/dtos/InsuranceSettlementApplyReqDto.java`

```java
package com.peoplecore.pay.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceSettlementApplyReqDto {

    @NotBlank(message = "반영대상 급여대장 월 선택은 필수입니다")
    private String targetPayYearMonth;      // 반영 대상 월 (예: "2026-04")

    @NotBlank(message = "정산 시작월은 필수입니다")
    private String fromYearMonth;

    @NotBlank(message = "정산 종료월은 필수입니다")
    private String toYearMonth;
}
```

---

## 5. Service

### InsuranceSettlementService.java
**파일 위치**: `pay/service/InsuranceSettlementService.java`

```java
package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.*;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.enums.PayrollStatus;
import com.peoplecore.pay.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class InsuranceSettlementService {

    // ── 4대보험 공제항목 표준명 ──
    private static final String ITEM_PENSION = "국민연금";
    private static final String ITEM_HEALTH = "건강보험";
    private static final String ITEM_LTC = "장기요양보험";
    private static final String ITEM_EMPLOYMENT = "고용보험";
    private static final List<String> INSURANCE_ITEM_NAMES = List.of(ITEM_PENSION, ITEM_HEALTH, ITEM_LTC, ITEM_EMPLOYMENT);

    // ── 정산전용 PayItems 항목명 (isSystem=true) ──
    private static final String SETTLE_HEALTH_CHARGE     = "건강보험 정산분";
    private static final String SETTLE_LTC_CHARGE        = "장기요양 정산분";
    private static final String SETTLE_EMPLOYMENT_CHARGE = "고용보험 정산분";
    private static final String SETTLE_HEALTH_REFUND     = "건강보험 환급분";
    private static final String SETTLE_LTC_REFUND        = "장기요양 환급분";
    private static final String SETTLE_EMPLOYMENT_REFUND = "고용보험 환급분";

    private static final List<String> SETTLEMENT_ITEM_NAMES = List.of(
            SETTLE_HEALTH_CHARGE, SETTLE_LTC_CHARGE, SETTLE_EMPLOYMENT_CHARGE,
            SETTLE_HEALTH_REFUND, SETTLE_LTC_REFUND, SETTLE_EMPLOYMENT_REFUND
    );

    private final InsuranceSettlementRepository insuranceSettlementRepository;
    private final PayrollRunsRepository payrollRunsRepository;
    private final InsuranceRatesRepository insuranceRatesRepository;
    private final PayrollDetailsRepository payrollDetailsRepository;
    private final PayItemsRepository payItemsRepository;

    @Autowired
    public InsuranceSettlementService(InsuranceSettlementRepository insuranceSettlementRepository,
                                      PayrollRunsRepository payrollRunsRepository,
                                      InsuranceRatesRepository insuranceRatesRepository,
                                      PayrollDetailsRepository payrollDetailsRepository,
                                      PayItemsRepository payItemsRepository) {
        this.insuranceSettlementRepository = insuranceSettlementRepository;
        this.payrollRunsRepository = payrollRunsRepository;
        this.insuranceRatesRepository = insuranceRatesRepository;
        this.payrollDetailsRepository = payrollDetailsRepository;
        this.payItemsRepository = payItemsRepository;
    }


    // ══════════════════════════════════════════════
    //  1. 정산보험료 목록조회 (합계 + 페이징)
    // ══════════════════════════════════════════════
    public InsuranceSettlementSummaryResDto getSettlementList(UUID companyId, String fromYearMonth, String toYearMonth, Pageable pageable) {

        // 합계용 전체 조회 (페이징 X)
        List<InsuranceSettlement> allSettlements = insuranceSettlementRepository
                .findAllByPeriodForSummary(companyId, fromYearMonth, toYearMonth);

        if (allSettlements.isEmpty()) {
            return InsuranceSettlementSummaryResDto.builder()
                    .settlementFromMonth(fromYearMonth)
                    .settlementToMonth(toYearMonth)
                    .totalEmployees(0)
                    .appliedCount(0).totalChargeAmount(0L).totalRefundAmount(0L)
                    .totalBaseSalary(0L)
                    .totalPensionEmployee(0L).totalPensionEmployer(0L)
                    .totalHealthEmployee(0L).totalHealthEmployer(0L)
                    .totalLtcEmployee(0L).totalLtcEmployer(0L)
                    .totalEmploymentEmployee(0L).totalEmploymentEmployer(0L)
                    .totalIndustrialEmployer(0L)
                    .grandTotalEmployee(0L).grandTotalEmployer(0L)
                    .grandTotalDeducted(0L).grandTotalDiff(0L)
                    .settlements(List.of())
                    .page(0).size(pageable.getPageSize()).totalElements(0).totalPages(0)
                    .build();
        }

        // 합계 계산 (전체 데이터)
        long sumBase = 0, sumPenEmp = 0, sumPenEmpr = 0;
        long sumHlthEmp = 0, sumHlthEmpr = 0;
        long sumLtcEmp = 0, sumLtcEmpr = 0;
        long sumEmpInsEmp = 0, sumEmpInsEmpr = 0;
        long sumIndEmpr = 0;
        long sumTotalEmp = 0, sumTotalEmpr = 0;
        long sumTotalDeducted = 0, sumTotalDiff = 0;
        int appliedCount = 0;
        long totalCharge = 0, totalRefund = 0;

        for (InsuranceSettlement s : allSettlements) {
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
            sumTotalDeducted += s.getTotalDeducted();
            sumTotalDiff += s.getTotalDiff();

            if (Boolean.TRUE.equals(s.getIsApplied())) appliedCount++;
            if (s.getTotalDiff() > 0) totalCharge += s.getTotalDiff();
            if (s.getTotalDiff() < 0) totalRefund += Math.abs(s.getTotalDiff());
        }

        // 테이블용 페이징 조회
        Page<InsuranceSettlement> pageResult = insuranceSettlementRepository
                .findPageByPeriod(companyId, fromYearMonth, toYearMonth, pageable);

        List<InsuranceSettlementResDto> dtos = pageResult.getContent().stream()
                .map(InsuranceSettlementResDto::fromEntity)
                .collect(Collectors.toList());

        return InsuranceSettlementSummaryResDto.builder()
                .settlementFromMonth(fromYearMonth)
                .settlementToMonth(toYearMonth)
                .totalEmployees(allSettlements.size())
                .appliedCount(appliedCount)
                .totalChargeAmount(totalCharge)
                .totalRefundAmount(totalRefund)
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
                .grandTotalDeducted(sumTotalDeducted)
                .grandTotalDiff(sumTotalDiff)
                .settlements(dtos)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
    }


    // ══════════════════════════════════════════════
    //  2. 보험료 산정 (정산기간 기반)
    // ══════════════════════════════════════════════
    @Transactional
    public InsuranceSettlementSummaryResDto calculateSettlement(UUID companyId, InsuranceSettlementCalcReqDto reqDto, Pageable pageable) {

        String fromMonth = reqDto.getFromYearMonth();
        String toMonth = reqDto.getToYearMonth();

        // 1. 정산기간 내 지급된(PAID) 급여대장 조회
        List<PayrollRuns> paidRuns = payrollRunsRepository
                .findByCompany_CompanyIdAndPayrollStatusAndPayYearMonthBetween(
                        companyId, PayrollStatus.PAID, fromMonth, toMonth);

        if (paidRuns.isEmpty()) {
            throw new CustomException(ErrorCode.PAYROLL_NOT_FOUND);
        }

        // 2. 보험요율 조회 (정산시작 연도 기준)
        int year = Integer.parseInt(fromMonth.substring(0, 4));
        InsuranceRates rates = insuranceRatesRepository
                .findByCompany_CompanyIdAndYear(companyId, year)
                .orElseThrow(() -> new CustomException(ErrorCode.INSURANCE_RATES_NOT_FOUND));

        // 3. PAID 급여대장의 사원별 지급항목(PAYMENT) 합산 → 보수총액
        Map<Long, Long> empBaseSalaryMap = new HashMap<>();
        Map<Long, Employee> empMap = new HashMap<>();

        for (PayrollRuns run : paidRuns) {
            List<PayrollDetails> details = payrollDetailsRepository.findByPayrollRuns(run);
            for (PayrollDetails d : details) {
                Long empId = d.getEmployee().getEmpId();
                empMap.putIfAbsent(empId, d.getEmployee());
                if (d.getPayItemType() == PayItemType.PAYMENT) {
                    empBaseSalaryMap.merge(empId, d.getAmount(), Long::sum);
                }
            }
        }

        // 4. 정산기간 내 기공제액 조회
        List<InsuranceDeductionSummary> deductionSummaries = payrollDetailsRepository
                .sumDeductionsByEmpAndItem(companyId, PayrollStatus.PAID,
                        fromMonth, toMonth, PayItemType.DEDUCTION, INSURANCE_ITEM_NAMES);

        Map<Long, Map<String, Long>> empDeductedMap = new HashMap<>();
        for (InsuranceDeductionSummary ds : deductionSummaries) {
            empDeductedMap.computeIfAbsent(ds.getEmpId(), k -> new HashMap<>())
                    .put(ds.getPayItemName(), ds.getTotalAmount());
        }

        // 5. 기존 정산데이터가 있으면 삭제 후 재생성
        if (insuranceSettlementRepository.existsByCompany_CompanyIdAndSettlementFromMonthAndSettlementToMonth(companyId, fromMonth, toMonth)) {
            insuranceSettlementRepository.deleteByCompany_CompanyIdAndSettlementFromMonthAndSettlementToMonth(companyId, fromMonth, toMonth);
        }

        // 6. 사원별 정산 계산
        List<InsuranceSettlement> newSettlements = new ArrayList<>();
        Company company = paidRuns.get(0).getCompany();

        for (Map.Entry<Long, Long> entry : empBaseSalaryMap.entrySet()) {
            Long empId = entry.getKey();
            Long baseSalary = entry.getValue();
            Employee emp = empMap.get(empId);

            // 산재보험요율: 사원의 업종별 요율, 없으면 기본요율
            BigDecimal industrialRate = rates.getIndustrialAccident();
            if (emp.getJobTypes() != null && emp.getJobTypes().getIndustrialAccidentRate() != null) {
                industrialRate = emp.getJobTypes().getIndustrialAccidentRate();
            }

            // 국민연금: 보수총액에 상/하한 적용 (월 상한 × 개월수)
            long pensionBase = baseSalary;
            long monthCount = countMonths(fromMonth, toMonth);
            long upperTotal = rates.getPensionUpperLimit() * monthCount;
            long lowerTotal = rates.getPensionLowerLimit() * monthCount;
            if (pensionBase > upperTotal) {
                pensionBase = upperTotal;
            } else if (pensionBase < lowerTotal) {
                pensionBase = lowerTotal;
            }

            long pensionEmp = calcHalf(pensionBase, rates.getNationalPension());
            long pensionEmpr = pensionEmp;

            // 건강보험
            long healthEmp = calcHalf(baseSalary, rates.getHealthInsurance());
            long healthEmpr = healthEmp;

            // 장기요양: 건강보험료 × 장기요양 요율
            long healthTotal = healthEmp + healthEmpr;
            long ltcTotal = calcAmount(healthTotal, rates.getLongTermCare());
            long ltcEmp = ltcTotal / 2;
            long ltcEmpr = ltcTotal - ltcEmp;   // 홀수원 처리

            // 고용보험
            long employmentEmp = calcAmount(baseSalary, rates.getEmploymentInsurance());
            long employmentEmpr = calcAmount(baseSalary, rates.getEmploymentInsuranceEmployer());

            // 산재보험
            long industrialEmpr = calcAmount(baseSalary, industrialRate);

            // ── 기공제액 ──
            Map<String, Long> deducted = empDeductedMap.getOrDefault(empId, Map.of());
            long dedPension = deducted.getOrDefault(ITEM_PENSION, 0L);
            long dedHealth = deducted.getOrDefault(ITEM_HEALTH, 0L);
            long dedLtc = deducted.getOrDefault(ITEM_LTC, 0L);
            long dedEmployment = deducted.getOrDefault(ITEM_EMPLOYMENT, 0L);

            // 합계
            long totalEmp = pensionEmp + healthEmp + ltcEmp + employmentEmp;
            long totalEmpr = pensionEmpr + healthEmpr + ltcEmpr + employmentEmpr + industrialEmpr;
            long totalDeducted = dedPension + dedHealth + dedLtc + dedEmployment;

            InsuranceSettlement settlement = InsuranceSettlement.builder()
                    .payYearMonth(toMonth)              // 정산기준월
                    .settlementFromMonth(fromMonth)
                    .settlementToMonth(toMonth)
                    .baseSalary(baseSalary)
                    // 정산액
                    .pensionEmployee(pensionEmp).pensionEmployer(pensionEmpr)
                    .healthEmployee(healthEmp).healthEmployer(healthEmpr)
                    .ltcEmployee(ltcEmp).ltcEmployer(ltcEmpr)
                    .employmentEmployee(employmentEmp).employmentEmployer(employmentEmpr)
                    .industrialEmployer(industrialEmpr)
                    .totalEmployee(totalEmp).totalEmployer(totalEmpr)
                    .totalAmount(totalEmp + totalEmpr)
                    // 기공제액
                    .deductedPension(dedPension).deductedHealth(dedHealth)
                    .deductedLtc(dedLtc).deductedEmployment(dedEmployment)
                    .totalDeducted(totalDeducted)
                    // 차액 (양수=추가징수, 음수=환급)
                    .diffPension(pensionEmp - dedPension)
                    .diffHealth(healthEmp - dedHealth)
                    .diffLtc(ltcEmp - dedLtc)
                    .diffEmployment(employmentEmp - dedEmployment)
                    .totalDiff(totalEmp - totalDeducted)
                    .isApplied(false)
                    .company(company)
                    .employee(emp)
                    .payrollRuns(paidRuns.get(paidRuns.size() - 1))
                    .insuranceRates(rates)
                    .build();

            newSettlements.add(settlement);
        }

        insuranceSettlementRepository.saveAll(newSettlements);
        return getSettlementList(companyId, fromMonth, toMonth, pageable);
    }


    // ══════════════════════════════════════════════
    //  3. 사원별 보험료 상세 조회 (모달)
    // ══════════════════════════════════════════════
    public InsuranceSettlementDetailResDto getSettlementDetail(UUID companyId, Long settlementId) {
        InsuranceSettlement settlement = insuranceSettlementRepository
                .findDetailById(settlementId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.INSURANCE_SETTLEMENT_NOT_FOUND));

        return InsuranceSettlementDetailResDto.fromEntity(settlement);
    }


    // ══════════════════════════════════════════════
    //  4. 정산보험료 → 급여대장 일괄반영 (diff 기반, 국민연금 제외)
    // ══════════════════════════════════════════════
    @Transactional
    public void applyToPayroll(UUID companyId, InsuranceSettlementApplyReqDto reqDto) {

        // 1. 정산기간으로 배치 조회
        List<InsuranceSettlement> settlements = insuranceSettlementRepository
                .findAllByPeriodForSummary(companyId, reqDto.getFromYearMonth(), reqDto.getToYearMonth());

        if (settlements.isEmpty()) {
            throw new CustomException(ErrorCode.INSURANCE_SETTLEMENT_NOT_FOUND);
        }

        // 2. 대상 월 급여대장
        PayrollRuns targetRun = payrollRunsRepository
                .findByCompany_CompanyIdAndPayYearMonth(companyId, reqDto.getTargetPayYearMonth())
                .orElseThrow(() -> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));

        if (targetRun.getPayrollStatus() != PayrollStatus.CALCULATING) {
            throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
        }

        // 3. 정산전용 PayItems 6종 조회 (isSystem=true)
        Map<String, PayItems> settlementItemMap = loadSettlementPayItems(companyId);

        // 4. 사원별 diff 반영 (국민연금 제외 — 연 1회 기준소득월액 재산정으로 처리)
        for (InsuranceSettlement s : settlements) {
            if (Boolean.TRUE.equals(s.getIsApplied())) continue;

            String period = s.getSettlementFromMonth() + "~" + s.getSettlementToMonth();

            // 건강보험 diff: 양수 → 정산분(추가징수/DEDUCTION), 음수 → 환급분(PAYMENT)
            createSettlementDetail(targetRun, s, s.getDiffHealth(),
                    settlementItemMap.get(SETTLE_HEALTH_CHARGE),
                    settlementItemMap.get(SETTLE_HEALTH_REFUND),
                    "건강보험 정산 (" + period + ")");

            // 장기요양 diff
            createSettlementDetail(targetRun, s, s.getDiffLtc(),
                    settlementItemMap.get(SETTLE_LTC_CHARGE),
                    settlementItemMap.get(SETTLE_LTC_REFUND),
                    "장기요양 정산 (" + period + ")");

            // 고용보험 diff
            createSettlementDetail(targetRun, s, s.getDiffEmployment(),
                    settlementItemMap.get(SETTLE_EMPLOYMENT_CHARGE),
                    settlementItemMap.get(SETTLE_EMPLOYMENT_REFUND),
                    "고용보험 정산 (" + period + ")");

            s.markApplied();
        }

        // 5. 급여대장 합계 재계산
        recalculateTotals(targetRun);
    }


    // ══════════════════════════════════════════════
    //  Private Helper Methods
    // ══════════════════════════════════════════════

    // 정산전용 PayItems 6종 로딩
    private Map<String, PayItems> loadSettlementPayItems(UUID companyId) {
        List<PayItems> items = payItemsRepository
                .findByCompany_CompanyIdAndPayItemNameInAndIsSystemTrue(companyId, SETTLEMENT_ITEM_NAMES);

        Map<String, PayItems> map = items.stream()
                .collect(Collectors.toMap(PayItems::getPayItemName, p -> p));

        for (String name : SETTLEMENT_ITEM_NAMES) {
            if (!map.containsKey(name)) {
                throw new CustomException(ErrorCode.INSURANCE_PAY_ITEM_NOT_FOUND);
            }
        }
        return map;
    }

    // diff 기반 정산 상세 1건 생성 (양수 → charge/DEDUCTION, 음수 → refund/PAYMENT)
    private void createSettlementDetail(PayrollRuns targetRun, InsuranceSettlement settlement,
                                        Long diff, PayItems chargeItem, PayItems refundItem, String memo) {
        if (diff == null || diff == 0L) return;

        PayItems targetItem;
        PayItemType targetType;
        long targetAmount;

        if (diff > 0) {
            targetItem = chargeItem;
            targetType = PayItemType.DEDUCTION;
            targetAmount = diff;
        } else {
            targetItem = refundItem;
            targetType = PayItemType.PAYMENT;
            targetAmount = Math.abs(diff);
        }

        PayrollDetails detail = PayrollDetails.builder()
                .payrollRuns(targetRun)
                .employee(settlement.getEmployee())
                .payItems(targetItem)
                .payItemName(targetItem.getPayItemName())
                .payItemType(targetType)
                .amount(targetAmount)
                .memo(memo)
                .company(settlement.getCompany())
                .build();

        payrollDetailsRepository.save(detail);
    }

    // 보수월액 × 요율 (반올림, 원단위)
    private long calcAmount(long base, BigDecimal rate) {
        return BigDecimal.valueOf(base)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    // 보수월액 × 요율 / 2 (근로자/사업주 반씩, 반올림)
    private long calcHalf(long base, BigDecimal rate) {
        return BigDecimal.valueOf(base)
                .multiply(rate)
                .divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    // 정산기간 개월 수 계산 (예: "2025-04" ~ "2026-03" → 12)
    private long countMonths(String fromYearMonth, String toYearMonth) {
        int fromYear = Integer.parseInt(fromYearMonth.substring(0, 4));
        int fromMonth = Integer.parseInt(fromYearMonth.substring(5, 7));
        int toYear = Integer.parseInt(toYearMonth.substring(0, 4));
        int toMonth = Integer.parseInt(toYearMonth.substring(5, 7));
        return (toYear - fromYear) * 12L + (toMonth - fromMonth) + 1;
    }

    // 급여대장 합계 재계산
    private void recalculateTotals(PayrollRuns run) {
        List<PayrollDetails> allDetails = payrollDetailsRepository.findByPayrollRuns(run);

        long totalPay = allDetails.stream()
                .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
                .mapToLong(PayrollDetails::getAmount).sum();
        long totalDeduction = allDetails.stream()
                .filter(d -> d.getPayItemType() == PayItemType.DEDUCTION)
                .mapToLong(PayrollDetails::getAmount).sum();
        long empCount = allDetails.stream()
                .map(d -> d.getEmployee().getEmpId())
                .distinct().count();

        run.updateTotals(empCount, totalPay, totalDeduction, totalPay - totalDeduction);
    }
}
```

---

## 6. Controller

### InsuranceSettlementController.java
**파일 위치**: `pay/controller/InsuranceSettlementController.java`

```java
package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.InsuranceSettlementApplyReqDto;
import com.peoplecore.pay.dtos.InsuranceSettlementCalcReqDto;
import com.peoplecore.pay.dtos.InsuranceSettlementDetailResDto;
import com.peoplecore.pay.dtos.InsuranceSettlementSummaryResDto;
import com.peoplecore.pay.service.InsuranceSettlementService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/pay/insurance")
@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
public class InsuranceSettlementController {

    private final InsuranceSettlementService insuranceSettlementService;

    public InsuranceSettlementController(InsuranceSettlementService insuranceSettlementService) {
        this.insuranceSettlementService = insuranceSettlementService;
    }

    // 정산보험료 목록조회 (페이징)
    @GetMapping
    public ResponseEntity<InsuranceSettlementSummaryResDto> getSettlementList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam String fromYearMonth,
            @RequestParam String toYearMonth,
            @PageableDefault(size = 10) Pageable pageable) {

        return ResponseEntity.ok(insuranceSettlementService.getSettlementList(companyId, fromYearMonth, toYearMonth, pageable));
    }

    // 보험료 산정 (정산기간 기반)
    @PostMapping("/calculate")
    public ResponseEntity<InsuranceSettlementSummaryResDto> calculateSettlement(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody @Valid InsuranceSettlementCalcReqDto reqDto,
            @PageableDefault(size = 10) Pageable pageable) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                insuranceSettlementService.calculateSettlement(companyId, reqDto, pageable));
    }

    // 사원별 보험료 상세 (모달)
    @GetMapping("/{settlementId}")
    public ResponseEntity<InsuranceSettlementDetailResDto> getSettlementDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long settlementId) {

        return ResponseEntity.ok(insuranceSettlementService.getSettlementDetail(companyId, settlementId));
    }

    // 정산보험료 급여대장에 일괄반영
    @PostMapping("/apply-to-payroll")
    public ResponseEntity<Void> applyToPayroll(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody @Valid InsuranceSettlementApplyReqDto reqDto) {

        insuranceSettlementService.applyToPayroll(companyId, reqDto);
        return ResponseEntity.ok().build();
    }
}
```

---

## 7. ErrorCode 추가
**파일 위치**: `common/ErrorCode.java`

```java
// 정산보험료 관련
INSURANCE_SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "IS001", "정산보험료를 찾을 수 없습니다"),
INSURANCE_RATES_NOT_FOUND(HttpStatus.NOT_FOUND, "IS002", "보험요율 정보를 찾을 수 없습니다"),
INSURANCE_PAY_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "IS003", "정산전용 급여항목이 등록되지 않았습니다"),
PAYROLL_NOT_FOUND(HttpStatus.NOT_FOUND, "PR001", "급여대장을 찾을 수 없습니다"),
PAYROLL_STATUS_INVALID(HttpStatus.BAD_REQUEST, "PR002", "급여대장 상태가 유효하지 않습니다"),

// PayItems isSystem 보호
SYSTEM_PAY_ITEM_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "PI001", "시스템 급여항목은 수정할 수 없습니다"),
SYSTEM_PAY_ITEM_NOT_DELETABLE(HttpStatus.BAD_REQUEST, "PI002", "시스템 급여항목은 삭제할 수 없습니다"),
```

---

## 8. 정산전용 PayItems 초기 데이터 (isSystem=true)

> 회사 생성 시 PayItems `initDefault()`에서 아래 6종을 자동 생성
> `isSystem=true`로 설정하여 관리자가 수정/삭제할 수 없도록 보호

| payItemName | payItemType | isSystem | 설명 |
|---|---|---|---|
| 건강보험 정산분 | DEDUCTION | true | diff > 0 일 때 사용 (추가징수) |
| 장기요양 정산분 | DEDUCTION | true | diff > 0 일 때 사용 |
| 고용보험 정산분 | DEDUCTION | true | diff > 0 일 때 사용 |
| 건강보험 환급분 | PAYMENT | true | diff < 0 일 때 사용 (환급) |
| 장기요양 환급분 | PAYMENT | true | diff < 0 일 때 사용 |
| 고용보험 환급분 | PAYMENT | true | diff < 0 일 때 사용 |

---

## 9. 비즈니스 로직 요약

### 정산 흐름
1. **산정 (calculate)**: 정산기간 내 PAID 급여대장 → 사원별 보수총액 합산 → 요율 적용 → 기공제액 차감 → 차액 산출
2. **조회 (getList)**: 합계용 전체 조회(페이징X) + 테이블용 페이지 조회(페이징O) 분리
3. **상세 (getDetail)**: 단건 조회 (직급/직책 JOIN FETCH), 요율정보 포함
4. **반영 (applyToPayroll)**: 차액(diff)을 급여대장 상세에 추가 — 양수면 DEDUCTION(정산분), 음수면 PAYMENT(환급분)

### 국민연금 제외
- 국민연금은 정산 대상에서 **제외** (연 1회 기준소득월액 재산정으로 별도 처리)
- 정산액 자체는 계산하지만, `applyToPayroll`에서 건강/장기요양/고용 3종만 반영
- 정산전용 PayItems도 3종 × 2(정산분/환급분) = 6종

### diff 로직
- `차액 = 정산액(근로자부담) - 기공제액`
- 양수 → 추가징수 → 정산분 항목(DEDUCTION)으로 급여대장에 추가
- 음수 → 환급 → 환급분 항목(PAYMENT)으로 급여대장에 추가 (절대값)
- 0 → 스킵

### 페이징 구조
- **합계**: `findAllByPeriodForSummary()` → 전체 데이터로 합산 (페이징 없음)
- **테이블**: `findPageByPeriod()` → `Page<InsuranceSettlement>` (countQuery 별도)
- 이유: 합계는 전체 데이터 기준이어야 하고, 테이블은 현재 페이지만 보여주면 됨

### 화면 ↔ DTO 매핑
| 화면 영역 | DTO 필드 | 설명 |
|---|---|---|
| 정산기간 표시 | settlementFromMonth, settlementToMonth | "2025-04 ~ 2026-03" |
| 반영현황 카드 | appliedCount, totalChargeAmount, totalRefundAmount | 반영인원/추가징수총액/환급총액 |
| 차액 구분 뱃지 | diffCategory | "추가징수" / "환급" / "차액없음" |
| 상세 모달 직급/직책 | gradeName, titleName | JOIN FETCH로 조회 |
| 페이징 | page, size, totalElements, totalPages | @PageableDefault(size=10) |
