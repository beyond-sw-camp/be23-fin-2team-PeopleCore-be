package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.EvaluationRules;
import org.springframework.data.jpa.repository.JpaRepository;

// 평가규칙 리포지토리
public interface EvaluationRulesRepository extends JpaRepository<EvaluationRules, Long> {
}
