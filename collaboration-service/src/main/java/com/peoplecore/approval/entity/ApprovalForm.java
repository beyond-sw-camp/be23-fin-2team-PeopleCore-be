package com.peoplecore.approval.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;

import java.util.UUID;

import lombok.*;

/**
 * 결재 양식
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(uniqueConstraints = {@UniqueConstraint(columnNames = {"company_id", "form_code", "is_active"}), @UniqueConstraint(columnNames = {"company_id", "form_name", "is_active"})})
public class ApprovalForm extends BaseTimeEntity {

    /**
     * 결재 양식 id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long formId;

    /**
     * 회사 Id
     */
    @Column(nullable = false)
    private UUID companyId;

    /**
     * 양식 명 — 회사별 unique 는 클래스 레벨 @Table(uniqueConstraints) 로 보장.
     * 컬럼 단위 unique 는 글로벌 unique 가 되어 회사 간 충돌을 일으키므로 사용 X.
     */
    @Column(nullable = false)
    private String formName;

    /**
     * 양식 코드 — 회사별 unique 는 클래스 레벨 @Table(uniqueConstraints) 로 보장.
     */
    @Column(nullable = false)
    private String formCode;

    /**
     * 양식 html - html 템프릿
     */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String formHtml;

    /*
     * 기본 제공 수정 여부 - default == true == 개발자 제공 양식
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isSystem = true;

    /**
     * 양식 버전 - default == 1
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer formVersion = 1;

    /**
     * 현재 버전 여부 - 해당 양식의 활성 버전
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isCurrent = true;

    /**
     * 활성화 여부
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 작성자 - null이면 개발자 제공 버전
     */
    private Long empId;

    /**
     * 작성 권한 - 전체/부서/개인
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormWritePermission formWritePermission;

    /**
     * 공개 여부 - default == true
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean formIsPublic = true;

    /**
     * 보존 연한
     */
    @Column(nullable = false)
    private Integer formRetentionYear;

    /**
     * 전결 옵션
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean formPreApprovalYn = false;

    /**
     * 양식 폴더  id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", nullable = false)
    private ApprovalFormFolder folderId;

    /**
     * 폴더 내 양식 정렬 순서
     */
    @Column(nullable = false)
    private Integer formSortOrder;

    /*
     * 수정/비활성화 보호 양식 여부.
     * 보호 대상 초기 seed 예: 급여지급결의서, 추가근로신청서, 사직서
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isProtected = false;

    /* 양식 내용 수정 */
    public void updateForm(String formName, String formHtml, FormWritePermission formWritePermission,
                           Boolean formIsPublic, Integer formRetentionYear,
                           Boolean formPreApprovalYn) {
        assertNotProtected("양식 내용");
        this.formName = formName;
        this.formHtml = formHtml;
        this.formWritePermission = formWritePermission;
        this.formIsPublic = formIsPublic;
        this.formRetentionYear = formRetentionYear;
        this.formPreApprovalYn = formPreApprovalYn;
        this.formVersion += 1;
    }

    public void updateSortOrder(Integer formSortOrder) {
        this.formSortOrder = formSortOrder;
    }

    public void deactivate() {
        assertNotProtected("비활성화");
        this.isActive = false;
    }

    /* 일괄 설정 수정 */
    public void updateBatchSettings(Boolean formIsPublic, Boolean formPreApprovalYn) {
        assertNotProtected("일괄 설정");
        if (formIsPublic != null) this.formIsPublic = formIsPublic;
        if (formPreApprovalYn != null) this.formPreApprovalYn = formPreApprovalYn;
    }

    /*
     * 시스템 양식 보호 가드 — 공통 예외 메시지.
     */
    private void assertNotProtected(String action) {
        if (Boolean.TRUE.equals(this.isProtected)) {
            throw new IllegalStateException(
                    "보호된 양식은 " + action + " 할 수 없습니다 - formId=" + this.formId
                            + ", formCode=" + this.formCode);
        }
    }
}
