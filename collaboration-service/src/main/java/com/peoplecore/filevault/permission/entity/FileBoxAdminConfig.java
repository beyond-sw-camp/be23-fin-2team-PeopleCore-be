package com.peoplecore.filevault.permission.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 회사별 "파일함 Admin 권한" 부여 기준 (직급 XOR 직책). 회사당 1행.
 * 권한이 부여된 구체 대상은 {@link FileBoxAdminGrant} 에 저장한다.
 */
@Entity
@Table(
    name = "file_box_admin_config",
    uniqueConstraints = @UniqueConstraint(name = "uk_admin_config_company", columnNames = "company_id")
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileBoxAdminConfig extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileBoxAdminMode mode;

    public void changeMode(FileBoxAdminMode newMode) {
        this.mode = newMode;
    }
}
