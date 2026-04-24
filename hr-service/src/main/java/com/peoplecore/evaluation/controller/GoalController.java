package com.peoplecore.evaluation.controller;

import com.peoplecore.evaluation.dto.GoalDeleteResultDto;
import com.peoplecore.evaluation.dto.GoalRejectRequest;
import com.peoplecore.evaluation.dto.GoalRequest;
import com.peoplecore.evaluation.dto.GoalResponse;
import com.peoplecore.evaluation.dto.TeamMemberGoalResponse;
import com.peoplecore.evaluation.service.EvaluatorRoleService;
import com.peoplecore.evaluation.service.GoalService;
import com.peoplecore.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// 목표 - 사원 목표 등록/수정 및 평가자 승인/반려
//   - 사원(1~6): 본인 목표 CRUD + 제출 (가드 불필요)
//   - 평가자(7~10): 팀원 목표 조회/승인/반려 (평가자 가드 필수)
@RestController
@RequestMapping("/eval/goals")
public class GoalController {

    private final GoalService goalService;
    private final EvaluatorRoleService evaluatorRoleService;

    public GoalController(GoalService goalService,
                          EvaluatorRoleService evaluatorRoleService) {
        this.goalService = goalService;
        this.evaluatorRoleService = evaluatorRoleService;
    }

    // 1. 본인 목표 목록 조회 - 회사의 현재 진행(OPEN) 시즌만
    @GetMapping
    public ResponseEntity<List<GoalResponse>> getMyGoals(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(goalService.getMyGoals(companyId, empId));
    }

    // 2. 신규 등록
    @PostMapping
    public ResponseEntity<GoalResponse> createGoal(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody @Valid GoalRequest request) {
        return ResponseEntity.ok(goalService.createGoal(companyId, empId, request));
    }

    // 3. 수정
    //    -  작성중 또는 반려 상태만 수정 가능
    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> updateGoal(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id,
            @RequestBody @Valid GoalRequest request) {
        return ResponseEntity.ok(goalService.updateGoal(companyId, empId, id, request));
    }

    // 4. 삭제
    //    - 작성중 또는 반려 상태만 삭제 가능, 제출완료/승인 상태는 삭제 X
    //    - KPI 삭제 시 cascade: 마지막 KPI + OKR 잔존이면 confirm=false 로 1차 조회
    //      -> 반환된 cascadedOkrs 로 프론트 확인 다이얼로그 -> confirm=true 재호출 시 OKR 일괄 삭제 + KPI 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<GoalDeleteResultDto> deleteGoal(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean confirm) {
        return ResponseEntity.ok(goalService.deleteGoal(companyId, empId, id, confirm));
    }

    // 5. 단건 제출 (작성중 -> 제출완료, approval = 대기)
    //  - 프론트에서 카드별 "제출" 버튼 제거됨. 일괄 제출(submit-all)로 통합
    //  - 단건 제출이 다시 필요해지면 아래 주석 해제
    /*
    @PostMapping("/{id}/submit")
    public ResponseEntity<GoalResponse> submitGoal(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id) {
        return ResponseEntity.ok(goalService.submitGoal(companyId, empId, id));
    }
    */

    // 6. 본인의 작성중 목표 일괄 제출
    @PostMapping("/submit-all")
    public ResponseEntity<List<GoalResponse>> submitAllDrafts(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(goalService.submitAllDrafts(companyId, empId));
    }

//    평가자 목표 조회 및 승인

    // 7. 팀원 목표 전체 조회 - 팀원별 묶음 (상단 카드 집계 -> 프론트)
    @GetMapping("/team")
    public ResponseEntity<List<TeamMemberGoalResponse>> getTeamGoals(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerId) {
        requireEvaluator(companyId, managerId);
        return ResponseEntity.ok(goalService.getTeamGoals(companyId, managerId));
    }

    // 8. 단건 승인
    @PostMapping("/{id}/approve")
    public ResponseEntity<GoalResponse> approveGoal(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerId,
            @PathVariable Long id) {
        requireEvaluator(companyId, managerId);
        return ResponseEntity.ok(goalService.approveGoal(companyId, managerId, id));
    }

    // 9. 단건 반려 (body 에 사유)
    @PostMapping("/{id}/reject")
    public ResponseEntity<GoalResponse> rejectGoal(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerId,
            @PathVariable Long id,
            @RequestBody @Valid GoalRejectRequest request) {
        requireEvaluator(companyId, managerId);
        return ResponseEntity.ok(goalService.rejectGoal(companyId, managerId, id, request.getRejectReason()));
    }

    // 10. 팀원 단위 대기 건 일괄 승인
    @PostMapping("/approve-all/{empId}")
    public ResponseEntity<List<GoalResponse>> approveAllPending(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerId,
            @PathVariable Long empId) {
        requireEvaluator(companyId, managerId);
        return ResponseEntity.ok(goalService.approveAllPending(companyId, managerId, empId));
    }

    // 각 엔드포인트 앞에서 호출되는 평가자 가드. empId 가 부서별 배정에 있는지만 확인.
    private void requireEvaluator(UUID companyId, Long empId) {
        if (!evaluatorRoleService.isEvaluator(companyId, empId)) {
            throw new BusinessException("평가자 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
    }
}
