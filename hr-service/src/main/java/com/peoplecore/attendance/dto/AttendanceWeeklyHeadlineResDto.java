package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/* 근태 현황 집계탭에서 상단 4개 카드 응답 dto
 * 기간은 월~일
 *
 * 전체의 기준이 되는 분모는 해당 주의 각 일별 근무 예정 사원 수 합계 ->근무그룹 비트마스크 기준 (휴가 제외)
 *  지각 -> 출근하기를 찍었지만 근무 그룹의 근무 시작시간 보다 늦은 자
 *  계산식의 소수점 1자리는 반올림
 * */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceWeeklyHeadlineResDto {
    /* 조회 주의 시작일 */
    private LocalDate weekStart;
    /*조회 주의 종료일 */
    private LocalDate weekEnd;

    /* 출근율 (%) = (정상출근 + 지각) / (근무 예정자 * 날짜, 휴가자 제외) * 100 */
    private Double attendanceRate;

    /*지각율 (%) = 지각 / (근무 예정자*날짜,(휴가자 제외) * 100 )*/
    private Double lateRate;

    /*결근 사원 수 (distinct) - 주 중 한번이라고 근무 예정 + 체크인 X + 승인 휴가 없음*/
    private Integer absentCount;

    /*주간 최대근무시간 초과 사원 수(distinct) - 주간 누적분 > 정책 시간*/
    private Integer weeklyMaxExceedCount;
}
