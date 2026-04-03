package com.peoplecore.approval.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 결재 문서 첨부파일
 * MinIO에 저장된 파일의 메타데이터를 관리
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalAttachment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attachId;

    /** 결재 문서 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_id", nullable = false)
    private ApprovalDocument docId;

    /** 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 원본 파일명 */
    @Column(nullable = false)
    private String fileName;

    /** 파일 크기 (bytes) */
    @Column(nullable = false)
    private Long fileSize;

    /** MinIO 오브젝트 이름 (실제 저장 경로) */
    @Column(nullable = false)
    private String objectName;

    /** 파일 Content-Type */
    @Column(nullable = false)
    private String contentType;
}
