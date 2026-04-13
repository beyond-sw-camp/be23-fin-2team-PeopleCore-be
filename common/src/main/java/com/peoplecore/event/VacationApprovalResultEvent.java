package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/* 휴가 결재 결과 이벤트 collab -> hr */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class VacationApprovalResultEvent {

    /* 회사 ID */
    private UUID companyId;

    /* VacationReq PK */
    private Long vacReqId;

    /* collab 의 ApprovalDocument PK */
    private Long approvalDocId;

    /* 결재 결과 - APPROVED / REJECTED 문자열 */
    private String status;

    /* 최종 처리자 사원 ID */
    private Long managerId;

    /* 반려 사유 (REJECTED 일 때만) */
    private String rejectReason;
}
