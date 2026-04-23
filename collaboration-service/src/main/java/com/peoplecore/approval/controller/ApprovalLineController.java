package com.peoplecore.approval.controller;

import com.peoplecore.approval.service.ApprovalLineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/approval/document")
public class ApprovalLineController {
    private final ApprovalLineService approvalLineService;

    @Autowired
    public ApprovalLineController(ApprovalLineService approvalLineService) {
        this.approvalLineService = approvalLineService;
    }

    /**
     * 승인
     */
    @PostMapping("/{docId}/approve")
    public ResponseEntity<Void> approve(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId,
            @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.get("comment") : null;
        approvalLineService.approvalDocument(companyId, empId, docId, comment);
        return ResponseEntity.ok().build();
    }

    /**
     * 반려
     */
    @PostMapping("/{docId}/reject")
    public ResponseEntity<Void> reject(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId,
            @RequestBody Map<String, String> body) {
        String reason = body.get("reason");
        if (reason == null || reason.isBlank()) {
            throw new com.peoplecore.exception.BusinessException("반려 사유는 필수입니다.");
        }
        approvalLineService.rejectDocument(companyId, empId, docId, reason);
        return ResponseEntity.ok().build();
    }

    /**
     * 수신 접수
     */
    @PostMapping("/{docId}/receive")
    public ResponseEntity<Void> receive(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId) {
        approvalLineService.receiveDocument(companyId, empId, docId);
        return ResponseEntity.ok().build();
    }

    /**
     * 열람 확인
     */
    @PostMapping("/{docId}/read")
    public ResponseEntity<Void> read(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId) {
        approvalLineService.readDocument(companyId, empId, docId);
        return ResponseEntity.ok().build();
    }

    /**
     * 참조 확인
     */
    @PostMapping("/{docId}/cc-confirm")
    public ResponseEntity<Void> ccConfirm(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId) {
        approvalLineService.ccConfirm(companyId, empId, docId);
        return ResponseEntity.ok().build();
    }

    /*전결 */
    @PostMapping("/{docId}/all-confirm")
    public ResponseEntity<?> approvalDocumentAll(@RequestHeader("X-User-Company") UUID companyId, @RequestHeader("X-User-Id") Long empId, @PathVariable Long docId,
                                                 @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.get("comment") : null;
        approvalLineService.approvalDocumentAll(companyId, empId, docId, comment);
        return ResponseEntity.ok().build();
    }
}
