package com.peoplecore.collaboration_service.approval.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
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
public class ApprovalForm extends BaseTimeEntity {

    /** 결재 양식 id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long formId;

    /** 회사 Id */
    @Column(nullable = false)
    private UUID companyId;

    /** 양식 명 */
    @Column(nullable = false, unique = true)
    private String formName;

    /** 양식 코드 */
    @Column(nullable = false, unique = true)
    private String formCode;

    /** 양식 html - html 템프릿 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String formHtml;

    /** 기본 제공 수정 여부 - default == true == 개발자 제공 양식 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isSystem = true;

    /** 양식 버전 - default == 1 */
    @Column(nullable = false)
    private Integer formVersion;

    /** 현재 버전 여부 - 해당 양식의 활성 버전 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isCurrent = true;

    /** 활성화 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** 작성자 - null이면 개발자 제공 버전 */
    private Long empId;

    /** 작성 권한 - 전체/부서/개인 */
    @Column(nullable = false)
    private String formWritePermission;

    /** 공개 여부 - default == true */
    @Column(nullable = false)
    @Builder.Default
    private Boolean formIsPublic = true;

    /** 보존 연한 */
    @Column(nullable = false)
    private Integer formRetentionYear;

    /** 모바일 기안 허용 - default == false */
    @Column(nullable = false)
    @Builder.Default
    private Boolean formMobileYn = false;

    /** 전결 옵션 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean formPreApprovalYn = false;

    /** 양식 폴더  id */
    @ManyToOne(fetch = FetchType.LAZY)
    private FormFolder folderId;

    /** miniIO 파일 경로 */
    @Column(nullable = false)
    private String formFilePath;

    /** 폴더 내 양식 정렬 순서 */
    @Column(nullable = false)
    private Integer formSortOrder;

}
