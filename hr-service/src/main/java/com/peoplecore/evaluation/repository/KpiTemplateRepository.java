package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.KpiTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

// KPI지표 템플릿 리포지토리
public interface KpiTemplateRepository extends JpaRepository<KpiTemplate, Long>, KpiTemplateRepositoryCustom {

    // 단건 조회 - 회사 스코프 강제 + 라벨 fetchJoin (grade 는 nullable -> leftJoin)
    @Query("""
            SELECT t
            FROM KpiTemplate t
            JOIN FETCH t.department d
            LEFT JOIN FETCH t.grade
            JOIN FETCH t.category
            JOIN FETCH t.unit
            WHERE t.kpiId = :id
              AND d.company.companyId = :companyId
              AND t.isActive = true
            """)
    Optional<KpiTemplate> findOneByCompany(@Param("id") Long id, @Param("companyId") UUID companyId);
}
