package com.peoplecore.approval.controller;

import com.peoplecore.approval.dto.DocumentCreateRequest;
import com.peoplecore.approval.dto.DocumentDetailResponse;
import com.peoplecore.approval.dto.DocumentUpdateRequest;
import com.peoplecore.approval.service.ApprovalDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequestMapping("/approval/document")
@RestController
public class ApprovalDocumentController {

    private final ApprovalDocumentService approvalDocumentService;

    @Autowired
    public ApprovalDocumentController(ApprovalDocumentService approvalDocumentService) {
        this.approvalDocumentService = approvalDocumentService;
    }

    /*문서 기안 결재 요청- 문서 생성 + 결재선 + 채번 */
    @PostMapping
    public ResponseEntity<Long> createDocument(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Name") String empName,
            @RequestHeader("X-User-Department") Long deptId,
            @RequestHeader("X-User-Grade") String empGrade,
            @RequestHeader(value = "X-User-Title", required = false) String empTitle,
            @RequestBody DocumentCreateRequest request) {
        Long docId = approvalDocumentService.createDocument(companyId, empId, empName, deptId, empGrade, empTitle, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(docId);
    }

    //    문서 상세 조회 (열람 시 자동 읽음 처리)
    @GetMapping("/{docId}")
    public ResponseEntity<DocumentDetailResponse> getDocumentDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId) {
        return ResponseEntity.ok(approvalDocumentService.getDocumentDetail(companyId, empId, docId));
    }

    /*문서 수정*/
    @PutMapping("/{docId}")
    public ResponseEntity<Void> updateDocument(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId,
            @RequestBody DocumentUpdateRequest request
    ) {
        approvalDocumentService.updateDocument(companyId, empId, docId, request);
        return ResponseEntity.ok().build();
    }

    /*임시저장 문서 삭제*/
    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> deleteDocument(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId) {
        approvalDocumentService.deleteDocument(companyId, empId, docId);
        return ResponseEntity.ok().build();
    }

    /*임시 저장 */
    @PostMapping("/temp")
    public ResponseEntity<Long> saveTempDocument(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Name") String empName,
            @RequestHeader("X-User-Department") Long deptId,
            @RequestHeader("X-User-Grade") String empGrade,
            @RequestHeader(value = "X-User-Title", required = false) String empTitle,
            @RequestBody DocumentCreateRequest request) {
        Long docId = approvalDocumentService.saveTempDocument(companyId, empId, empName, deptId, empGrade, empTitle, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(docId);
    }

    /* 임시저장 수정 */
    @PutMapping("/temp/{docId}")
    public ResponseEntity<Void> updateTempDocument(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId,
            @RequestBody DocumentUpdateRequest request) {
        approvalDocumentService.updateTempDocument(companyId, empId, docId, request);
        return ResponseEntity.ok().build();
    }

    /* 임시저장  -> 결재 요청 전환  */
    @PostMapping("/{docId}/submit")
    public ResponseEntity<Void> submitDocument(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Department") Long deptId,
            @PathVariable Long docId) {
        approvalDocumentService.submitDocument(companyId, deptId, empId, docId);
        return ResponseEntity.ok().build();
    }

    /** 반려 문서 재기안 - 새 문서 row INSERT (새 docId 반환, 이전 문서는 REJECTED 로 보존) */
    @PostMapping("/{docId}/resubmit")
    public ResponseEntity<Long> resubmitDocument(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Department") Long deptId,
            @PathVariable Long docId,
            @RequestBody DocumentUpdateRequest request) {
        Long newDocId = approvalDocumentService.resubmitDocument(companyId, empId, deptId, docId, request);
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
