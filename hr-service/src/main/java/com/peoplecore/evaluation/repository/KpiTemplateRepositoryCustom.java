package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.dto.KpiTemplateResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

// KPI지표 커스텀 (부서/카테고리 필터 + 페이징 + 검색)
public interface KpiTemplateRepositoryCustom {

    // gradeId 선택 시: 해당 직급 OR 전 직급 공통(null) 모두 매칭
    Page<KpiTemplateResponse> searchTemplates(UUID companyId, Long deptId, Long gradeId, String categoryLabel, String keyword, Pageable pageable);
}
