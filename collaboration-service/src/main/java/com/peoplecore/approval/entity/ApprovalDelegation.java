package com.peoplecore.approval.entity;

import com.peoplecore.entity.BaseTimeEntity;
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
    private Long empId;

    /** 위임자 부서 */
    @Column(nullable = false)
    private String empDeptName;

    /** 위임자 직급 */
    @Column(nullable = false)
    private String empGrade;

    /** 위임자 직책 */
    @Column(nullable = false)
    private String empTitle;

    /** 위임자 이름 */
    @Column(nullable = false)
    private String empName;

    /** 위임 대리자 Id */
    @Column(nullable = false)
    private Long deleEmpId;

    /** 위임 시작일 */
    @Column(nullable = false)
    private LocalDate startAt;

    /** 위임 종료일 */
    @Column(nullable = false)
    private LocalDate endAt;

    /** 활성화 여부 - default == true */
    @Column(nullable = false)
    private Boolean isActive;

    /** 위임자 부서 */
    @Column(nullable = false)
    private String deleDeptName;

    /** 위임자 직급 */
    @Column(nullable = false)
    private String deleGrade;

    /** 위임자 직책 */
    @Column(nullable = false)
    private String deleTitle;

    /** 위임자 이름 */
    @Column(nullable = false)
    private String deleName;

}
