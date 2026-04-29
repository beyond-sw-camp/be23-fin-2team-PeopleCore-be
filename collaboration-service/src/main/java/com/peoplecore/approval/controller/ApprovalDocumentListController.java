package com.peoplecore.approval.controller;


import com.peoplecore.approval.dto.DocumentCountResponse;
import com.peoplecore.approval.dto.DocumentListResponseDto;
import com.peoplecore.approval.dto.DocumentListSearchDto;
import com.peoplecore.approval.dto.WaitingCountResponse;
import com.peoplecore.approval.service.ApprovalDocumentListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/approval/documents")
public class ApprovalDocumentListController {
    private final ApprovalDocumentListService listService;

    @Autowired
    public ApprovalDocumentListController(ApprovalDocumentListService listService) {
        this.listService = listService;
    }

    /* === 전체 문서함 건수 === */

    @GetMapping("/counts")
    public ResponseEntity<DocumentCountResponse> getDocumentCounts(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Department") Long deptId) {
        return ResponseEntity.ok(listService.getDocumentCounts(companyId, empId, deptId));
    }

    /* === 결재 대기 건수 단건 (배지용) === */

    @GetMapping("/waiting/count")
    public ResponseEntity<WaitingCountResponse> getWaitingCount(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(listService.getWaitingCount(companyId, empId));
    }

    /* === 결재하기 === */

    @GetMapping("/waiting")
    public ResponseEntity<Page<DocumentListResponseDto>> getWaitingDocuments(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @ModelAttribute DocumentListSearchDto searchDto,
            Pageable pageable) {
        return ResponseEntity.ok(listService.getWaitingDocuments(companyId, empId, searchDto, pageable));
    }

    @GetMapping("/cc-view")
    public ResponseEntity<Page<DocumentListResponseDto>> getCcViewDocuments(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @ModelAttribute DocumentListSearchDto searchDto,
            Pageable pageable) {
        return ResponseEntity.ok(listService.getCcViewDocuments(companyId, empId, searchDto, pageable));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<Page<DocumentListResponseDto>> getUpcomingDocuments(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @ModelAttribute DocumentListSearchDto searchDto,
            Pageable pageable) {
        return ResponseEntity.ok(listService.getUpcomingDocuments(companyId, empId, searchDto, pageable));
    }

    @GetMapping("/draft")
    public ResponseEntity<Page<DocumentListResponseDto>> getDraftDocuments(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @ModelAttribute DocumentListSearchDto searchDto,
            Pageable pageable) {
        return ResponseEntity.ok(listService.getDraftDocuments(companyId, empId, searchDto, pageable));
    }

    @GetMapping("/temp")
    public ResponseEntity<Page<DocumentListResponseDto>> getTempDocuments(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @ModelAttribute DocumentListSearchDto searchDto,
            Pageable pageable) {
        return ResponseEntity.ok(listService.getTempDocuments(companyId, empId, searchDto, pageable));
    }

    @GetMapping("/approved")
    public ResponseEntity<Page<DocumentListResponseDto>> getApprovedDocuments(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @ModelAttribute DocumentListSearchDto searchDto,
            Pageable pageable) {
        return ResponseEntity.ok(listService.getApprovedDocuments(companyId, empId, searchDto, pageable));
    }

    @GetMapping("/cc-view-box")
    public ResponseEntity<Page<DocumentListResponseDto>> getCcViewBoxDocuments(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @ModelAttribute DocumentListSearchDto searchDto,
            Pageable pageable) {
        return ResponseEntity.ok(listService.getCcViewBoxDocuments(companyId, empId, searchDto, pageable));
    }

    @GetMapping("/inbox")
    public ResponseEntity<Page<DocumentListResponseDto>> getInboxDocuments(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @ModelAttribute DocumentListSearchDto searchDto,
            Pageable pageable) {
        return ResponseEntity.ok(listService.getInboxDocuments(companyId, empId, searchDto, pageable));
    }

    /* === 부서 문서함 === */

    @GetMapping("/dept")
    public ResponseEntity<Page<DocumentListResponseDto>> getDeptDocuments(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Department") Long deptId,
            @ModelAttribute DocumentListSearchDto searchDto,
            Pageable pageable) {
        return ResponseEntity.ok(listService.getDeptDocuments(companyId, deptId, searchDto, pageable));
    }
}
