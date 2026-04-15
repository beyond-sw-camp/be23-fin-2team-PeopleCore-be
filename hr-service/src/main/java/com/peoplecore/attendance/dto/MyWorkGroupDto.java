package com.peoplecore.attendance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/*
 * 사원 근무그룹 정보 + 회사 정책상 주간 최대.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyWorkGroupDto {

    /* 근무그룹 PK — 미배정 사원은 null */
    private Long workGroupId;

    /* 근무그룹명 (예: "기본그룹") */
    private String groupName;

    /* 소정 시업 시각 "HH:mm" */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime groupStartTime;

    /* 소정 종업 시각 "HH:mm" */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime groupEndTime;

    /* 1일 근무 분 (휴게시간 차감 완료) */
    private Integer dailyWorkMinutes;

    /* 주 근무 요일 수 (groupWorkDay 비트마스크 카운트) */
    private Integer weeklyWorkDays;

    /* 주 적정 근무 분 = dailyWorkMinutes × weeklyWorkDays */
    private Integer weeklyWorkMinutes;

    /* 회사 정책상 주간 최대 근무 분 (OvertimePolicy.otPolicyWeeklyMaxMinutes) */
    private Integer companyWeeklyMaxMinutes;
}