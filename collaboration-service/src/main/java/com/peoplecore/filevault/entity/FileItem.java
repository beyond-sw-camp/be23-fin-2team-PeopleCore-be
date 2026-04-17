package com.peoplecore.filevault.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 파일 메타데이터.
 *
 * <p>실제 바이너리는 MinIO 에 저장되고, DB 에는 {@code storageKey} 등 메타만 보관한다.
 * MinIO 키 규약: {@code c{companyId}/f{folderId}/{uuid}-{filename}}</p>
 *
 * <p>Soft delete: {@code deletedAt != null} → 휴지통. 영구 삭제 시에만 MinIO 객체 제거.</p>
 */
@Entity
@Table(
    name = "file_item",
    indexes = {
        @Index(name = "idx_file_folder", columnList = "folder_id"),
        @Index(name = "idx_file_uploader", columnList = "uploaded_by")
    }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long id;

    /**
     * 소속 폴더 id ({@link FileFolder#getId()}).
     */
    @Column(name = "folder_id", nullable = false)
    private Long folderId;

    /**
     * 파일명 (표시용, 확장자 포함).
     */
    @Column(nullable = false, length = 255)
    private String name;

    /**
     * MIME 타입 (매직바이트 검증 후 확정).
     */
    @Column(name = "mime_type", nullable = false, length = 127)
    private String mimeType;

    /**
     * 파일 크기 (바이트).
     */
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /**
     * MinIO 객체 키 (버킷 내 경로).
     */
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    /**
     * SHA-256 체크섬 (무결성 검증용, optional).
     */
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    /**
     * 업로더 empId.
     */
    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    /**
     * soft delete 시각.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public void rename(String newName) {
        this.name = newName;
    }

    public void moveTo(Long newFolderId) {
        this.folderId = newFolderId;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
