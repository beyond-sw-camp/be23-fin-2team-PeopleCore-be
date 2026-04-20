package com.peoplecore.evaluation.controller;

import com.peoplecore.evaluation.dto.SelfEvaluationDraftRequest;
import com.peoplecore.evaluation.dto.SelfEvaluationResponse;
import com.peoplecore.evaluation.service.SelfEvaluationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

// 자기평가 - 사원 실적 입력/제출
//   - 사원: 본인 자기평가 조회 / 임시저장 / 제출 / 근거파일 업로드·삭제
//   - 팀장용 조회/승인/반려는 별도 (추후 추가)
@RestController
@RequestMapping("/eval/self-evaluations")
public class SelfEvaluationController {

    private final SelfEvaluationService selfEvaluationService;

    public SelfEvaluationController(SelfEvaluationService selfEvaluationService) {
        this.selfEvaluationService = selfEvaluationService;
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
}
