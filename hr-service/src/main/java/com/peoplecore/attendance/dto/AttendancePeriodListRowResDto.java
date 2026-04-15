package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.AttendanceCardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 기간별 리스트(/attendance/admin/period/list) 한 행.
 * 구조는 AttendanceDailyListRowResDto 와 동일하되, 기간 내 여러 일자가 섞이므로
 * 날짜 식별을 위해 workDate 필드를 추가함.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendancePeriodListRowResDto {

    /** 해당 행의 기준 일자 (기간 [start, end] 내 1일) */
    private LocalDate workDate;

    /** 사원 PK */
    private Long empId;

    /** 사번 */
    private String empNum;

    /** 사원명 */
    private String empName;

    /** 부서명 */
    private String deptName;

    /** 근무그룹명 (미배정 사원 호환을 위해 nullable 허용) */
    private String workGroupName;

    /** 출근시각 (미출근 시 null) */
    private LocalDateTime checkInAt;

    /** 퇴근시각 (미퇴근 시 null) */
    private LocalDateTime checkOutAt;

    /** 당일 실근무 분 (출퇴근 둘 다 존재할 때만 계산, 아니면 null) */
    private Long totalWorkMinutes;

    /** 당일 승인된 휴가 유형명 (없으면 null) */
    private String vacationTypeName;

    /** 판정된 카드 타입 리스트 (LATE/EARLY_LEAVE 등 중복 허용) */
    private List<AttendanceCardType> attendanceStatuses;
}
