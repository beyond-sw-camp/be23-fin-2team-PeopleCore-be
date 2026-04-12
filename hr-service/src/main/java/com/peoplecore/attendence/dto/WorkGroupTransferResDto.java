package com.peoplecore.attendence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkGroupTransferResDto {

    /* 이관할 대상들이 속해있는 그룹 Id*/
    private Long sourceWorkGroupId;
    /*이관할 대상의 그룹 Id*/
    private Long targetWorkGroupId;
    /* 이관 철리된 사원 수 */
    private Integer moveCount;

    /* 이관 처리된 사원 Id 목록 */
    private List<WorkGroupMemberResDto> movedMembers;
}
