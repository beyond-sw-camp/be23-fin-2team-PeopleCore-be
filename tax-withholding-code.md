# 간이세액표 확인 - 전체 백엔드 코드

## 설계 요약

- `TaxWithholdingTable` 엔티티: 변경 없음 (기존 그대로)
- company FK 없음 → 국세청 공통 데이터이므로 회사별 분리 불필요
- **조회 전용** (CRUD 중 R만)
- 데이터는 DB에 직접 INSERT (코드X)

---

## 1. Entity (변경 없음)

`TaxWithholdingTable.java` 현재 상태 그대로 사용합니다.

```java
@Entity
@Table(name = "tax_withholding_table")
public class TaxWithholdingTable {
    private Long taxId;
    private Integer taxYear;
    private Long salaryFrom;
    private Long salaryTo;
    private Integer dependents;   // 부양가족수 (본인 포함)
    private Long incomeTax;       // 소득세 (지방소득세 = 소득세 * 10%)
}
```

---

## 2. Repository

### TaxWithholdingRepository.java (신규)
**파일 위치**: `pay/repository/TaxWithholdingRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.TaxWithholdingTable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TaxWithholdingRepository extends JpaRepository<TaxWithholdingTable, Long> {

    // 특정 연도 세액표 (페이징)
    Page<TaxWithholdingTable> findByTaxYearOrderBySalaryFromAscDependentsAsc(
            Integer taxYear, Pageable pageable);

    // 세액 조회: 급여 구간 + 부양가족 수 → 세액 (급여산정 시 사용)
    Optional<TaxWithholdingTable> findByTaxYearAndSalaryFromLessThanEqualAndSalaryToGreaterThanAndDependents(
            Integer taxYear, Long salary, Long salary2, Integer dependents);

    // 등록된 연도 목록
    @Query("SELECT DISTINCT t.taxYear FROM TaxWithholdingTable t ORDER BY t.taxYear DESC")
    List<Integer> findDistinctTaxYears();

}
```

---

## 3. DTO

### TaxWithholdingResDto.java (신규)
**파일 위치**: `pay/dtos/TaxWithholdingResDto.java`

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.TaxWithholdingTable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxWithholdingResDto {

    private Long taxId;
    private Integer taxYear;
    private Long salaryFrom;
    private Long salaryTo;
    private Integer dependents;
    private Long incomeTax;
    private Long localIncomeTax;    // 지방소득세 = 소득세 * 10%

    public static TaxWithholdingResDto fromEntity(TaxWithholdingTable t) {
        return TaxWithholdingResDto.builder()
                .taxId(t.getTaxId())
                .taxYear(t.getTaxYear())
                .salaryFrom(t.getSalaryFrom())
                .salaryTo(t.getSalaryTo())
                .dependents(t.getDependents())
                .incomeTax(t.getIncomeTax())
                .localIncomeTax(t.getIncomeTax() / 10)
                .build();
    }
}
```

> ~~TaxYearSummaryResDto~~ → **불필요** (삭제)
> 간이세액표 데이터는 연도별 건수가 고정(급여구간 × 부양가족수)이므로
> 연도 목록은 `List<Integer>`로 충분합니다.

---

## 4. Service

### TaxWithholdingService.java (신규)
**파일 위치**: `pay/service/TaxWithholdingService.java`

```java
package com.peoplecore.pay.service;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.TaxWithholdingTable;
import com.peoplecore.pay.dtos.TaxWithholdingResDto;
import com.peoplecore.pay.repository.TaxWithholdingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TaxWithholdingService {

    private final TaxWithholdingRepository taxWithholdingRepository;

    @Autowired
    public TaxWithholdingService(TaxWithholdingRepository taxWithholdingRepository) {
        this.taxWithholdingRepository = taxWithholdingRepository;
    }


    // ── 등록된 연도 목록 ──
    public List<Integer> getYearList() {
        return taxWithholdingRepository.findDistinctTaxYears();
    }

    // ── 특정 연도 세액표 조회 (페이징) ──
    public Page<TaxWithholdingResDto> getTableByYear(Integer year, Pageable pageable) {
        Page<TaxWithholdingTable> page = taxWithholdingRepository
                .findByTaxYearOrderBySalaryFromAscDependentsAsc(year, pageable);

        if (page.isEmpty()) {
            throw new CustomException(ErrorCode.TAX_TABLE_NOT_FOUND);
        }

        return page.map(TaxWithholdingResDto::fromEntity);
    }

    // ── 세액 조회 (급여 + 부양가족 수 → 소득세/지방소득세) ──
    // 급여산정 시 내부 호출용으로도 사용
    public TaxWithholdingResDto lookupTax(Integer taxYear, Long monthlySalary, Integer dependents) {
        TaxWithholdingTable tax = taxWithholdingRepository
                .findByTaxYearAndSalaryFromLessThanEqualAndSalaryToGreaterThanAndDependents(
                        taxYear, monthlySalary, monthlySalary, dependents)
                .orElseThrow(() -> new CustomException(ErrorCode.TAX_TABLE_LOOKUP_FAILED));

        return TaxWithholdingResDto.fromEntity(tax);
    }
}
```

---

## 5. Controller

### TaxWithholdingController.java (신규)
**파일 위치**: `pay/controller/TaxWithholdingController.java`

```java
package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.TaxWithholdingResDto;
import com.peoplecore.pay.service.TaxWithholdingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pay/superadmin/tax-table")
@RoleRequired({"HR_SUPER_ADMIN"})
public class TaxWithholdingController {

    private final TaxWithholdingService taxWithholdingService;

    @Autowired
    public TaxWithholdingController(TaxWithholdingService taxWithholdingService) {
        this.taxWithholdingService = taxWithholdingService;
    }


