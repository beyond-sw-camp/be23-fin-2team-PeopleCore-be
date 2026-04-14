package com.peoplecore.attendance.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 초과근무 신청
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeRequest extends BaseTimeEntity {

    /**
     * 초과근무신청 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long otId;

    /**
     * 사원 아이디
     */
    @Column(nullable = false)
    private Long empId;

    /**
     * 신청 날짜
     */
    @Column(nullable = false)
    private LocalDateTime otDate;

    /**
     * 초과 근무 시작 예정 시간
     */
    @Column(nullable = false)
    private LocalDateTime otPlanStart;

    /**
     * 초과 근무 종료예정 시간
     */
    @Column(nullable = false)
    private LocalDateTime otPlanEnd;

    /**
     * 초과 근무 실제 시작 시간
     */
    private LocalDateTime otActStart;

    /**
     * 초과 근무 실제 종료 시간
     */
    private LocalDateTime otActEnd;

    /**
     * 초과 근무 사유
     */
    @Column(nullable = false)
    private String otReason;

    /**
     * 초과 근무 신청 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtStatus otStatus;

    /**
     * 처리자 사원 id
     */
    @Column(nullable = false)
    private Long managerId;

    /** TODO :
     * 초과 근무 유형 (비트마스크)
     * bit 0 (1) = 연장근로
     * bit 1 (2) = 야간근로
     * bit 2 (4) = 휴일근로
     * 예: 3 = 연장+야간, 7 = 연장+야간+휴일
     */
    @Column(nullable = false)
    private Integer otTypeFlag;

    /** TODO :
     * 승인된 분리 시간 (분 단위)
     * 근태에서 근무그룹 기준으로 시간대별 자동 분리 계산 후 저장
     * - 야간: 22:00~06:00 구간 (법정 고정)
     * - 연장: 근무그룹 소정근로시간 밖
     * - 휴일: 해당 날짜가 휴일
     */
    @Builder.Default
    private Integer overtimeMinutes = 0;   // 연장근로 시간(분)
    @Builder.Default
    private Integer nightMinutes = 0;      // 야간근로 시간(분)
    @Builder.Default
    private Integer holidayMinutes = 0;    // 휴일근로 시간(분)
}
