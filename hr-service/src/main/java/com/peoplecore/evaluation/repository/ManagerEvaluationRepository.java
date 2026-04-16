package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.ManagerEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

// 팀장평가 리포지토리
public interface ManagerEvaluationRepository extends JpaRepository<ManagerEvaluation, Long> {
}
