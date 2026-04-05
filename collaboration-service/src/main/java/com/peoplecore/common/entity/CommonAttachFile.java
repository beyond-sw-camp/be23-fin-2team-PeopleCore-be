package com.peoplecore.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CommonAttachFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attachId;

    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID companyId;

    @Column(nullable = false, length = 100)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    /** MinIO에 저장된 실제 오브젝트 키 */
    @Column(nullable = false, length = 255)
    private String storedFileName;

    /** MinIO pre-signed URL (FileService가 주기적으로 갱신) */
    @Column(nullable = false, length = 512)
    private String fileUrl;

    /** bytes 단위 */
    @Column(nullable = false)
    private Long fileSize;

    /** ex) IMAGE, PDF, EXCEL, ETC */
    @Column(nullable = false, length = 50)
    private String fileType;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}