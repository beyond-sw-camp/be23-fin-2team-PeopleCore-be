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
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_approval_form_code_version",
                columnNames = {"company_id", "form_code", "form_version"}),
        @UniqueConstraint(name = "uk_approval_form_name_version",
                columnNames = {"company_id", "form_name", "form_version"})
}, indexes = {
        // 회사별 양식 목록 조회 — 가장 빈번한 access 패턴
        @Index(name = "idx_approval_form_company_deleted_current",
                columnList = "company_id, is_deleted, is_current")
})
public class ApprovalForm extends BaseTimeEntity {

    /** 결재 양식 id - 버전마다 새 row 발급 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long formId;

    /** 회사 Id */
    @Column(nullable = false)
    private UUID companyId;

    /** 양식 명 — 같은 회사 내에서는 (form_name, form_version) 조합이 unique */
    @Column(nullable = false)
    private String formName;

    /** 양식 코드 — 같은 회사 내에서는 (form_code, form_version) 조합이 unique. 같은 formCode 의 v1, v2 ... 가 공존 */
    @Column(nullable = false)
    private String formCode;

    /** 양식 html - html 템플릿 (버전별 박제) */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String formHtml;

    /** 기본 제공 수정 여부 - default == true == 개발자 제공 양식 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isSystem = true;

    /** 양식 버전 - 같은 formCode 그룹 내에서 MAX(formVersion)+1 로 신규 row 발급. 롤백 시에는 옛 버전 번호가 다시 current 가 될 수 있음 */
    @Column(nullable = false)
    @Builder.Default
    private Integer formVersion = 1;

    /** 현재 버전 여부 - 같은 formCode 그룹에서 isCurrent=true 는 항상 1행 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isCurrent = true;

    /** 활성화 여부 - false 면 신규 상신/편집 화면에서 노출 X. 옛 버전 row 는 isActive=true 로 살아있음(과거 문서가 본문 참조) */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** 삭제 여부 (soft delete) — true 면 모든 조회에서 영구 제외. 비가역 (복원 불가).
     *  isActive(활성화) 와 별개 — isActive=false 는 "사용 안함" 토글 OFF, isDeleted=true 는 "삭제됨" */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    /** 작성자 - null 이면 개발자 제공 버전 */
    private Long empId;

    /** 작성 권한 - 전체/부서/개인 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormWritePermission formWritePermission;

    /** 공개 여부 - default == true */
    @Column(nullable = false)
    @Builder.Default
    private Boolean formIsPublic = true;

    /** 보존 연한 */
    @Column(nullable = false)
    private Integer formRetentionYear;

    /** 전결 옵션 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean formPreApprovalYn = false;

    /** 양식 폴더 id */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", nullable = false)
    private ApprovalFormFolder folderId;

    /** 폴더 내 양식 정렬 순서 */
    @Column(nullable = false)
    private Integer formSortOrder;

    /**
     * 수정/비활성화 보호 양식 여부.
     * 보호 대상 초기 seed 예: 급여지급결의서, 추가근로신청서, 사직서
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isProtected = false;

    /**
     * 새 버전 row 생성 정적 팩토리.
     * - prev: 복사 베이스가 되는 row (양식 수정 시 = 현재 isCurrent row, 롤백 후 재수정 시에도 현재 row 면 됨)
     * - nextVersion: 같은 formCode 그룹의 MAX(formVersion)+1 — 호출부(서비스)가 repository 로 계산해 넘김
     * 메타(폴더/sortOrder/isSystem/empId/isProtected)는 prev 에서 그대로 복사, 수정 가능 필드만 인자로 교체.
     * 호출부가 prev.markAsObsolete() 도 함께 호출해야 isCurrent 1행 불변식 유지됨.
     */
    public static ApprovalForm nextVersionFrom(ApprovalForm prev,
                                               int nextVersion,
                                               String formName,
                                               String formHtml,
                                               FormWritePermission formWritePermission,
                                               Boolean formIsPublic,
                                               Integer formRetentionYear,
                                               Boolean formPreApprovalYn) {
        prev.assertNotProtected("양식 내용");
        return ApprovalForm.builder()
                .companyId(prev.companyId)
                .formName(formName)
                .formCode(prev.formCode)
                .formHtml(formHtml)
                .isSystem(prev.isSystem)
                .formVersion(nextVersion)
                .isCurrent(true)
                .isActive(true)
                .empId(prev.empId)
                .formWritePermission(formWritePermission)
                .formIsPublic(formIsPublic)
                .formRetentionYear(formRetentionYear)
                .formPreApprovalYn(formPreApprovalYn)
                .folderId(prev.folderId)
                .formSortOrder(prev.formSortOrder)
                .isProtected(prev.isProtected)
                .build();
    }

    /** 롤백 — 옛 버전 row 를 다시 current 로 (서비스가 기존 current 의 markAsObsolete 와 함께 호출). updatedAt 자동 갱신되어 활성 시점 추적 가능 */
    public void becomeCurrent() {
        this.isCurrent = true;
    }

    /** 새 버전 등장 또는 다른 버전이 current 가 될 때 — isActive 는 건드리지 않음 */
    public void markAsObsolete() {
        this.isCurrent = false;
    }

    public void updateSortOrder(Integer formSortOrder) {
        this.formSortOrder = formSortOrder;
    }

    public void deactivate() {
        assertNotProtected("비활성화");
        this.isActive = false;
    }

    /** 양식 삭제 (soft) — 비가역. 보호 양식은 삭제 불가.
     *  서비스 호출부에서 같은 formCode 의 모든 버전을 일괄 markAsDeleted 처리해야 함 */
    public void markAsDeleted() {
        assertNotProtected("삭제");
        this.isDeleted = true;
    }

    /** 일괄 설정 수정 — 메타 변경이라 새 버전 X, 현재 row 만 업데이트 */
    public void updateBatchSettings(Boolean formIsPublic, Boolean formPreApprovalYn) {
        assertNotProtected("일괄 설정");
        if (formIsPublic != null) this.formIsPublic = formIsPublic;
        if (formPreApprovalYn != null) this.formPreApprovalYn = formPreApprovalYn;
    }

    /** 시스템 양식 보호 가드 — 공통 예외 메시지 */
    private void assertNotProtected(String action) {
        if (Boolean.TRUE.equals(this.isProtected)) {
            throw new IllegalStateException(
                    "보호된 양식은 " + action + " 할 수 없습니다 - formId=" + this.formId
                            + ", formCode=" + this.formCode);
        }
    }
}
