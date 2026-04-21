package com.peoplecore.filevault.permission.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 파일함 Admin 권한이 부여된 구체 대상 (직급 id 또는 직책 id).
 * {@code mode} 는 {@link FileBoxAdminConfig#getMode()} 와 일치해야 하며,
 * mode 전환 시 이 테이블의 해당 회사 전체 행이 삭제된다.
 *
 * <p>{@code targetId} 는 mode 에 따라 grade_id / title_id 중 하나를 가리킨다 (polymorphic ref).</p>
 */
@Entity
@Table(
    name = "file_box_admin_grant",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_admin_grant",
        columnNames = {"company_id", "mode", "target_id"}
    ),
    indexes = {
        @Index(name = "idx_admin_grant_company", columnList = "company_id")
    }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileBoxAdminGrant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grant_id")
    private Long id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileBoxAdminMode mode;

    @Column(name = "target_id", nullable = false)
    private Long targetId;
}
