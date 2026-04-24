package com.peoplecore.evaluation.controller;

import com.peoplecore.evaluation.dto.SelfEvaluationDraftRequest;
import com.peoplecore.evaluation.dto.SelfEvaluationRejectRequest;
import com.peoplecore.evaluation.dto.SelfEvaluationResponse;
import com.peoplecore.evaluation.dto.TeamMemberSelfEvaluationResponse;
import com.peoplecore.evaluation.service.EvaluatorRoleService;
import com.peoplecore.evaluation.service.SelfEvaluationService;
import com.peoplecore.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

// 자기평가 - 사원 실적 입력/제출 + 평가자 검토/승인/반려
//   - 사원(1~5): 본인 자기평가 조회 / 임시저장 / 제출 / 근거파일 업로드·삭제 (가드 불필요)
//   - 평가자(6~10): 팀원 자기평가 조회 / 파일 다운로드 / 승인·반려 (평가자 가드 필수)
@RestController
@RequestMapping("/eval/self-evaluations")
public class SelfEvaluationController {

    private final SelfEvaluationService selfEvaluationService;
    private final EvaluatorRoleService evaluatorRoleService;

    public SelfEvaluationController(SelfEvaluationService selfEvaluationService,
                                    EvaluatorRoleService evaluatorRoleService) {
        this.selfEvaluationService = selfEvaluationService;
        this.evaluatorRoleService = evaluatorRoleService;
    }

    // 1. 본인 자기평가 목록 - 현재 OPEN 시즌, 목표 승인된 것 기준
    @GetMapping
    public ResponseEntity<List<SelfEvaluationResponse>> getMySelfEvaluations(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(selfEvaluationService.getMySelfEvaluations(companyId, empId));
    }

    // 2. 전체 임시저장 (submittedAt 유지, upsert)
    //    - 화면의 모든 항목 state 를 그대로 보냄
    @PutMapping("/draft")
    public ResponseEntity<List<SelfEvaluationResponse>> saveDraft(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody @Valid SelfEvaluationDraftRequest request) {
        return ResponseEntity.ok(selfEvaluationService.saveDraft(companyId, empId, request));
    }

    // 3. 전체 제출 (upsert + submittedAt = now, 반려 사유 초기화)
    //    - 임시저장 없이 바로 제출해도 되도록 body 동일 포맷으로 받음
    @PostMapping("/submit-all")
    public ResponseEntity<List<SelfEvaluationResponse>> submitAll(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody @Valid SelfEvaluationDraftRequest request) {
        return ResponseEntity.ok(selfEvaluationService.submitAll(companyId, empId, request));
    }

    // 4. 근거 파일 업로드 (multipart → MinIO)
    @PostMapping("/{goalId}/files")
    public ResponseEntity<SelfEvaluationResponse.FileResponse> uploadFile(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long goalId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(selfEvaluationService.uploadFile(companyId, empId, goalId, file));
    }

    // 5. 근거 파일 삭제 (MinIO 객체까지 제거)
    @DeleteMapping("/{goalId}/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long goalId,
            @PathVariable Long fileId) {
        selfEvaluationService.deleteFile(companyId, empId, goalId, fileId);
        return ResponseEntity.noContent().build();
    }

//    평가자 - 자기평가 조회/파일 다운로드/승인/반려

    // 6. 팀원 자기평가 목록 - 팀원별 묶음 (상단 카드 집계 -> 프론트)
    @GetMapping("/team")
    public ResponseEntity<List<TeamMemberSelfEvaluationResponse>> getTeamSelfEvaluations(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerId) {
        requireEvaluator(companyId, managerId);
        return ResponseEntity.ok(selfEvaluationService.getTeamSelfEvaluations(companyId, managerId));
    }

    // 7. 근거 파일 다운로드 - MinIO 스트리밍 (서비스 -> ResponseEntity 조립)
    @GetMapping("/{goalId}/files/{fileId}")
    public ResponseEntity<Resource> downloadFile(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerId,
            @PathVariable Long goalId,
            @PathVariable Long fileId) {
        requireEvaluator(companyId, managerId);
        return selfEvaluationService.downloadFile(companyId, managerId, goalId, fileId);
    }

    // 8. 단건 승인 - 대기 상태만 가능
    @PostMapping("/{goalId}/approve")
    public ResponseEntity<SelfEvaluationResponse> approveSelfEvaluation(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerId,
            @PathVariable Long goalId) {
        requireEvaluator(companyId, managerId);
        return ResponseEntity.ok(selfEvaluationService.approveSelfEvaluation(companyId, managerId, goalId));
    }

    // 9. 단건 반려 (body에 사유)
    @PostMapping("/{goalId}/reject")
    public ResponseEntity<SelfEvaluationResponse> rejectSelfEvaluation(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerId,
            @PathVariable Long goalId,
            @RequestBody @Valid SelfEvaluationRejectRequest request) {
        requireEvaluator(companyId, managerId);
        return ResponseEntity.ok(
                selfEvaluationService.rejectSelfEvaluation(companyId, managerId, goalId, request.getRejectReason()));
    }

    // 10. 팀원 자기평가 대기 건 일괄 승인
    @PostMapping("/approve-all/{empId}")
    public ResponseEntity<List<SelfEvaluationResponse>> approveAllPending(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerId,
            @PathVariable Long empId) {
        requireEvaluator(companyId, managerId);
        return ResponseEntity.ok(selfEvaluationService.approveAllPendingSelfEvaluations(companyId, managerId, empId));
    }

    // 각 엔드포인트 앞에서 호출되는 평가자 가드. empId 가 부서별 배정에 있는지만 확인.
    private void requireEvaluator(UUID companyId, Long empId) {
        if (!evaluatorRoleService.isEvaluator(companyId, empId)) {
            throw new BusinessException("평가자 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
    }
}
