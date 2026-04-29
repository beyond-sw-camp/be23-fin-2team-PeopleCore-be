package com.peoplecore.approval.entity;

import com.peoplecore.entity.BaseTimeEntity;
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
@Table(indexes = {
        // 사원·관리자용 폴더 조회 시 회사별 + 삭제·노출 필터링 빠르게
        @Index(name = "idx_form_folder_company_deleted_visible",
                columnList = "folder_company_id, is_deleted, folder_is_visible")
})
public class ApprovalFormFolder extends BaseTimeEntity {

    /**
     * 결재 양식 폴더  id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long folderId;

    /**
     * 회사 id
     */
    @Column(nullable = false)
    private UUID folderCompanyId;

    /**
     * 폴더명
     */
    @Column(nullable = false)
    private String folderName;

    /**
     * 부모 폴더 (null이면 최상위 폴더)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ApprovalFormFolder parent;

    /**
     * miniIO  경로
     */
    @Column(nullable = false)
    private String folderPath;

    /**
     * 폴더  정렬 순서
     */
    @Column(nullable = false)
    private Integer folderSortOrder;

    /**
     * 공개 여부
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean folderIsVisible = true;

    /**
     * 삭제 여부 (soft delete) - true 면 모든 조회에서 영구 제외. 비가역 (복원 불가)
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    /**
     * 등록자 id
     */
    private Long folderEmpId;

    public void updateFolderName(String folderName) {
        this.folderName = folderName;
    }

    public void updateVisibility(Boolean folderIsVisible) {
        this.folderIsVisible = folderIsVisible;
    }

    public void updateSortOrder(Integer folderSortOrder) {
        this.folderSortOrder = folderSortOrder;
    }

    /** 폴더 삭제 (soft) — 비가역. 호출부에서 자식 양식 부재 검증 선행 */
    public void markAsDeleted() {
        this.isDeleted = true;
    }
}
