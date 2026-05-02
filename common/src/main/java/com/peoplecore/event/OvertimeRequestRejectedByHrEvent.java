package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * hr-service → collaboration-service 역방향 이벤트.
 * OT 결재 통과 후 hr-service 에서 주간 한도 BLOCK 검증 실패 시 자동 반려용.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class OvertimeRequestRejectedByHrEvent {

    /** 회사 ID */
    private UUID companyId;

    /** 자동 반려할 collab ApprovalDocument PK */
    private Long approvalDocId;

    /** 자동 반려 사유 — "주간 최대 근무시간(XXh)을 초과합니다." */
    private String rejectReason;
}
