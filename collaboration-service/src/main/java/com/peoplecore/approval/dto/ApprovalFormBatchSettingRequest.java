package com.peoplecore.approval.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalFormBatchSettingRequest {
    private List<Long> formIds;
    /* null이면 변경 안 함 */
    private Boolean formIsPublic;
    private Boolean formMobileYn;
    private Boolean formPreApprovalYn;
}
