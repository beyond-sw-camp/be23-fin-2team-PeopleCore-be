package com.peoplecore.evaluation.controller;

import com.peoplecore.evaluation.dto.ManagerEvalAchievementDto;
import com.peoplecore.evaluation.dto.ManagerEvalDetailDto;
import com.peoplecore.evaluation.dto.ManagerEvalRequest;
import com.peoplecore.evaluation.dto.MySeasonOptionDto;
import com.peoplecore.evaluation.dto.TeamMemberEvalListDto;
import com.peoplecore.evaluation.dto.TeamMemberResultDto;
import com.peoplecore.evaluation.service.ManagerEvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// 팀장 평가 - 팀원 1명당 등급/코멘트/피드백 1건 (사원별 1건)
@RestController
@RequestMapping("/eval/manager-evaluations")
public class ManagerEvaluationController {

    private final ManagerEvaluationService mgrEvalService;

    public ManagerEvaluationController(ManagerEvaluationService mgrEvalService) {
        this.mgrEvalService = mgrEvalService;
    }


    // 1. 팀원 목록 - 이름/부서/직급 + 승인 목표 KPI/OKR 수 + 자기평가/팀장평가 제출 여부
    @GetMapping("/team-members")
    public ResponseEntity<List<TeamMemberEvalListDto>> getTeamMembers(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerEmpId) {
        return ResponseEntity.ok(mgrEvalService.getTeamMembers(companyId, managerEmpId));
    }


    // 2. 팀원 달성도 조회 (플로팅 패널용) - 자기평가 미제출자는 프론트에서 버튼 차단
    @GetMapping("/{empId}/achievement")
    public ResponseEntity<ManagerEvalAchievementDto> getAchievement(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerEmpId,
            @PathVariable Long empId) {
        return ResponseEntity.ok(mgrEvalService.getAchievement(companyId, managerEmpId, empId));
    }


    // 3. 기존 평가 조회 (임시저장 복구/수정용) - 없으면 빈 DTO
    @GetMapping("/{empId}")
    public ResponseEntity<ManagerEvalDetailDto> getEvaluation(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerEmpId,
            @PathVariable Long empId) {
        return ResponseEntity.ok(mgrEvalService.getEvaluation(companyId, managerEmpId, empId));
    }


    // 4. 임시 저장 - submittedAt 유지 (자기평가 미제출자, 프론트 차단)
    @PutMapping("/{empId}/draft")
    public ResponseEntity<Void> saveDraft(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerEmpId,
            @PathVariable Long empId,
            @RequestBody ManagerEvalRequest request) {
        mgrEvalService.saveDraft(companyId, managerEmpId, empId, request);
        return ResponseEntity.noContent().build();
    }


    // 5. 최종 제출 - submittedAt 기록 (자기평가 미제출자, 프론트 차단)
    @PostMapping("/{empId}/submit")
    public ResponseEntity<Void> submit(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerEmpId,
            @PathVariable Long empId,
            @RequestBody ManagerEvalRequest request) {
        mgrEvalService.submit(companyId, managerEmpId, empId, request);
        return ResponseEntity.noContent().build();
    }


    // 6. 팀원 최종 평가결과 일괄 조회 - 팀장이 본인 팀원 확정 등급 + 코멘트/피드백 확인
    //    autoGrade/finalGrade (EvalGrade) + gradeLabel/comment/feedback (ManagerEvaluation) 결합
    //    seasonId 로 과거 시즌까지 조회 가능
    //    gradeFilter 로 최종등급 필터 (null=전체, S/A/B/C/D 등)
    @GetMapping("/team-results")
    public ResponseEntity<List<TeamMemberResultDto>> getTeamResults(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerEmpId,
            @RequestParam Long seasonId,
            @RequestParam(required = false) String gradeFilter) {
        return ResponseEntity.ok(mgrEvalService.getTeamResults(companyId, managerEmpId, seasonId, gradeFilter));
    }


    // 7. 팀장 평가 결과 드롭다운 - 팀장이 평가자로 참여한 시즌 목록 (최신순, 과거 포함)
    @GetMapping("/team-results/seasons")
    public ResponseEntity<List<MySeasonOptionDto>> getTeamResultSeasons(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerEmpId) {
        return ResponseEntity.ok(mgrEvalService.getTeamResultSeasons(companyId, managerEmpId));
    }
}
