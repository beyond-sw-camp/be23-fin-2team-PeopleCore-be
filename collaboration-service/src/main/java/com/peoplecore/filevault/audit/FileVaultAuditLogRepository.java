package com.peoplecore.filevault.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface FileVaultAuditLogRepository extends JpaRepository<FileVaultAuditLog, Long> {

    /**
     * 회사 단위 최근 감사 로그.
     */
    List<FileVaultAuditLog> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);

    /**
     * 관리자 검색 (필터 모두 optional). null 인 필드는 무시.
     */
    @Query("""
        SELECT a FROM FileVaultAuditLog a
        WHERE a.companyId = :companyId
          AND (:actorEmpId IS NULL OR a.actorEmpId = :actorEmpId)
          AND (:action IS NULL OR a.action = :action)
          AND (:resourceType IS NULL OR a.resourceType = :resourceType)
          AND (:resourceId IS NULL OR a.resourceId = :resourceId)
          AND (:from IS NULL OR a.createdAt >= :from)
          AND (:to IS NULL OR a.createdAt < :to)
        ORDER BY a.createdAt DESC
        """)
    Page<FileVaultAuditLog> search(
        @Param("companyId") UUID companyId,
        @Param("actorEmpId") Long actorEmpId,
        @Param("action") AuditAction action,
        @Param("resourceType") ResourceType resourceType,
        @Param("resourceId") Long resourceId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable
    );
}