    //    등록된 연도 목록 조회
    @GetMapping("/years")
    public ResponseEntity<List<Integer>> getYearList() {
        return ResponseEntity.ok(taxWithholdingService.getYearList());
    }

    //    특정 연도 세액표 조회 (페이징)
    @GetMapping("/{year}")
    public ResponseEntity<Page<TaxWithholdingResDto>> getTableByYear(
            @PathVariable Integer year,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(taxWithholdingService.getTableByYear(year, pageable));
    }

    // lookupTax()는 Controller에 노출하지 않음
    // → 급여계산 Service에서 taxWithholdingService.lookupTax()를 직접 호출하여 사용
}
```

---

## 6. ErrorCode 추가

**파일 위치**: `common/.../exception/ErrorCode.java`

```java
    // 간이세액표
    TAX_TABLE_NOT_FOUND(404, "해당 연도의 간이세액표를 찾을 수 없습니다."),
    TAX_TABLE_LOOKUP_FAILED(404, "해당 급여구간의 세액 정보를 찾을 수 없습니다."),
```

---

## 7. DDL + 샘플 데이터

### CREATE TABLE

```sql
CREATE TABLE tax_withholding_table (
    tax_id      BIGINT      NOT NULL AUTO_INCREMENT,
    tax_year    INT         NOT NULL COMMENT '적용연도',
    salary_from BIGINT      NOT NULL COMMENT '급여구간 하한',
    salary_to   BIGINT      NOT NULL COMMENT '급여구간 상한',
    dependents  INT         NOT NULL COMMENT '부양가족수 (본인 포함, 1~11)',
    income_tax  BIGINT      NOT NULL COMMENT '소득세 (지방소득세 = 소득세 * 10%)',

    PRIMARY KEY (tax_id),
    INDEX idx_tax_year_salary (tax_year, salary_from, salary_to),
    INDEX idx_tax_lookup (tax_year, dependents, salary_from)
);
```

### 2026년 샘플 데이터 (주요 구간)

```sql
-- ═══════════════════════════════════════════════════════════
--  2026년 간이세액표 샘플 데이터
--  실제 운영 시 국세청 전체 데이터를 INSERT 해야 합니다
--  부양가족수: 1(본인만) ~ 11(본인+10명)
-- ═══════════════════════════════════════════════════════════

INSERT INTO tax_withholding_table (tax_year, salary_from, salary_to, dependents, income_tax) VALUES
-- 월급여 1,060,000 ~ 1,500,000 구간 (부양가족 1~3명)
(2026, 1060000, 1500000, 1, 19060),
(2026, 1060000, 1500000, 2, 10060),
(2026, 1060000, 1500000, 3, 5710),

-- 월급여 1,500,001 ~ 2,000,000 구간
(2026, 1500001, 2000000, 1, 39060),
(2026, 1500001, 2000000, 2, 26060),
(2026, 1500001, 2000000, 3, 19060),

-- 월급여 2,000,001 ~ 2,500,000 구간
(2026, 2000001, 2500000, 1, 80150),
(2026, 2000001, 2500000, 2, 63680),
(2026, 2000001, 2500000, 3, 47760),

-- 월급여 2,500,001 ~ 3,000,000 구간
(2026, 2500001, 3000000, 1, 137520),
(2026, 2500001, 3000000, 2, 116040),
(2026, 2500001, 3000000, 3, 96240),

-- 월급여 3,000,001 ~ 3,500,000 구간
(2026, 3000001, 3500000, 1, 210870),
(2026, 3000001, 3500000, 2, 175890),
(2026, 3000001, 3500000, 3, 147510),

-- 월급여 3,500,001 ~ 4,000,000 구간
(2026, 3500001, 4000000, 1, 300870),
(2026, 3500001, 4000000, 2, 252450),
(2026, 3500001, 4000000, 3, 210870),

-- 월급여 4,000,001 ~ 5,000,000 구간
(2026, 4000001, 5000000, 1, 414200),
(2026, 4000001, 5000000, 2, 348240),
(2026, 4000001, 5000000, 3, 291870);
```

> **참고**: 위는 주요 구간 샘플입니다.
> 실제 운영 시 국세청 홈택스에서 전체 간이세액표를 다운로드하여 INSERT 해야 합니다.
> 부양가족 1~11명, 급여 구간 전체를 커버해야 합니다.

---

## API 요약

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/pay/superadmin/tax-table/years` | 등록된 연도 목록 |
| GET | `/pay/superadmin/tax-table/{year}` | 특정 연도 세액표 조회 (페이징) |
| - | `taxWithholdingService.lookupTax()` | 세액 조회 (Service 내부 호출용, Controller 미노출) |

---

## 파일 체크리스트

| # | 구분 | 파일명 | 작업 |
|---|------|--------|------|
| 1 | Entity | `TaxWithholdingTable.java` | 변경 없음 |
| 2 | Repository | `TaxWithholdingRepository.java` | 신규 |
| 3 | DTO | `TaxWithholdingResDto.java` | 신규 |
| 4 | Service | `TaxWithholdingService.java` | 신규 |
| 5 | Controller | `TaxWithholdingController.java` | 신규 |
| 6 | ErrorCode | `ErrorCode.java` | 2개 추가 |

---

## 참고: 세액 조회 공식 (급여산정 시 사용)

```
1. 월 과세급여 = 총 지급액 - 비과세 항목 합계
2. TaxWithholdingTable에서 조회:
   WHERE tax_year = 2026
     AND salary_from <= 과세급여
     AND salary_to > 과세급여
     AND dependents = 부양가족수
3. 소득세 = incomeTax
4. 지방소득세 = incomeTax / 10 (소득세의 10%)
```
