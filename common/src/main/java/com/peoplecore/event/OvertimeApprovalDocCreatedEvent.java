package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/* 초과근무 결재 문서 생성 이벤트 collab -> hr (DRAFT -> PENDING 승격 + docId bind 용) */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class OvertimeApprovalDocCreatedEvent {

    /* 회사 ID */
    private UUID companyId;

    /* hr 측 OvertimeRequest PK (프론트가 docData 에 otId 포함 → collab 이 파싱해서 전달) */
    private Long otId;

    /* collab 의 ApprovalDocument PK */
    private Long approvalDocId;
}
