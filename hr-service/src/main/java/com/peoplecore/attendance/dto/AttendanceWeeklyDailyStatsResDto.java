package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 주간현황(/attendance/admin/weekly-stats) 한 행.
 * 해당 주 월~일 중 한 일자에 대한 전사 근태 집계.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceWeeklyDailyStatsResDto {

    /** 집계 대상 날짜 (yyyy-MM-dd) */
    private LocalDate date;

    /** 요일 (MONDAY ~ SUNDAY) — 프론트 라벨("월"/"화"…) 매핑용 */
    private DayOfWeek dayOfWeek;

    /** 해당일 집계 대상 전체 인원 수 (employmentFilter 반영) */
    private int totalEmp;

    /** 정상출근 인원 (NORMAL 카드) */
    private int normal;

    /** 지각 인원 (LATE 카드) */
    private int late;

    /** 조퇴 인원 (EARLY_LEAVE 카드) */
    private int earlyLeave;

    /** 결근 인원 — 소정근무일 && 출근기록 없음 && 당일 승인 휴가 없음 */
    private int absent;

    /** 휴가 인원 — 당일 APPROVED 휴가 보유자 */
    private int onLeave;

    /** 초과근무 발생 인원 — 승인 OT 분 > 0 또는 UNAPPROVED_OT 카드 (사원 단위 중복 없음) */
    private int overtime;

    /** 출근율(%) = (정상 + 지각) / totalEmp * 100, 소수 1자리. totalEmp=0 이면 0.0 */
    private double attendRate;
}
