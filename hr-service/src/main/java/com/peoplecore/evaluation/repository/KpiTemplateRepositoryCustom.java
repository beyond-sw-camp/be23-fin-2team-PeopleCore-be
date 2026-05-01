package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.dto.KpiTemplateResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

// KPI지표 커스텀 (부서/카테고리 필터 + 페이징 + 검색)
public interface KpiTemplateRepositoryCustom {

    Page<KpiTemplateResponse> searchTemplates(UUID companyId, Long deptId, String categoryLabel, String keyword, Pageable pageable);
}
