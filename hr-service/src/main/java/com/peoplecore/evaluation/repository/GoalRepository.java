package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

// 목표 리포지토리
public interface GoalRepository extends JpaRepository<Goal, Long>, GoalRepositoryCustom {
}
