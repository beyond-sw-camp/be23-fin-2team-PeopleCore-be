package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.EvaluationRules;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 평가규칙 리포지토리
public interface EvaluationRulesRepository extends JpaRepository<EvaluationRules, Long> {

    //    시즌 id로 규칙 조회
    Optional<EvaluationRules> findBySeason_SeasonId(Long seasonId);

    //    같은 회사의 직전 시즌 규칙 조회 (신규 시즌 제외, 시작일 내림차순)
    //    신규 시즌 생성 시 "이전 시즌 규칙 복사" 용
    @Query("""
            SELECT r FROM EvaluationRules r
            WHERE r.season.company.companyId = :companyId
              AND r.season.seasonId <> :excludeSeasonId
            ORDER BY r.season.startDate DESC, r.season.seasonId DESC
            """)
    List<EvaluationRules> findLatestByCompany(@Param("companyId") UUID companyId,
                                              @Param("excludeSeasonId") Long excludeSeasonId);
}
