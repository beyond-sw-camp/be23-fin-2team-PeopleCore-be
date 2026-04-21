package com.peoplecore.filevault.audit;

import java.util.UUID;

/**
 * 감사 로그를 작성할 때 사용할 행위자/회사 컨텍스트.
 *
 * <p>HTTP 요청에서는 {@link AuditContextFilter} 가 헤더에서 추출하여
 * {@link AuditContextHolder} 에 저장하고, CDC 컨슈머에서는 {@link #cdc(UUID)} /
 * {@link #system(UUID)} 팩토리로 만들어 사용한다.</p>
 */
public record AuditContext(
    UUID companyId,
    Long actorEmpId,
    String actorName,
    Long actorTitleId,
    ActorSource actorSource
) {
    public static AuditContext user(UUID companyId, Long empId, String name, Long titleId) {
        return new AuditContext(
            companyId,
            empId != null ? empId : 0L,
            name != null && !name.isBlank() ? name : "unknown",
            titleId,
            ActorSource.USER
        );
    }

    public static AuditContext cdc(UUID companyId) {
        return new AuditContext(companyId, 0L, "system-cdc", null, ActorSource.CDC);
    }

    public static AuditContext system(UUID companyId) {
        return new AuditContext(companyId, 0L, "system", null, ActorSource.SYSTEM);
    }
}
