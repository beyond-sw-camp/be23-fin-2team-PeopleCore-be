package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.EmpEvaluatorGlobal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 글로벌 사원-평가자 매핑 레포
public interface EmpEvaluatorGlobalRepository extends JpaRepository<EmpEvaluatorGlobal, Long> {

    // 회사 전체 매핑 조회
    List<EmpEvaluatorGlobal> findByCompanyId(UUID companyId);

    // 평가자 1명 변경 시 해당 row 찾기
    Optional<EmpEvaluatorGlobal> findByCompanyIdAndEvaluatee_EmpId(UUID companyId, Long evaluateeEmpId);

    // 본인이 누군가의 평가자인지 (사이드바 메뉴 분기용)
    boolean existsByCompanyIdAndEvaluator_EmpId(UUID companyId, Long evaluatorEmpId);

    // 일괄 교체 시 기존 매핑 삭제 -> 재insert
    void deleteByCompanyId(UUID companyId);

    // 평가자 퇴사 시 — 그 사원이 evaluator 인 글로벌 매핑 row 들 삭제 (미정 상태로 되돌림)
    void deleteByCompanyIdAndEvaluator_EmpId(UUID companyId, Long evaluatorEmpId);
}
