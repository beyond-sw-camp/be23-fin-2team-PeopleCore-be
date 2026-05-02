package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.KpiOption;
import com.peoplecore.evaluation.domain.KpiOptionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// KPI 옵션 리포지토리
public interface KpiOptionRepository extends JpaRepository<KpiOption, Long> {

    // 회사 전체 활성 옵션 — type 묶음 → sort_order 오름차순
    List<KpiOption> findByCompany_CompanyIdAndIsActiveTrueOrderByTypeAscSortOrderAsc(UUID companyId);

    // 특정 type 행들만 (CATEGORY/UNIT 묶음 조회용)
    List<KpiOption> findByCompany_CompanyIdAndTypeAndIsActiveTrueOrderBySortOrderAsc(UUID companyId, KpiOptionType type);
}
