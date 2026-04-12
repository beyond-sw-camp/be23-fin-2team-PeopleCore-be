package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.SelfEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

// 자기평가 리포지토리
public interface SelfEvaluationRepository extends JpaRepository<SelfEvaluation, Long> {
}
