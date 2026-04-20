package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.SelfEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 자기평가 리포지토리
public interface SelfEvaluationRepository extends JpaRepository<SelfEvaluation, Long> {

    // 여러 목표의 자기평가를 IN 절로 한 번에 조회 (루프 개별 조회 대신)
    List<SelfEvaluation> findByGoal_GoalIdIn(List<Long> goalIds);
}
