package com.peoplecore.collaboration_service.common.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * 공통 첨부 파일
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommonAttachment extends BaseTimeEntity {

    /** 공통 첨부파일 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attachId;

    /** 회사 Id */
    @Column(nullable = false)
    private UUID companyId;

    /** 대상 구분 - 어느 파트에서 올린 첨부 파일인지 */
    @Column(nullable = false)
    private String attachEntityType;

    /** 대상 Id - 대상 테이블의 pk id */
    @Column(nullable = false)
    private Long attachEntityId;

    /** 원본 파일명 */
    @Column(nullable = false)
    private String attachFileName;

    /** 저장 파일 명 - S3 저장 이름 */
    @Column(nullable = false)
    private String attachStoreName;

    /** 파일 경로 */
    @Column(nullable = false)
    private String attachFileUrl;

    /** 파일 사이즈 - bytesㄹ 저장 */
    @Column(nullable = false)
    private Long attachFileSize;

    /** 파일 유형 - pdf인지 image 인지  등등 */
    @Column(nullable = false)
    private String attachFileType;

}
