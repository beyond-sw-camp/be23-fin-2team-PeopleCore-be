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

    /*사전 결재 필요 여부 */
    @Builder.Default
    @Column(nullable = false)
    private Boolean otPolicyBefore = true;

    /*사후 결재 필요 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean otPolicyAfter = false;

    /* 주간 최대 근무 시간*/
    @Column(nullable = false)
    @Builder.Default
    private Integer otPolicyWeeklyMaxHour = 52;

    /* 주간 근무 경고 시간*/
    @Column(nullable = false)
    @Builder.Default
    private Integer otPolicyWarningHour = 45;

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
        this.otPolicyBefore = dto.getOtPolicyBefore();
        this.otPolicyAfter = dto.getOtPolicyAfter();
        this.otPolicyWeeklyMaxHour = dto.getOtPolicyWeeklyMaxHour();
        this.otPolicyWarningHour = dto.getOtPolicyWarningHour();
        this.otExceedAction = dto.getOtExceedAction();
        this.otPolicyManagerId = empId;
        this.otPolicyManagerName = empName;
    }
}
