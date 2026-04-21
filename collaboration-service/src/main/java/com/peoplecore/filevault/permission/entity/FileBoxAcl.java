package com.peoplecore.filevault.permission.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 파일함(루트 폴더) 단위 사원별 ACL.
 *
 * <p>대상 folder 는 반드시 파일함 루트 ({@code parent_folder_id IS NULL} + type != PERSONAL) 여야 한다.
 * Owner (파일함 생성자) 도 이 테이블에 4-플래그 모두 true 인 행을 보유한다 — 감사 일관성.</p>
 */
@Entity
@Table(
    name = "file_box_acl",
    indexes = {
        @Index(name = "idx_acl_folder", columnList = "folder_id"),
        @Index(name = "idx_acl_emp", columnList = "emp_id")
    }
)
@IdClass(FileBoxAclId.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileBoxAcl extends BaseTimeEntity {

    @Id
    @Column(name = "folder_id", nullable = false)
    private Long folderId;

    @Id
    @Column(name = "emp_id", nullable = false)
    private Long empId;

    @Column(name = "can_read", nullable = false)
    private boolean canRead;

    @Column(name = "can_write", nullable = false)
    private boolean canWrite;

    @Column(name = "can_download", nullable = false)
    private boolean canDownload;

    @Column(name = "can_delete", nullable = false)
    private boolean canDelete;

    public void updateFlags(boolean read, boolean write, boolean download, boolean delete) {
        this.canRead = read;
        this.canWrite = write;
        this.canDownload = download;
        this.canDelete = delete;
    }
}
