package com.peoplecore.filevault.permission.controller;

import com.peoplecore.filevault.permission.dto.AdminCapabilityConfigResponse;
import com.peoplecore.filevault.permission.dto.AdminCapabilityUpdateRequest;
import com.peoplecore.filevault.permission.dto.MyAdminCapabilityResponse;
import com.peoplecore.filevault.permission.service.AdminCapabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 파일함 Admin 권한 (Tier-1) API.
 *
 * <ul>
 *   <li>GET /config — 현재 회사 모드 + 부여된 대상 id 목록</li>
 *   <li>PUT /config — 모드 + 대상 id 원자적 교체 (HR 관리자)</li>
 *   <li>GET /me — 내가 파일함 생성 권한이 있는가</li>
 * </ul>
 */
@RestController
@RequestMapping("/filevault/admin-capability")
@RequiredArgsConstructor
public class AdminCapabilityController {

    private final AdminCapabilityService service;

    @GetMapping("/config")
    public ResponseEntity<AdminCapabilityConfigResponse> getConfig(
        @RequestHeader("X-User-Company") UUID companyId
    ) {
        return ResponseEntity.ok(service.getConfig(companyId));
    }

    @PutMapping("/config")
    public ResponseEntity<AdminCapabilityConfigResponse> updateConfig(
        @RequestHeader("X-User-Company") UUID companyId,
        @Valid @RequestBody AdminCapabilityUpdateRequest request
    ) {
        return ResponseEntity.ok(service.updateConfig(companyId, request));
    }

    @GetMapping("/me")
    public ResponseEntity<MyAdminCapabilityResponse> me(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader(value = "X-User-Grade", required = false) Long gradeId,
        @RequestHeader(value = "X-User-Title", required = false) Long titleId
    ) {
        return ResponseEntity.ok(service.me(companyId, gradeId, titleId));
    }
}
