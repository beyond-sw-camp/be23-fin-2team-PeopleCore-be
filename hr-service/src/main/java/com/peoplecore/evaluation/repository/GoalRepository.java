package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 목표 리포지토리
public interface GoalRepository extends JpaRepository<Goal, Long>, GoalRepositoryCustom {

    // KPI 템플릿 사용 중인 목표 수 - 삭제 분기 판단
    long countByKpiTemplate_KpiId(Long kpiId);

    // 사원의 특정 시즌 목표 목록 - 최신순
    List<Goal> findByEmp_EmpIdAndSeason_SeasonIdOrderByGoalIdDesc(Long empId, Long seasonId);
}
