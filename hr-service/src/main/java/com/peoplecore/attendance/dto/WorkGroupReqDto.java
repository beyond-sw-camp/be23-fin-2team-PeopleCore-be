package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.WorkGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WorkGroupReqDto {
    /** 근무 그룹 명  */
    private String groupName;

    /** 근무 그룹 코드 - 회사 내 unique  */
    private String groupCode;

    /** 근무 그룹 설명 (nullable) */
    private String groupDesc;

    /** 출근 시간 */
    private LocalTime groupStartTime;

    /** 퇴근 시간  */
    private LocalTime groupEndTime;

    /** 근무 요일 - 비트마스크(월1,화2,수4,목8,금16,토32,일64)  */
    private Integer groupWorkDay;

    /** 휴게 시작 시간  */
    private LocalTime groupBreakStart;

    /** 휴게 종료 시간  */
    private LocalTime groupBreakEnd;

    /** 초과 근무 인정 방식 - APPROVAL(결재 승인만), ALL(전체 인정)  */
    private WorkGroup.GroupOvertimeRecognize groupOvertimeRecognize;

}
