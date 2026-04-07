package com.peoplecore.vacation.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.*;

/**
 * 연차 정책
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationPolicy extends BaseTimeEntity {

    public enum PolicyBaseType {
        HIRE,
        FISCAL;
    }

    /**
     * 연차 정책 Id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long policyId;

    /**
     * 회사 ID
     */
    @Column(nullable = false)
    private UUID companyId;

    /**
     * 권한 정책 생성 사원 id
     */
    @Column(nullable = false)
    private Long policyEmpId;


    /**
     * 연차 지급 기준
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyBaseType policyBaseType;

    /**
     * 회계연도 시작일 - mm-dd
     */
    private String policyFiscalYearStart;

    /**
     * 연차 촉진 알람 잔여일
     */
    @Column(nullable = false)
    private String policyAlarmDay;

    /**
     * 초과 근무 신청 단위
     */
    @Column(nullable = false)
    private Integer policyOvertimeMin;

    /**
     * 초과 근무 사전 결재
     */
    @Column(nullable = false)
    private Boolean policyOvertimeBeforeApproval;

    /**
     * 초과 근무 사후 결재
     */
    @Column(nullable = false)
    private Boolean policyOvertimeAfterApproval;

    /** 연차 발생 규칙 목록 (양방향) */
    @OneToMany(mappedBy = "vacationPolicy", cascade = CascadeType.ALL)
    @Builder.Default
    private List<VacationCreateRule> createRules = new ArrayList<>();

    public void changeGrantBasis(PolicyBaseType newBasis) {
        this.policyBaseType = newBasis;
    }

}
