package com.peoplecore.attendence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkGroupResDto {

    /** 근무 그룹 ID */
    private Long workGroupId;

    /** 근무 그룹 명 */
    private String groupName;

    /** 근무 그룹 코드 */
    private String groupCode;

    /** 출근 시간 */
    private LocalTime groupStartTime;

    /** 퇴근 시간 */
    private LocalTime groupEndTime;

    /** 근무 요일 - 비트마스크 */
    private Integer groupWorkDay;

    /** 모바일 출퇴근 허용 여부 */
    private Boolean groupMobileCheck;

    /** 이 그룹에 소속된 사원 수 */
    private Long memberCount;
}