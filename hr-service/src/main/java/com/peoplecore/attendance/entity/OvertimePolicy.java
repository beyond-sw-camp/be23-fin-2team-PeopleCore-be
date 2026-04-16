package com.peoplecore.attendance.entity;

import com.peoplecore.attendance.dto.OverTimePolicyReqDto;
import com.peoplecore.company.domain.Company;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
/* 초과 근무 정책 엔티티*/
public class OvertimePolicy {


    /* 초과 근무 정책 Id*/
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long otPolicyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Company company;

    /* 초과 근무 신청 최소 단위*/
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtMinUnit otMinUnit = OtMinUnit.FIFTEEN;

    /* 주간 최대 근무 시간*/
    @Column(nullable = false)
    @Builder.Default
    private Integer otPolicyWeeklyMaxMinutes = 3120;

    /* 주간 근무 경고 시간*/
    @Column(nullable = false)
    @Builder.Default
    private Integer otPolicyWarningMinutes = 2700;

    /* 주간 근로 시간 초과시 처리 방법 ( 알림, 초과근무 신청 자동 차단 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OtExceedAction otExceedAction = OtExceedAction.NOTIFY;

    /* 정책 설정 사원 Id*/
    private Long otPolicyManagerId;

    /*정책 설정 사원 이름*/
    private String otPolicyManagerName;

    public void update(OverTimePolicyReqDto dto, Long empId, String empName) {
        this.otMinUnit = dto.getOtMinUnit();
        this.otPolicyWeeklyMaxMinutes = dto.getOtPolicyWeeklyMaxMinutes();
        this.otPolicyWarningMinutes = dto.getOtPolicyWarningMinutes();
        this.otExceedAction = dto.getOtExceedAction();
        this.otPolicyManagerId = empId;
        this.otPolicyManagerName = empName;
    }
}
