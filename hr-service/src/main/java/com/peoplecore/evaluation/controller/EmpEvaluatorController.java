package com.peoplecore.evaluation.controller;

import com.peoplecore.evaluation.dto.EmpEvaluatorChangeRequest;
import com.peoplecore.evaluation.dto.EmpEvaluatorGlobalResponse;
import com.peoplecore.evaluation.dto.EmpEvaluatorMappingDto;
import com.peoplecore.evaluation.dto.EmpEvaluatorUpdateRequest;
import com.peoplecore.evaluation.service.EmpEvaluatorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// 사원별 평가자 매핑 페이지의 모든 동작 처리.
// HR_ADMIN / HR_SUPER_ADMIN 만 접근 가능.
@RestController
public class EmpEvaluatorController {

    private final EmpEvaluatorService service;

    public EmpEvaluatorController(EmpEvaluatorService service) {
        this.service = service;
    }

    // HR 관리자 권한 체크 — 4개 endpoint 공통 가드
    private static boolean isHrAdminOrAbove(String role) {
        return "HR_ADMIN".equals(role) || "HR_SUPER_ADMIN".equals(role);
    }

    // 사원-평가자 매핑 전체 조회
    // -> 매핑 페이지 진입 시 호출. 진행 중 시즌 있으면 그 시즌 대상자만 노출.
    @GetMapping("/emp-evaluator/global")
    public ResponseEntity<EmpEvaluatorGlobalResponse> getGlobal(@RequestHeader("X-User-Company") UUID companyId,
                                                                @RequestHeader("X-User-Role") String role) {
        if (!isHrAdminOrAbove(role)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(service.getGlobal(companyId));
    }

    // 매핑 일괄 저장 (전체 교체)
    // -> 페이지 저장 버튼 클릭 시 호출. 변경된 행 모두 한 번에 트랜잭션으로 저장.
    @PutMapping("/emp-evaluator/global")
    public ResponseEntity<EmpEvaluatorGlobalResponse> updateGlobal(@RequestHeader("X-User-Company") UUID companyId,
                                                                   @RequestHeader("X-User-Role") String role,
                                                                   @Valid @RequestBody EmpEvaluatorUpdateRequest request) {
        if (!isHrAdminOrAbove(role)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(service.updateGlobal(companyId, request));
    }

    // 평가 제외 처리 (그 사원은 시즌 평가 대상에서 빠짐)
    // -> 행 호버 시 "평가 제외" 클릭 시 호출. evaluator null + excluded=true 로 변경.
    @PatchMapping("/emp-evaluator/global/{evaluateeEmpId}/exclude")
    public ResponseEntity<EmpEvaluatorMappingDto> markExcluded(@RequestHeader("X-User-Company") UUID companyId,
                                                               @RequestHeader("X-User-Role") String role,
                                                               @PathVariable Long evaluateeEmpId) {
        if (!isHrAdminOrAbove(role)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(service.markExcluded(companyId, evaluateeEmpId));
    }

    // 시즌 진행 중 평가자 재지정 — 퇴사로 풀린 미지정 행에만 허용. EvalGrade 박제값 update.
    // -> 시즌 OPEN 중 매핑 페이지에서 미지정 행에 평가자 지정 시 호출.
    @PatchMapping("/emp-evaluator/season/{evaluateeEmpId}/evaluator")
    public ResponseEntity<Void> reassignDuringSeason(@RequestHeader("X-User-Company") UUID companyId,
                                                     @RequestHeader("X-User-Role") String role,
                                                     @PathVariable Long evaluateeEmpId,
                                                     @Valid @RequestBody EmpEvaluatorChangeRequest request) {
        if (!isHrAdminOrAbove(role)) return ResponseEntity.status(403).build();
        service.reassignDuringSeason(companyId, evaluateeEmpId, request);
        return ResponseEntity.ok().build();
    }
}
