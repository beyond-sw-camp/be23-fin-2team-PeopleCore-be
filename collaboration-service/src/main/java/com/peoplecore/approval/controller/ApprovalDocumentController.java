package com.peoplecore.approval.controller;

import com.peoplecore.approval.dto.DocumentCreateRequest;
import com.peoplecore.approval.dto.DocumentDetailResponse;
import com.peoplecore.approval.dto.DocumentUpdateRequest;
import com.peoplecore.approval.service.ApprovalDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RequestMapping("/approval/document")
@RestController
public class ApprovalDocumentController {

    private final ApprovalDocumentService approvalDocumentService;

    @Autowired
    public ApprovalDocumentController(ApprovalDocumentService approvalDocumentService) {
        this.approvalDocumentService = approvalDocumentService;
    }

    /* 문서 기안 - 문서 생성 + 결재선 + 채번 + 첨부 업로드 (한 요청) */
    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<Long> createDocument(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Name") String empName,
            @RequestHeader("X-User-Department") Long deptId,
            @RequestHeader("X-User-Grade") String empGrade,
            @RequestHeader(value = "X-User-Title", required = false) String empTitle,
            @RequestPart("request") DocumentCreateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        Long docId = approvalDocumentService.createDocument(companyId, empId, empName, deptId, empGrade, empTitle, request, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(docId);
    }

    /* 문서 상세 조회 (열람 시 자동 읽음 처리) */
    @GetMapping("/{docId}")
    public ResponseEntity<DocumentDetailResponse> getDocumentDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId) {
        return ResponseEntity.ok(approvalDocumentService.getDocumentDetail(companyId, empId, docId));
    }

    /* 문서 수정 (임시저장 문서) + 신규 첨부 추가 */
    @PutMapping(value = "/{docId}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<Void> updateDocument(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId,
            @RequestPart("request") DocumentUpdateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        approvalDocumentService.updateDocument(companyId, empId, docId, request, files);
        return ResponseEntity.ok().build();
    }

    /* 임시저장 문서 삭제 (첨부 MinIO+DB 동시 삭제는 Service 에서 처리) */
    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> deleteDocument(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId) {
        approvalDocumentService.deleteDocument(companyId, empId, docId);
        return ResponseEntity.ok().build();
    }

    /* 임시 저장 - Draft + 첨부 업로드 */
    @PostMapping(value = "/temp", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<Long> saveTempDocument(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Name") String empName,
            @RequestHeader("X-User-Department") Long deptId,
            @RequestHeader("X-User-Grade") String empGrade,
            @RequestHeader(value = "X-User-Title", required = false) String empTitle,
            @RequestPart("request") DocumentCreateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        Long docId = approvalDocumentService.saveTempDocument(companyId, empId, empName, deptId, empGrade, empTitle, request, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(docId);
    }

    /* 임시저장 수정 + 신규 첨부 추가 */
    @PutMapping(value = "/temp/{docId}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<Void> updateTempDocument(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId,
            @RequestPart("request") DocumentUpdateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        approvalDocumentService.updateTempDocument(companyId, empId, docId, request, files);
        return ResponseEntity.ok().build();
    }

    /* 임시 저장 -> 결재 요청 전환 (첨부 없음) */
    @PostMapping("/{docId}/submit")
    public ResponseEntity<Void> submitDocument(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Department") Long deptId,
            @PathVariable Long docId) {
        approvalDocumentService.submitDocument(companyId, deptId, empId, docId);
        return ResponseEntity.ok().build();
    }

    /** 반려 문서 재기안 - 새 문서 row INSERT + 신규 추가 첨부 업로드 */
    @PostMapping(value = "/{docId}/resubmit", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<Long> resubmitDocument(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Department") Long deptId,
            @PathVariable Long docId,
            @RequestPart("request") DocumentUpdateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        Long newDocId = approvalDocumentService.resubmitDocument(companyId, empId, deptId, docId, request, files);
        return ResponseEntity.ok(newDocId);
    }

    /** 상신 취소(회수) - PENDING → CANCELED */
    @PostMapping("/{docId}/recall")
    public ResponseEntity<Void> recallDocument(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId) {
        approvalDocumentService.recallDocument(companyId, empId, docId);
        return ResponseEntity.ok().build();
    }

}
