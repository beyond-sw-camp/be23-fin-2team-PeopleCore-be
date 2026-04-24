package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.EvaluatorRoleDeptAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// 부서별 평가자 배정 리포지토리.
public interface EvaluatorRoleDeptAssignmentRepository extends JpaRepository<EvaluatorRoleDeptAssignment, Long> {

    // config 별 전체 배정 조회
    List<EvaluatorRoleDeptAssignment> findByConfig_Id(Long configId);

    // config 별 전체 삭제 (설정 갱신 시 원자 교체용)
    void deleteByConfig_Id(Long configId);

    // empId 가 평가자인지 여부 (gate 에서 자주 호출)
    boolean existsByConfig_CompanyIdAndEmployee_EmpId(UUID companyId, Long empId);
}
