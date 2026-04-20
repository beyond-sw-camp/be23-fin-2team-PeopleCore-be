package com.peoplecore.evaluation.controller;

import com.peoplecore.evaluation.dto.GoalRequest;
import com.peoplecore.evaluation.dto.GoalResponse;
import com.peoplecore.evaluation.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// 목표 - 사원 목표 등록/수정 및 팀장 승인/반려
//   - 사원: 본인 목표 CRUD + 제출
//   - 팀장용 승인/반려는 별도 (추후 추가)
@RestController
@RequestMapping("/eval/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
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
    //    - 작성중 또는 반려 상태만 삭제 가능
    //    - 제출완료/승인 상태는 삭제x
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoal(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id) {
        goalService.deleteGoal(companyId, empId, id);
        return ResponseEntity.noContent().build();
    }

    // 5. 단건 제출 (작성중 -> 제출완료, approval = 대기)
    @PostMapping("/{id}/submit")
    public ResponseEntity<GoalResponse> submitGoal(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id) {
        return ResponseEntity.ok(goalService.submitGoal(companyId, empId, id));
    }

    // 6. 본인의 작성중 목표 일괄 제출
    @PostMapping("/submit-all")
    public ResponseEntity<List<GoalResponse>> submitAllDrafts(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(goalService.submitAllDrafts(companyId, empId));
    }
}
