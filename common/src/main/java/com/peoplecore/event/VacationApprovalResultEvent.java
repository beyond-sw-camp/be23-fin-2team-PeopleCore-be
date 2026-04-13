package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/*휴가 결재 결과 이벤트 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class VacationApprovalResultEvent {

    private UUID companyId;
    /*vacReq pk ㄴ*/
    private Long vacReqId;

    private Long approvalId;

    private String status;

    private Long managerId;

    private String rejectReason;
}
