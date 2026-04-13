package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;


@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
/*초과 근무 결재 결과 이벤트 collabo -> hr */
public class OvertimeApprovalResultEvent {
    /*회사 iD */
    private UUID companyId;

    /*overTimeReq pk*/
    private Long otId;

    /*collabo 에 approvalDoc Pk */
    private Long approvalDocId;

    /* 결재 결과 -> 승인, 반려 문자열로 전다 */
    private String status;

    /*최종 승인자 id */
    private Long managerId;

    /*반려 사유*/
    private String rejectReason;
}
