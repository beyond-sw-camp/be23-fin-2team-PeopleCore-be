package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.KpiTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

// KPI지표 템플릿 리포지토리
public interface KpiTemplateRepository extends JpaRepository<KpiTemplate, Long>, KpiTemplateRepositoryCustom {
}
