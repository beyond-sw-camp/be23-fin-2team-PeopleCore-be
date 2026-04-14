package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/* 휴가 결재 문서 생성 이벤트 collab -> hr (docId bind 용) */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class VacationApprovalDocCreatedEvent {

    /* 회사 ID */
    private UUID companyId;

    /* hr 측 VacationReq PK (docData 의 vacReqId) */
    private Long vacReqId;

    /* collab 의 ApprovalDocument PK */
    private Long approvalDocId;
}
