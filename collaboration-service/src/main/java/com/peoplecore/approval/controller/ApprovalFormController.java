package com.peoplecore.approval.controller;

import com.peoplecore.approval.dto.FormDetailResponse;
import com.peoplecore.approval.dto.FormFolderResponse;
import com.peoplecore.approval.dto.FormListResponse;
import com.peoplecore.approval.service.ApprovalFormService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public ResponseEntity<List<FormFolderResponse>> getFormFolder(@RequestHeader("X-Company") UUID companyId) {
        return ResponseEntity.ok(approvalFormService.getFormFolder(companyId));
    }

    @GetMapping("/form")
    public ResponseEntity<List<FormListResponse>> getForm(@RequestHeader("X-Company") UUID companyId, @RequestParam(required = false) Long folderId) {
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
}
