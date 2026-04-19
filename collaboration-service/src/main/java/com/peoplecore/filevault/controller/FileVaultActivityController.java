package com.peoplecore.filevault.controller;

import com.peoplecore.filevault.dto.ActivityResponse;
import com.peoplecore.filevault.service.FileVaultActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 우측 활동 이력 패널 조회 전용 엔드포인트.
 *
 * <p>활동 기록 자체는 더 이상 FE 가 만들지 않는다 — 도메인 서비스가 발행하는
 * {@link com.peoplecore.filevault.audit.FileVaultAuditEvent} 가 자동으로 기록된다.</p>
 */
@RestController
@RequestMapping("/filevault/activities")
@RequiredArgsConstructor
public class FileVaultActivityController {

    private final FileVaultActivityService activityService;

    @GetMapping
    public ResponseEntity<List<ActivityResponse>> list(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader("X-User-Id") Long empId,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(activityService.list(companyId, empId, limit));
    }
}
