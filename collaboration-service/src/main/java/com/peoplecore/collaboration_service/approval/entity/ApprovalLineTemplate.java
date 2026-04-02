package com.peoplecore.collaboration_service.approval.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
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

    /** 결재 라인 템플릿 Id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lineTemId;

    /** 회사 Id */
    @Column(nullable = false)
    private UUID companyId;

    /** 소유자 Id */
    @Column(nullable = false)
    private Long lineTemEmpId;

    /** 템플릿 이름 */
    @Column(nullable = false)
    private String lineTemName;

}


