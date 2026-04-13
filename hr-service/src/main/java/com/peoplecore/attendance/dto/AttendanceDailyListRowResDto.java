package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.AttendanceCardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
/*
 * 일자별 사원 테이블 행 응답.

 * 컬럼: 사번, 사원명, 부서명, 근무그룹명, 출근시간, 퇴근시간, 총 근로시간(분), 휴가, 근태이상(배열).
 * totalWorkMinutes 는 퇴근 이전엔 null (근무 진행중).
 * attendanceStatuses 는 AttendanceStatusJudge 판정 결과로 중복 허용 리스트.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceDailyListRowResDto {

    /** 사원 PK */
    private Long empId;

    /** 사번 */
    private String empNum;

    /** 사원명 */
    private String empName;

    /** 부서명 */
    private String deptName;

    /** 근무그룹명 (미배정 시 null) */
    private String workGroupName;

    /** 출근 시각 (미출근 시 null) */
    private LocalDateTime checkInAt;

    /** 퇴근 시각 (미퇴근 시 null) */
    private LocalDateTime checkOutAt;

    /** 총 근로시간 분 (퇴근 전엔 null) */
    private Long totalWorkMinutes;

    /** 오늘 APPROVED 휴가 유형명 (없으면 null) */
    private String vacationTypeName;

    /** 근태이상 배열 (판정된 AttendanceCardType 전부) */
    private List<AttendanceCardType> attendanceStatuses;
}