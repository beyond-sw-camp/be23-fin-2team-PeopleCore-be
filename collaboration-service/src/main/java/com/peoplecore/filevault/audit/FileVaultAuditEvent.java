package com.peoplecore.filevault.audit;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * 파일함 도메인 이벤트.
 *
 * <p>서비스가 비즈니스 로직 완료 직전 {@code ApplicationEventPublisher} 로 발행하면
 * {@link FileVaultAuditListener} 가 같은 트랜잭션({@code BEFORE_COMMIT}) 내에서
 * {@link FileVaultAuditLog} 로 영속한다.</p>
 *
 * <p>행위자(actor) 정보는 이벤트에 포함하지 않는다 — 리스너가
 * {@link AuditContextHolder} 에서 꺼내 채운다.</p>
 */
@Getter
@Builder
@ToString
public class FileVaultAuditEvent {

    private final AuditAction action;
    private final ResourceType resourceType;
    private final Long resourceId;
    private final String resourceName;

    /**
     * 부모 폴더 id (위치 표시용). 최상위면 null.
     */
    private final Long parentFolderId;

    /**
     * 부모 폴더 이름 스냅샷.
     */
    private final String parentName;

    /**
     * 변경 내역 (e.g. {"from":"old","to":"new"}). null 가능.
     */
    private final Map<String, Object> changes;

    /**
     * 추가 메타데이터 (e.g. size, mimeType). null 가능.
     */
    private final Map<String, Object> metadata;
}
