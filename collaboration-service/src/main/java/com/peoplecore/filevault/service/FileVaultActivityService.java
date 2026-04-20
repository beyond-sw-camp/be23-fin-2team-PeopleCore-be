package com.peoplecore.filevault.service;

import com.peoplecore.filevault.audit.AuditAction;
import com.peoplecore.filevault.audit.FileVaultAuditLog;
import com.peoplecore.filevault.audit.FileVaultAuditLogRepository;
import com.peoplecore.filevault.dto.ActivityResponse;
import com.peoplecore.filevault.entity.ActivityAction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 우측 활동 이력 패널 조회 서비스.
 *
 * <p>내부적으로는 새로운 {@link FileVaultAuditLog} 테이블을 읽고,
 * FE 호환을 위해 더 거친 단위인 {@link ActivityAction} 으로 묶어서 반환한다.
 * MOVE_* 처럼 매핑할 ActivityAction 이 없는 행은 응답에서 제외된다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileVaultActivityService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final FileVaultAuditLogRepository auditLogRepository;

    public List<ActivityResponse> list(UUID companyId, Long empId, Integer limit) {
        int size = clampLimit(limit);
        return auditLogRepository
            .findByCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(0, size * 2))
            .stream()
            .map(this::toActivity)
            .filter(java.util.Objects::nonNull)
            .limit(size)
            .toList();
    }

    private ActivityResponse toActivity(FileVaultAuditLog log) {
        ActivityAction mapped = mapAction(log.getAction());
        if (mapped == null) return null;
        return ActivityResponse.builder()
            .id(log.getId())
            .action(mapped)
            .targetName(log.getResourceName())
            .location(log.getParentName() != null ? log.getParentName() : "(루트)")
            .userName(log.getActorName())
            .createdAt(log.getCreatedAt())
            .build();
    }

    private ActivityAction mapAction(AuditAction action) {
        return switch (action) {
            case CREATE_FOLDER -> ActivityAction.CREATE_FOLDER;
            case SOFT_DELETE_FOLDER -> ActivityAction.DELETE_FOLDER;
            case PERMANENT_DELETE_FOLDER, PERMANENT_DELETE_FILE -> ActivityAction.PERMANENT_DELETE;
            case RENAME_FOLDER, RENAME_FILE -> ActivityAction.RENAME;
            case UPLOAD_FILE -> ActivityAction.UPLOAD;
            case SOFT_DELETE_FILE -> ActivityAction.DELETE;
            case DOWNLOAD_FILE -> ActivityAction.DOWNLOAD;
            case RESTORE_FOLDER, RESTORE_FILE -> ActivityAction.RESTORE;
            case MOVE_FOLDER, MOVE_FILE -> null;
        };
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }
}
