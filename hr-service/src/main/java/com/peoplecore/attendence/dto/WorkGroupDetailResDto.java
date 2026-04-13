package com.peoplecore.attendence.dto;

import com.peoplecore.attendence.entity.WorkGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkGroupDetailResDto {

    /** 근무 그룹 ID */
    private Long workGroupId;

    /** 근무 그룹 명 */
    private String groupName;

    /** 근무 그룹 코드 */
    private String groupCode;

    /** 근무 그룹 설명 */
    private String groupDesc;

    /** 출근 시간 */
    private LocalTime groupStartTime;

    /** 퇴근 시간 */
    private LocalTime groupEndTime;

    /** 근무 요일 - 비트마스크 */
    private Integer groupWorkDay;

    /** 휴게 시작 시간 */
    private LocalTime groupBreakStart;

    /** 휴게 종료 시간 */
    private LocalTime groupBreakEnd;

    /** 초과 근무 인정 방식 */
    private WorkGroup.GroupOvertimeRecognize groupOvertimeRecognize;

    /** 모바일 출퇴근 허용 여부 */
    private Boolean groupMobileCheck;

    /** 근무 그룹 상세 조회 */
    public static WorkGroupDetailResDto from(WorkGroup entity) {
        return WorkGroupDetailResDto.builder()
                .workGroupId(entity.getWorkGroupId())
                .groupName(entity.getGroupName())
                .groupCode(entity.getGroupCode())
                .groupDesc(entity.getGroupDesc())
                .groupStartTime(entity.getGroupStartTime())
                .groupEndTime(entity.getGroupEndTime())
                .groupWorkDay(entity.getGroupWorkDay())
                .groupBreakStart(entity.getGroupBreakStart())
                .groupBreakEnd(entity.getGroupBreakEnd())
                .groupOvertimeRecognize(entity.getGroupOvertimeRecognize())
                .groupMobileCheck(entity.getGroupMobileCheck())
                .build();
    }
}