package com.peoplecore.approval.controller;

import com.peoplecore.approval.dto.ApprovalLineTemplateCreateRequest;
import com.peoplecore.approval.dto.ApprovalLineTemplateResponse;
import com.peoplecore.approval.service.ApprovalLineTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/approval/line-templates")
public class ApprovalLineTemplateController {

    private final ApprovalLineTemplateService templateService;

    @Autowired
    public ApprovalLineTemplateController(ApprovalLineTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ResponseEntity<List<ApprovalLineTemplateResponse>> getTemplates(@RequestHeader("X-User-Company") UUID companyId, @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(templateService.getTemplates(companyId, empId));
    }

    @PostMapping
    public ResponseEntity<Long> createTemplate(@RequestHeader("X-User-Company") UUID companyId, @RequestHeader("X-User-Id") Long empId, @RequestBody ApprovalLineTemplateCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createTemplate(companyId, empId, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateTemplate(@RequestHeader("X-User-Company") UUID companyId, @RequestHeader("X-User-Id") Long empId, @PathVariable Long id,
                                               @RequestBody ApprovalLineTemplateCreateRequest request) {
        templateService.updateTemplate(companyId, empId, id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id) {
        templateService.deleteTemplate(companyId, empId, id);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/default")
    public ResponseEntity<ApprovalLineTemplateResponse> getDefaultTemplate(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(templateService.getDefaultTemplate(companyId, empId));
    }

}
