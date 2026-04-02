package com.peoplecore.collaboration_service.approval.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * 결재 양식 폴더
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalFormFolder extends BaseTimeEntity {

    /** 결재 양식 폴더  id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long folderId;

    /** 회사 id */
    @Column(nullable = false)
    private UUID folderCompanyId;

    /** 폴더명 */
    @Column(nullable = false)
    private String folderName;

    /** niniIO  경로 */
    @Column(nullable = false)
    private String folderPath;

    /** 폴더  정렬 순서 */
    @Column(nullable = false)
    private Integer folderSortOrder;

    /** 공개 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean folderIsVisible = true;

    /** 등록자 id */
    @Column(nullable = false)
    private Long folderEmpId;

}
