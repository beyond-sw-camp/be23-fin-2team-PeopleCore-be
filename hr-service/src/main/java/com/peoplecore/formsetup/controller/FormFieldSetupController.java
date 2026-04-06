package com.peoplecore.formsetup.controller;

import com.peoplecore.formsetup.domain.FormType;
import com.peoplecore.formsetup.dto.FormFieldSetupRequest;
import com.peoplecore.formsetup.dto.FormFieldSetupResponse;
import com.peoplecore.formsetup.service.FormFieldSetupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/form-setup")
public class FormFieldSetupController {

    private final FormFieldSetupService service;

    public FormFieldSetupController(FormFieldSetupService service) {
        this.service = service;
    }

    // 1. 폼 설정 조회 (없으면 기본값 자동 생성)
    @GetMapping("/{formType}")
    public ResponseEntity<List<FormFieldSetupResponse>> getSetup(
            @RequestHeader("X-User-Company") String companyId,
            @PathVariable FormType formType) {
        return ResponseEntity.ok(service.getSetup(UUID.fromString(companyId), formType));
    }

    // 2. 폼 설정 일괄 저장
    @PutMapping("/{formType}")
    public ResponseEntity<List<FormFieldSetupResponse>> saveSetup(
            @RequestHeader("X-User-Company") String companyId,
            @PathVariable FormType formType,
            @RequestBody @Valid List<FormFieldSetupRequest> requests) {
        return ResponseEntity.ok(service.saveSetup(UUID.fromString(companyId), formType, requests));
    }

    // 3. 기본값으로 초기화
    @PostMapping("/{formType}/reset")
    public ResponseEntity<List<FormFieldSetupResponse>> resetSetup(
            @RequestHeader("X-User-Company") String companyId,
            @PathVariable FormType formType) {
        return ResponseEntity.ok(service.resetSetup(UUID.fromString(companyId), formType));
    }
}
