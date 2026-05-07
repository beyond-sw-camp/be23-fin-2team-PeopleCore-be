package com.peoplecore.approval.controller;

import com.peoplecore.approval.dto.ApprovalSignatureResponseDto;
import com.peoplecore.approval.service.ApprovalSignatureService;
import com.peoplecore.auth.RoleRequired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/approval/signatures")
public class ApprovalSignatureController {

    private final ApprovalSignatureService signatureService;

    @Autowired
    public ApprovalSignatureController(ApprovalSignatureService signatureService) {
        this.signatureService = signatureService;
    }

    // ==================== 본인용 ====================

    /** 내 서명 조회 */
    @GetMapping("/me")
    public ResponseEntity<ApprovalSignatureResponseDto> getMySignature(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(signatureService.getSignature(companyId, empId));
    }

    /** 내 서명 등록/수정 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApprovalSignatureResponseDto> createMySignature(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(signatureService.createOrUpdate(companyId, empId, null, file));
    }

    /** 내 서명 삭제 */
    @DeleteMapping
    public ResponseEntity<Void> deleteMySignature(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        signatureService.delete(companyId, empId);
        return ResponseEntity.ok().build();
    }

    // ==================== 관리자용 ====================

    /** 특정 사원 서명 조회 */
    @RoleRequired({"HR_SUPER_ADMIN"})
    @GetMapping("/{empId}")
    public ResponseEntity<ApprovalSignatureResponseDto> getSignature(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId) {
        return ResponseEntity.ok(signatureService.getSignature(companyId, empId));
    }

    /** 특정 사원 서명 대리 등록/수정 */
    @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping(value = "/{empId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApprovalSignatureResponseDto> createSignatureByAdmin(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerId,
            @PathVariable Long empId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(signatureService.createOrUpdate(companyId, empId, managerId, file));
    }

    /** 특정 사원 서명 삭제 */
    @RoleRequired({"HR_SUPER_ADMIN"})
    @DeleteMapping("/{empId}")
    public ResponseEntity<Void> deleteSignatureByAdmin(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId) {
        signatureService.delete(companyId, empId);
        return ResponseEntity.ok().build();
    }

    /** 서명 이미지 파일 프록시 GET (회사 내 인증된 사용자 누구나) */
    @GetMapping("/{empId}/file")
    public ResponseEntity<Resource> downloadSignatureFile(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId) {
        return signatureService.downloadFile(companyId, empId);
    }
}