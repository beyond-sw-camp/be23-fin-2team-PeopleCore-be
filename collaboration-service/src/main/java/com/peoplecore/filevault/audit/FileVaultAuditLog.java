package com.peoplecore.filevault.audit;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 파일함 감사 로그.
 *
 * <p>모든 폴더/파일 변경(생성·이름변경·이동·삭제·복원·영구삭제·다운로드)을 영구 기록한다.
 * 행위자 스냅샷(이름/직급/empId)과 리소스 스냅샷(이름/부모)을 함께 보관하여
 * 사후 행 삭제·이동에도 감사 추적이 가능하도록 한다.</p>
 *
 * <p>도메인 트랜잭션과 동일한 단일 트랜잭션({@code BEFORE_COMMIT}) 내에서 기록되므로
 * 비즈니스 결과와 감사 로그는 원자적으로 함께 커밋된다.</p>
 */
@Entity
@Table(
    name = "file_vault_audit_log",
    indexes = {
        @Index(name = "idx_audit_company_created", columnList = "company_id,created_at"),
        @Index(name = "idx_audit_actor_created", columnList = "actor_emp_id,created_at"),
        @Index(name = "idx_audit_resource", columnList = "resource_type,resource_id"),
        @Index(name = "idx_audit_action_created", columnList = "action,created_at")
    }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileVaultAuditLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long id;

    /**
     * 회사 id (테넌트 격리).
     */
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /**
     * 행위자 empId. CDC/SYSTEM 인 경우 0.
     */
    @Column(name = "actor_emp_id", nullable = false)
    private Long actorEmpId;

    /**
     * 행위자 이름 스냅샷.
     */
    @Column(name = "actor_name", nullable = false, length = 100)
    private String actorName;

    /**
     * 행위자 직급 id 스냅샷.
     */
    @Column(name = "actor_title_id")
    private Long actorTitleId;

    /**
     * 행위자 출처 ({@link ActorSource}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_source", nullable = false, length = 16)
    private ActorSource actorSource;

    /**
     * 행동 유형 ({@link AuditAction}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private AuditAction action;

    /**
     * 결과 (success / fail). 현재는 success 만 기록.
     */
    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;

    /**
     * 리소스 유형 ({@link ResourceType}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 16)
    private ResourceType resourceType;

    /**
     * 리소스 id (folder_id / file_id).
     */
    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    /**
     * 리소스 이름 스냅샷.
     */
    @Column(name = "resource_name", nullable = false, length = 255)
    private String resourceName;

    /**
     * 부모 폴더 id 스냅샷. null 이면 최상위.
     */
    @Column(name = "parent_folder_id")
    private Long parentFolderId;

    /**
     * 부모 폴더 이름 스냅샷 (위치 표시용).
     */
    @Column(name = "parent_name", length = 255)
    private String parentName;

    /**
     * 변경 내역 JSON (rename: {from, to}, move: {from, to}).
     */
    @Column(name = "changes", columnDefinition = "json")
    private String changes;

    /**
     * 추가 메타데이터 JSON (size, mimeType, downloadUrlExpiresIn 등).
     */
    @Column(name = "metadata", columnDefinition = "json")
    private String metadata;
}
