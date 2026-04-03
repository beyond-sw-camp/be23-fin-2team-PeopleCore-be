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
            @RequestHeader("X-User-Department") String empDeptName,
            @RequestHeader("X-User-Grade") String empGrade,
            @RequestHeader("X-User-Title") String empTitle,
            @RequestBody DocumentCreateRequest request) {
        Long docId = approvalDocumentService.createDocument(companyId, empId, empName, empDeptName, empGrade, empTitle, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(docId);
    }

    //    문서 상세 조회
    @GetMapping("/{docId}")
    public ResponseEntity<DocumentDetailResponse> getDocumentDetail(@RequestHeader("X-User-Company") UUID companyId, @PathVariable Long docId) {
        return ResponseEntity.ok(approvalDocumentService.getDocumentDetail(companyId, docId));
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
            @RequestHeader("X-User-Department") String empDeptName,
            @RequestHeader("X-User-Grade") String empGrade,
            @RequestHeader("X-User-Title") String empTitle,
            @RequestBody DocumentCreateRequest request) {
        Long docId = approvalDocumentService.saveTempDocument(companyId, empId, empName, empDeptName, empGrade, empTitle, request);
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
            @PathVariable Long docId) {
        approvalDocumentService.submitDocument(companyId, empId, docId);
        return ResponseEntity.ok().build();
    }


}
