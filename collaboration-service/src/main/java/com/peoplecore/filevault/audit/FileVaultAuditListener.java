package com.peoplecore.filevault.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * {@link FileVaultAuditEvent} 를 받아 {@link FileVaultAuditLog} 로 영속하는 리스너.
 *
 * <p>{@link TransactionPhase#BEFORE_COMMIT} 단계에서 동작하므로 도메인 변경과 동일한
 * 트랜잭션·동일한 커넥션을 공유한다 → 비즈니스 결과와 감사 로그가 원자적으로 함께 커밋되거나
 * 함께 롤백된다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileVaultAuditListener {

    private final FileVaultAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(FileVaultAuditEvent event) {
        AuditContext ctx = AuditContextHolder.get();
        if (ctx == null) {
            log.warn("FileVaultAuditEvent dropped — no AuditContext: action={}, resource={}#{}",
                event.getAction(), event.getResourceType(), event.getResourceId());
            return;
        }

        FileVaultAuditLog log = FileVaultAuditLog.builder()
            .companyId(ctx.companyId())
            .actorEmpId(ctx.actorEmpId())
            .actorName(ctx.actorName())
            .actorTitleId(ctx.actorTitleId())
            .actorSource(ctx.actorSource())
            .action(event.getAction())
            .outcome("success")
            .resourceType(event.getResourceType())
            .resourceId(event.getResourceId())
            .resourceName(event.getResourceName())
            .parentFolderId(event.getParentFolderId())
            .parentName(event.getParentName())
            .changes(toJson(event.getChanges()))
            .metadata(toJson(event.getMetadata()))
            .build();

        auditLogRepository.save(log);
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("FileVaultAuditListener: failed to serialize map → {}", map, e);
            return null;
        }
    }
}
