package com.peoplecore.approval.controller;

import com.peoplecore.approval.dto.*;
import com.peoplecore.approval.service.ApprovalDocumentListService;
import com.peoplecore.approval.service.PersonalApprovalFolderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/approval/personal-folder")
public class PersonalApprovalFolderController {

    private final PersonalApprovalFolderService folderService;
    private final ApprovalDocumentListService listService;

    @Autowired
    public PersonalApprovalFolderController(PersonalApprovalFolderService folderService, ApprovalDocumentListService listService) {
        this.folderService = folderService;
        this.listService = listService;
    }

    /*개인 문서함 조회 */
    @GetMapping
    public ResponseEntity<List<PersonalFolderResponse>> getList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(folderService.getList(companyId, empId));
    }

    /** 개인 폴더 문서 조회 */
    @GetMapping("/{id}/documents")
    public ResponseEntity<Page<DocumentListResponseDto>> getFolderDocuments(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id,
            @ModelAttribute DocumentListSearchDto searchDto,
            Pageable pageable) {
        return ResponseEntity.ok(listService.getPersonalFolderDocuments(companyId, empId, id, searchDto, pageable));
    }

    /** 개인 문서함 생성 */
    @PostMapping
    public ResponseEntity<PersonalFolderResponse> create(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody PersonalFolderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(folderService.create(companyId, empId, request));
    }

    /** 개인 문서함 이름 수정 */
    @PutMapping("/{id}")
    public ResponseEntity<PersonalFolderResponse> update(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id,
            @RequestBody PersonalFolderRequest request) {
        return ResponseEntity.ok(folderService.update(companyId, empId, id, request));
    }

    /** 개인 문서함 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id) {
        folderService.delete(companyId, empId, id);
        return ResponseEntity.noContent().build();
    }

    /** 순서 일괄 변경 */
    @PutMapping("/reorder")
    public ResponseEntity<List<PersonalFolderResponse>> reorder(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody PersonalFolderReorderRequest request) {
        return ResponseEntity.ok(folderService.reorder(companyId, empId, request));
    }

    /** 문서함 이관 */
    @PostMapping("/{id}/transfer")
    public ResponseEntity<Void> transfer(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id,
            @RequestBody PersonalFolderTransferRequest request) {
        folderService.transfer(companyId, empId, id, request.getTargetEmpId());
        return ResponseEntity.ok().build();
    }

    /** 문서 개별 이동 */
    @PutMapping("/{id}/move-documents")
    public ResponseEntity<Void> moveDocuments(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id,
            @RequestBody PersonalFolderMoveRequest request) {
        folderService.moveDocuments(companyId, empId, id, request);
        return ResponseEntity.ok().build();
    }

    /** 문서 전체 이동 */
    @PutMapping("/{id}/move-all")
    public ResponseEntity<Void> moveAllDocuments(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id,
            @RequestParam(required = false) Long targetFolderId) {
        folderService.moveAllDocuments(companyId, empId, id, targetFolderId);
        return ResponseEntity.ok().build();
    }
}
