package com.peoplecore.attendence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkGroupOptionResDto {
    /* 근무 그룹 Id */
    private Long workGroupId;
    /* 근무 그룹 명*/
    private String workGroupName;
    /* 근무 그룹 코드 */
    private String groupCode;
}
