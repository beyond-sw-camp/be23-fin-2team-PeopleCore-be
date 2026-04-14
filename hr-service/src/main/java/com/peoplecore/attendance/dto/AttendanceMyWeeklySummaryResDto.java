package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/* 사원 개인 주간 근태 요약 응답 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceMyWeeklySummaryResDto {

    /* 오늘자 출퇴근 정보 (출근 전ㅇ면 checkIn 까지 null)*/
    private TodayCommuteDto today;

    /*사원 소속 근무 그룹 기본 정보 + 회사 정책사아 주간 최대 */
    private MyWorkGroupDto workGroup;

    /*주간 집예 */
    private MyWeekStatsDto weekly;

}
