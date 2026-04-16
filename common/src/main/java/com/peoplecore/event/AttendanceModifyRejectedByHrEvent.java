package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * hr-service → collaboration-service 역방향 이벤트.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AttendanceModifyRejectedByHrEvent {

    /** 회사 ID */
    private UUID companyId;

    /** 자동 반려할 collab ApprovalDocument PK */
    private Long approvalDocId;

    /** 자동 반려 사유 — 고정 문구 "동일 출퇴근 기록에 대한 정정 신청이 이미 진행 중입니다." */
    private String rejectReason;
}