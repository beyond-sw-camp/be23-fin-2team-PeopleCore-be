package com.peoplecore.filevault.audit;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 관리자용 감사 로그 조회 응답.
 */
@Getter
@Builder
public class AuditLogResponse {
    private final Long auditId;
    private final Long actorEmpId;
    private final String actorName;
    private final Long actorTitleId;
    private final ActorSource actorSource;
    private final AuditAction action;
    private final String outcome;
    private final ResourceType resourceType;
    private final Long resourceId;
    private final String resourceName;
    private final Long parentFolderId;
    private final String parentName;
    private final String changes;
    private final String metadata;
    private final LocalDateTime createdAt;

    public static AuditLogResponse from(FileVaultAuditLog log) {
        return AuditLogResponse.builder()
            .auditId(log.getId())
            .actorEmpId(log.getActorEmpId())
            .actorName(log.getActorName())
            .actorTitleId(log.getActorTitleId())
            .actorSource(log.getActorSource())
            .action(log.getAction())
            .outcome(log.getOutcome())
            .resourceType(log.getResourceType())
            .resourceId(log.getResourceId())
            .resourceName(log.getResourceName())
            .parentFolderId(log.getParentFolderId())
            .parentName(log.getParentName())
            .changes(log.getChanges())
            .metadata(log.getMetadata())
            .createdAt(log.getCreatedAt())
            .build();
    }
}
