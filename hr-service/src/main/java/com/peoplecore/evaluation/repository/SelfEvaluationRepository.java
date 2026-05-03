package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.SelfEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 자기평가 리포지토리
public interface SelfEvaluationRepository extends JpaRepository<SelfEvaluation, Long> {

    // 여러 목표의 자기평가를 IN 절로 한 번에 조회 (루프 개별 조회 대신)
    List<SelfEvaluation> findByGoal_GoalIdIn(List<Long> goalIds);

    // KPI 사내평균 집계 — 시즌 endDate 가 from~to 범위에 들어가고
    //   자기평가가 APPROVED 인 actualValue 의 KPI 별 평균
    interface KpiAvgRow {
        Long getKpiId();
        BigDecimal getAvg();
    }

    @Query("""
            SELECT g.kpiTemplate.kpiId AS kpiId,
                   AVG(s.actualValue)  AS avg
            FROM SelfEvaluation s
            JOIN s.goal g
            JOIN g.season sn
            WHERE g.kpiTemplate.kpiId IN :kpiIds
              AND sn.endDate BETWEEN :from AND :to
              AND s.approvalStatus = com.peoplecore.evaluation.domain.SelfEvalApprovalStatus.APPROVED
              AND s.actualValue IS NOT NULL
            GROUP BY g.kpiTemplate.kpiId
            """)
    List<KpiAvgRow> averageByKpiInRange(@Param("kpiIds") List<Long> kpiIds,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);
}
