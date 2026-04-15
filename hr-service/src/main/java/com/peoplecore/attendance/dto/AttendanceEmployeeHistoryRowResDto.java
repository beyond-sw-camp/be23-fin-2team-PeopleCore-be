package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.AttendanceCardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 사원 일별 근무 현황 테이블 한 행.
 * 기준: commute_record 1건 = 1행. 기록이 없는 날짜(결근/주말 미출근)는 행에 포함되지 않음.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceEmployeeHistoryRowResDto {

    /** 근무일자 */
    private LocalDate workDate;

    /** 요일 (MONDAY ~ SUNDAY) */
    private DayOfWeek dayOfWeek;

    /** 출근 시각 (기록 있으면 항상 non-null) */
    private LocalDateTime checkInAt;

    /** 퇴근 시각 (미퇴근 상태면 null) */
    private LocalDateTime checkOutAt;

    /** 당일 실근무 분 — checkOut - checkIn (미퇴근이면 null) */
    private Long workMinutes;

    /** 당일 실근무 포맷 "Xh Ym" (미퇴근이면 null) */
    private String workText;

    /** 당일 승인 초과근무 분 (APPROVED overtime_request 의 plan 합). 0 이면 null 로 반환 */
    private Long overtimeMinutes;

    /** 당일 승인 초과근무 포맷 "Xh Ym" (0 이면 null → 프론트 "-" 표시) */
    private String overtimeText;

    /** 당일 판정 카드 리스트 (중복 허용 배열 그대로 노출) */
    private List<AttendanceCardType> attendanceStatuses;
}
