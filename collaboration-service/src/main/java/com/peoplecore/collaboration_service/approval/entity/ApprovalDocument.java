package com.peoplecore.collaboration_service.approval.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.*;

/**
 * 결재 문서
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalDocument extends BaseTimeEntity {

    /**
     * 결재 문서 Id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long docId;

    /**
     * 회사 Id
     */
    @Column(nullable = false)
    private UUID companyId;

    /**
     * 결재 번호 - 제출 시 채번
     */
    @Column(unique = true)
    private String docNum;

    /**
     * 결재 양식 id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private ApprovalForm formId;

    /**
     * 기안자 id - 사원 Id
     */
    @Column(nullable = false)
    private Long empId;

    /**
     * 기안자 이름
     */
    @Column(nullable = false)
    private String empName;

    /**
     * 기안자 부서
     */
    @Column(nullable = false)
    private String empDeptName;

    /**
     * 기안자 직급
     */
    @Column(nullable = false)
    private String empGrade;

    /**
     * 기안자 직책
     */
    @Column(nullable = false)
    private String empTitle;

    /**
     * 양식 유형
     */
    @Column(nullable = false)
    private String docType;

    /**
     * 양식 데이터 - 양식 입력값이 JSON
     */
    @Column(nullable = false, columnDefinition = "json")
    private String docData;

    /**
     * 제목
     */
    @Column(nullable = false)
    private String docTitle;

    /**
     * 문서 상태 - 임시저장,결재중,승인,반려,대기 등등
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    /**
     * 긴급 여부 - default == false
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isEmergency = false;

    /**
     * 제출 일시 - 채번이 새겨지는 시점
     */
    private LocalDateTime docSubmittedAt;

    /**
     * 상태 완료 일시 - 승인/반려 상태 확정 시
     */
    private LocalDateTime docCompleteAt;

}
