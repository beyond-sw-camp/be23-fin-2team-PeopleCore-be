package com.peoplecore.approval.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.*;

/**
 * 결재 라인 템플릿
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalLineTemplate extends BaseTimeEntity {

    /**
     * 결재 라인 템플릿 Id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lineTemId;

    /**
     * 회사 Id
     */
    @Column(nullable = false)
    private UUID companyId;

    /**
     * 소유자 Id
     */
    @Column(nullable = false)
    private Long lineTemEmpId;

    /**
     * 템플릿 이름
     */
    @Column(nullable = false)
    private String lineTemName;

    /*기본 결재선 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @OneToMany(mappedBy = "approvalLineTemplateId", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ApprovalLineTemplateList> items = new ArrayList<>();

    public void updateName(String lineTemName) {
        this.lineTemName = lineTemName;
    }

    public void updateDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

}


