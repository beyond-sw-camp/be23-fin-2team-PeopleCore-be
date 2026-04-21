package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.ManagerEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 팀장평가 리포지토리
public interface ManagerEvaluationRepository extends JpaRepository<ManagerEvaluation, Long> {

//    팀장(evaluator) 기준 시즌 전체 평가 row 조회 - 팀원 목록 뱃지용
    List<ManagerEvaluation> findByEvaluator_EmpIdAndSeason_SeasonId(Long evaluatorEmpId, Long seasonId);


//    특정 팀원에 대한 팀장의 평가 단건 조회 - 조회/임시저장/제출 공용 (사원별 1건 원칙)
    Optional<ManagerEvaluation> findByEmployee_EmpIdAndEvaluator_EmpIdAndSeason_SeasonId(
            Long employeeEmpId, Long evaluatorEmpId, Long seasonId);


//    15번 상세 조회용 - 특정 사원이 받은 상위자평가 단건 (등급/코멘트/피드백 추출)
    Optional<ManagerEvaluation> findByEmployee_EmpIdAndSeason_SeasonId(Long employeeEmpId, Long seasonId);
}
