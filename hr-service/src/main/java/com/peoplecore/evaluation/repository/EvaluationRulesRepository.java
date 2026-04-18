package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.EvaluationRules;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// 평가규칙 리포지토리 (회사당 1 row)
public interface EvaluationRulesRepository extends JpaRepository<EvaluationRules, Long> {

    //    회사 id로 규칙 조회 (회사당 1개)
    Optional<EvaluationRules> findByCompany_CompanyId(UUID companyId);
}
