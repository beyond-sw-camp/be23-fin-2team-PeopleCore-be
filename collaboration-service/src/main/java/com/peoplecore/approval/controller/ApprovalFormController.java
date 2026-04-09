package com.peoplecore.approval.controller;

import com.peoplecore.approval.dto.*;
import com.peoplecore.approval.service.ApprovalFormService;
import com.peoplecore.auth.RoleRequired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequestMapping("/approval")
@RestController
public class ApprovalFormController {

    private final ApprovalFormService approvalFormService;

    @Autowired
    public ApprovalFormController(ApprovalFormService approvalFormService) {
        this.approvalFormService = approvalFormService;
    }

    @GetMapping("/form-folder")
    public ResponseEntity<List<FormFolderResponse>> getFormFolder(@RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(approvalFormService.getFormFolder(companyId));
    }

    @GetMapping("/form")
    public ResponseEntity<List<FormListResponse>> getForm(@RequestHeader("X-User-Company") UUID companyId, @RequestParam(required = false) Long folderId) {
        return ResponseEntity.ok(approvalFormService.getForms(companyId, folderId));
    }

    @GetMapping("/forms/{formId}")
    public ResponseEntity<FormDetailResponse> getFormDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long formId) {
        return ResponseEntity.ok(approvalFormService.getFormDetail(companyId, formId));
    }

    @GetMapping("/forms/frequent")
    public ResponseEntity<List<FormListResponse>> getFrequentForms(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(approvalFormService.getFrequentForms(companyId, empId));
    }

    @PostMapping("/forms/frequent/{formId}")
    public ResponseEntity<Void> addFrequentForm(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long formId) {
        approvalFormService.addFrequentForm(companyId, empId, formId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/forms/frequent/{formId}")
    public ResponseEntity<Void> removeFrequentForm(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long formId) {
        approvalFormService.removeFrequentForm(companyId, empId, formId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/forms/{formId}/edit")
    public ResponseEntity<FormDetailResponse> getFormDetailForEditing(@RequestHeader("X-User-Company") UUID companyId, @PathVariable Long formId) {
        return ResponseEntity.ok(approvalFormService.getFormDetailEditing(companyId, formId));

    }

    // 관리자용 - 전체 폴더 조회 (숨김 포함)
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/form-folder/all")
    public ResponseEntity<List<FormFolderResponse>> getAllFormFolders(@RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(approvalFormService.getAllFormFolders(companyId));
    }

    // 폴더 추가
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PostMapping("/form-folder")
    public ResponseEntity<FormFolderResponse> createFormFolder(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody ApprovalFormFolderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(approvalFormService.createFormFolder(companyId, empId, request));
    }

    // 폴더 수정
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PutMapping("/form-folder/{folderId}")
    public ResponseEntity<FormFolderResponse> updateFormFolder(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long folderId,
            @RequestBody ApprovalFormFolderUpdateRequest request) {
        return ResponseEntity.ok(approvalFormService.updateFormFolder(companyId, folderId, request));
    }

    // 폴더 삭제
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @DeleteMapping("/form-folder/{folderId}")
    public ResponseEntity<Void> deleteFormFolder(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long folderId) {
        approvalFormService.deleteFormFolder(companyId, folderId);
        return ResponseEntity.noContent().build();
    }

    // 폴더 노출 여부 변경
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PutMapping("/form-folder/{folderId}/visibility")
    public ResponseEntity<FormFolderResponse> updateFolderVisibility(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long folderId,
            @RequestBody ApprovalFormFolderVisibilityRequest request) {
        return ResponseEntity.ok(approvalFormService.updateFolderVisibility(companyId, folderId, request));
    }


    // ========== 양식 관리 (인사 관리자용) ==========

    // 양식 추가
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PostMapping("/forms")
    public ResponseEntity<FormDetailResponse> createForm(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody ApprovalFormCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(approvalFormService.createForm(companyId, empId, request));
    }

    // 양식 수정
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PutMapping("/forms/{formId}")
    public ResponseEntity<FormDetailResponse> updateForm(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long formId,
            @RequestBody ApprovalFormUpdateRequest request) {
        return ResponseEntity.ok(approvalFormService.updateForm(companyId, formId, request));
    }

    // 양식 삭제
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @DeleteMapping("/forms/{formId}")
    public ResponseEntity<Void> deleteForm(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long formId) {
        approvalFormService.deleteForm(companyId, formId);
        return ResponseEntity.noContent().build();
    }

    // 양식 순서 변경
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PutMapping("/forms/reorder")
    public ResponseEntity<List<FormListResponse>> reorderForms(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody ApprovalFormReorderRequest request) {
        return ResponseEntity.ok(approvalFormService.reorderForms(companyId, request));
    }

    // 양식 일괄 설정
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PutMapping("/forms/batch-settings")
    public ResponseEntity<List<FormListResponse>> batchUpdateFormSettings(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody ApprovalFormBatchSettingRequest request) {
        return ResponseEntity.ok(approvalFormService.batchUpdateFormSettings(companyId, request));
    }


    /*기본 양식 추가를 위한 기본 폴더 생성 api*/
    @PostMapping("/init/formfolder")
    public ResponseEntity<?> initFormFolder(@RequestHeader("X-User-Company") UUID companyId) {
        approvalFormService.initFormFolder(companyId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
