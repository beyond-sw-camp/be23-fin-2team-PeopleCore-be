package com.peoplecore.attendence.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

/**
 * 근태(월별 파티션)
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(AttendanceId.class)
public class Attendance extends BaseTimeEntity {

    /** 근태 ID */
    @Id
    private Long attenId;

    /** 근무 일자 - 복합키 */
    @Id
    private LocalDate attenWorkDate;

    /** 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 사원 아이디 */
    @Column(nullable = false)
    private Long empId;

    /** 근무 스케줄 Id */
    @Column(nullable = false)
    private Long scheId;

    /** 근무 유형 - 인사과 등록 */
    @Column(nullable = false)
    private String attenWorkType;

    /** 근무 시간 - default == 0 */
    @Column(nullable = false)
    private Integer attenWorkMinute;

    /** 초과 근무 (분) - default == 0 */
    @Column(nullable = false)
    private Integer attenOverMinute;

    /** 지각 시간 (분) - default ==0 */
    @Column(nullable = false)
    private Integer attenLateMinute;

    /** 조퇴시간 (분) - default==0 */
    @Column(nullable = false)
    private Integer attenLeaveMinute;

    /** 근태 상태 - default 확정/ ex) 확정/수정 */
    @Column(nullable = false)
    private String attenStatus;

}
