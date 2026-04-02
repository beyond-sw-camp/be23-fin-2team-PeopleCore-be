package com.peoplecore.approval.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * 전자 서명
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalSignature extends BaseTimeEntity {

    /** 전자 서명 Id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sigId;

    /** 회사 Id */
    @Column(nullable = false)
    private UUID companyId;

    /** 사원 Id */
    @Column(nullable = false)
    private Long sigEmpId;

    /** 서명 이미지 경로 */
    @Column(nullable = false)
    private String sigUrl;

    /** 인사 등록자 id */
    private Long sigManagerId;

}
