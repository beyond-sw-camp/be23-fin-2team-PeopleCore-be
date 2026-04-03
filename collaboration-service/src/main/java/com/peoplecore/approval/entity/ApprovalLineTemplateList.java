package com.peoplecore.approval.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * 결재 라인 템플릿 항목
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalLineTemplateList extends BaseTimeEntity {

    /** 결재 라인 템플릿 항목 id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lineTemListId;

    /** 결재 라인 템플릿 Id */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_tem_id", nullable = false)
    private ApprovalLineTemplate approvalLineTemplateId;

    /** 회사 Id */
    @Column(nullable = false)
    private UUID companyId;

    /** 결재자 Id */
    @Column(nullable = false)
    private Long lineTemListEmpId;

    /** 결재자 역할 - 결재자/열람자/참조자 */
    @Enumerated(EnumType.STRING)
    private ApprovalRole approvalRole;

    /** 처리 순서 */
    @Column(nullable = false)
    private Integer lineTemListStep;

}
