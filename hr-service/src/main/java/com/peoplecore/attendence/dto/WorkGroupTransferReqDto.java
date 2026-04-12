package com.peoplecore.attendence.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkGroupTransferReqDto {

    /* 이관 대상 근무그룹 Id*/
    @NotNull
    private Long targetWorkGroupId;

    /* 이관할 사원 Id 목록 */
    @NotEmpty
    private List<Long> empIds;
}
