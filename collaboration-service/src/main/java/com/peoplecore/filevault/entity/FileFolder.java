package com.peoplecore.filevault.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 파일함·폴더 통합 엔티티.
 *
 * <p>"파일함"과 "하위 폴더"를 같은 테이블로 표현한다. {@code parentFolderId == null}
 * 이면 최상위 파일함(루트), 그 외엔 하위 폴더.</p>
 *
 * <p>시스템 기본 파일함({@code isSystemDefault=true}) — 부서 생성 시 자동 생성되는
 * 디폴트 부서 파일함 등 — 은 삭제·이름변경이 차단된다.</p>
 *
 * <p>Soft delete: {@code deletedAt != null} 이면 휴지통 상태.</p>
 */
@Entity
@Table(
    name = "file_folder",
    indexes = {
        @Index(name = "idx_folder_parent", columnList = "parent_folder_id"),
        @Index(name = "idx_folder_dept", columnList = "dept_id"),
        @Index(name = "idx_folder_owner", columnList = "owner_emp_id"),
        @Index(name = "idx_folder_company_type", columnList = "company_id,type")
    }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileFolder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "folder_id")
    private Long id;

    /**
     * 회사 id (테넌트 격리).
     */
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /**
     * 폴더/파일함 이름.
     */
    @Column(nullable = false, length = 255)
    private String name;

    /**
     * 파일함 타입 ({@link FolderType}).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FolderType type;

    /**
     * 부모 폴더 id. null 이면 최상위 파일함.
     */
    @Column(name = "parent_folder_id")
    private Long parentFolderId;

    /**
     * 개인 파일함 소유자 empId ({@link FolderType#PERSONAL} 에서만 사용).
     */
    @Column(name = "owner_emp_id")
    private Long ownerEmpId;

    /**
     * 부서 id ({@link FolderType#DEPT} 에서만 사용).
     */
    @Column(name = "dept_id")
    private Long deptId;

    /**
     * 시스템 기본 파일함 여부 (디폴트 부서 파일함, 전사 기본 파일함 등).
     * true 면 삭제·이름변경 불가.
     */
    @Column(name = "is_system_default", nullable = false)
    @Builder.Default
    private Boolean isSystemDefault = false;

    /**
     * 생성자 empId.
     */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /**
     * soft delete 시각. null 이면 활성.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 낙관적 락 버전. 동시 rename/move/soft-delete 시 last-writer-wins 를 차단한다.
     * Hibernate가 UPDATE 시 WHERE version=? 조건을 붙여 충돌을 {@link jakarta.persistence.OptimisticLockException} 으로 보고한다.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    public void rename(String newName) {
        if (Boolean.TRUE.equals(this.isSystemDefault)) {
            throw new IllegalStateException("시스템 기본 파일함은 이름을 변경할 수 없습니다.");
        }
        this.name = newName;
    }

    public void moveTo(Long newParentFolderId) {
        if (Boolean.TRUE.equals(this.isSystemDefault)) {
            throw new IllegalStateException("시스템 기본 파일함은 이동할 수 없습니다.");
        }
        this.parentFolderId = newParentFolderId;
    }

    public void softDelete() {
        if (Boolean.TRUE.equals(this.isSystemDefault)) {
            throw new IllegalStateException("시스템 기본 파일함은 삭제할 수 없습니다.");
        }
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
