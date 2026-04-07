package com.peoplecore.attendence.entity;

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

}
