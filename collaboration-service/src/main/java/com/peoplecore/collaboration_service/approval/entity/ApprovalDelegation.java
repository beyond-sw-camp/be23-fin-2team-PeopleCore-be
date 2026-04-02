package com.peoplecore.collaboration_service.approval.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

/**
 * 결재 위임
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalDelegation extends BaseTimeEntity {

    /** 결재 위임 Id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long appDeleId;

    /** 회사 Id */
    @Column(nullable = false)
    private UUID companyId;

    /** 위임자 Id - 원래 결재자 */
    @Column(nullable = false)
    private Long appEmpId;

    /** 위임 대리자 Id */
    @Column(nullable = false)
    private Long appDeleEmpId;

    /** 위임 시작일 */
    @Column(nullable = false)
    private LocalDate appDeleStartAt;

    /** 위임 종료일 */
    @Column(nullable = false)
    private LocalDate appDeleEndAt;

    /** 활성화 여부 - default == true */
    @Column(nullable = false)
    private Boolean appDeleIsActive;

    /** 위임자 부서 */
    @Column(nullable = false)
    private String appDeleDeptName;

    /** 위임자 직급 */
    @Column(nullable = false)
    private String appDeleGrade;

    /** 위임자 직책 */
    @Column(nullable = false)
    private String appDeleTitle;

}
