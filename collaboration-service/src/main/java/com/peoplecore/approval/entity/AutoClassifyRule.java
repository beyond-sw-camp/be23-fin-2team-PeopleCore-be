package com.peoplecore.approval.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;

import java.util.UUID;

import lombok.*;

/**
 * 개인 문서함 자동분류 규칙
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoClassifyRule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ruleId;

    /**
     * 회사 ID
     */
    @Column(nullable = false)
    private UUID companyId;

    /* 규칙 소유자 Id*/
    @Column(nullable = false)
    private Long empId;

    /* 소스 문서함 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SourceBoxType sourceBox;

    /**
     * 규칙 이름
     */
    @Column(nullable = false, length = 200)
    private String ruleName;

    /**
     * 조건: 제목 포함
     */
    @Column(length = 200)
    private String titleContains;

    /**
     * 조건: 양식명
     */
    @Column(length = 100)
    private String formName;

    /**
     * 조건: 기안부서
     */
    @Column(length = 100)
    private String drafterDept;

    /**
     * 조건: 기안자
     */
    @Column(length = 50)
    private String drafterName;

    /**
     * 분류 대상 폴더 ID
     */
    @Column(nullable = false)
    private Long targetFolderId;

    /**
     * 활성 여부
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 우선순위 (낮을수록 먼저 적용)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    /**
     * 규칙 수정
     */
    public void update(String ruleName, SourceBoxType sourceBox,
                       String titleContains, String formName,
                       String drafterDept, String drafterName,
                       Long targetFolderId, Boolean isActive) {
        this.ruleName = ruleName;
        this.sourceBox = sourceBox;
        this.titleContains = titleContains;
        this.formName = formName;
        this.drafterDept = drafterDept;
        this.drafterName = drafterName;
        this.targetFolderId = targetFolderId;
        this.isActive = isActive;
    }

    /**
     * 활성/비활성 토글
     */
    public void toggleActive() {
        this.isActive = !this.isActive;
    }

    /**
     * 순서 변경
     */
    public void updateSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
