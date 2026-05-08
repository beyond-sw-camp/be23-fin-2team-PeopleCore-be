package com.peoplecore.approval.controller;

import com.peoplecore.approval.dto.AttachmentListResponse;
import com.peoplecore.approval.dto.AttachmentResponse;
import com.peoplecore.approval.service.ApprovalAttachmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RequestMapping("/approval/document")
@RestController
public class ApprovalAttachmentController {

    private final ApprovalAttachmentService attachmentService;

    @Autowired
    public ApprovalAttachmentController(ApprovalAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /** 첨부파일 업로드 (multipart/form-data) */
    @PostMapping(value = "/{docId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<AttachmentResponse>> uploadAttachments(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId,
            @RequestPart("files") List<MultipartFile> files) {
        List<AttachmentResponse> responses = attachmentService.uploadAttachments(companyId, empId, docId, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    /** 첨부파일 목록 조회 */
    @GetMapping("/{docId}/attachments")
    public ResponseEntity<List<AttachmentListResponse>> getAttachments(@PathVariable Long docId) {
        return ResponseEntity.ok(attachmentService.getAttachments(docId));
    }

    /** 첨부파일 다운로드 URL 발급 - 기안자/결재라인 본인만 통과 */
    @GetMapping("/attachments/{attachId}/download")
    public ResponseEntity<String> getDownloadUrl(
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long attachId) {
        return ResponseEntity.ok(attachmentService.getDownloadUrl(empId, attachId));
    }

    /** 첨부파일 프록시 GET (실제 파일 스트림) - 기안자/결재라인 본인만 통과 */
    @GetMapping("/attachments/{attachId}/file")
    public ResponseEntity<Resource> downloadAttachmentFile(
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long attachId) {
        return attachmentService.downloadFile(empId, attachId);
    }

    /** 첨부파일 단건 삭제 */
    @DeleteMapping("/attachments/{attachId}")
    public ResponseEntity<Void> deleteAttachment(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long attachId) {
        attachmentService.deleteAttachment(companyId, empId, attachId);
        return ResponseEntity.ok().build();
    }
}
