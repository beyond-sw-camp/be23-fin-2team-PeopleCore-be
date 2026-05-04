package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SeveranceApprovalResultEvent { //퇴직금 전자결재 결과

    private UUID companyId;

    private Long approvalDocId;

//    결재 결과 : DRAFT / PENDING / APPROVED / REJECTED / CANCELED
    private String status;

//    최종승인자ID
    private Long managerId;

//    반려 사유
    private String rejectReason;

}
