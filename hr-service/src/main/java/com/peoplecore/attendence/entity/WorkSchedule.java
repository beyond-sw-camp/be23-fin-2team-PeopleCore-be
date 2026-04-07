package com.peoplecore.attendence.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;
import lombok.*;

/**
 * 근무 스케쥴
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkSchedule extends BaseTimeEntity {

    /** 근무 스케줄 Id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long scheId;

    /** 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 스케줄명 - 인사과 생성 */
    @Column(nullable = false)
    private String scheName;

    /** 스케줄 코드 - 인사과 생성 */
    @Column(nullable = false)
    private String scheCode;

    /** 기준 출근 시간 - 인사과 생성 */
    @Column(nullable = false)
    private LocalTime scheStandStart;

    /** 기준 퇴근 시간 - 인사과 설정 */
    @Column(nullable = false)
    private LocalTime scheStandEnd;

    /** 기준 근무분 - 인사과 설정. 사원별 설정은 emp_schedule에서 */
    @Column(nullable = false)
    private Integer scheStandMinute;

    /** 유연 근무 여부 - 인사과 설정 defalt ==false */
    @Column(nullable = false)
    private Boolean scheIsFlexible;

    /** 기본 스케줄 여부 - 회사당 하나만 true */
    @Column(nullable = false)
    private Boolean scheIsDefault;

    /** 활성화 여부 - 인사과 관리 default == true */
    @Column(nullable = false)
    private Boolean scheIsActive;

}
