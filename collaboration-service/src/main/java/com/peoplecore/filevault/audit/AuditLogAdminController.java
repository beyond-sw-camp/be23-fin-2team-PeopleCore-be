package com.peoplecore.filevault.audit;

import com.peoplecore.capability.service.CapabilityService;
import com.peoplecore.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 파일함 감사 로그 관리자 전용 검색 엔드포인트.
 *
 * <p>{@code FILE_VIEW_AUDIT_LOG} capability 가 부여된 직급(인사/IT 관리자 등)만 호출 가능.</p>
 */
@RestController
@RequestMapping("/admin/audit/file-vault")
@RequiredArgsConstructor
public class AuditLogAdminController {

    private static final String CAPABILITY = "FILE_VIEW_AUDIT_LOG";
    private static final int MAX_PAGE_SIZE = 200;

    private final FileVaultAuditLogRepository auditLogRepository;
    private final CapabilityService capabilityService;

    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> search(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestParam(required = false) Long actorEmpId,
        @RequestParam(required = false) AuditAction action,
        @RequestParam(required = false) ResourceType resourceType,
        @RequestParam(required = false) Long resourceId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        if (titleId == null || !capabilityService.hasCapability(titleId, CAPABILITY)) {
            throw new BusinessException("감사 로그 조회 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        Pageable pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), MAX_PAGE_SIZE)
        );
        Page<AuditLogResponse> result = auditLogRepository
            .search(companyId, actorEmpId, action, resourceType, resourceId, from, to, pageable)
            .map(AuditLogResponse::from);
        return ResponseEntity.ok(result);
    }
}
