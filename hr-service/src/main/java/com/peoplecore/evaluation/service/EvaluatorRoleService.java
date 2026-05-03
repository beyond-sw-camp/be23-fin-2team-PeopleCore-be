package com.peoplecore.evaluation.service;

import com.peoplecore.evaluation.dto.MyEvaluatorRoleResponse;
import com.peoplecore.evaluation.repository.EmpEvaluatorGlobalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// 평가자 판정 — 글로벌 매핑 기반. /me 응답 + 다른 컨트롤러 권한 가드 공용.
@Service
@Transactional(readOnly = true)
public class EvaluatorRoleService {

    private final EmpEvaluatorGlobalRepository empEvaluatorGlobalRepository;

    public EvaluatorRoleService(EmpEvaluatorGlobalRepository empEvaluatorGlobalRepository) {
        this.empEvaluatorGlobalRepository = empEvaluatorGlobalRepository;
    }

    // 본인이 누군가의 평가자인지 — 글로벌 매핑에 evaluator 로 등록돼있으면 true.
    // /me 응답 + 다른 컨트롤러 권한 가드 공용 (.isEvaluator() 로 boolean 추출).
    public MyEvaluatorRoleResponse me(UUID companyId, Long empId) {
        boolean evaluator = empId != null && empEvaluatorGlobalRepository.existsByCompanyIdAndEvaluator_EmpId(companyId, empId);
        return MyEvaluatorRoleResponse.builder()
            .evaluator(evaluator)
            .build();
    }
}
