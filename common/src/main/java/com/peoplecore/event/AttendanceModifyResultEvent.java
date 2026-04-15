package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/*
 * 근태 정정 결재 결과 이벤트 (collaboration-service → hr-service).
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AttendanceModifyResultEvent {

    /* 회사 ID */
    private UUID companyId;

    /* collab ApprovalDocument PK — hr-service 가 이 값으로 AttendanceModify 역조회 */
    private Long approvalDocId;

    /* 결재 결과 — "승인" | "반려" | "취소" (ModifyStatus label 과 매칭) */
    private String status;

    /* 최종 처리자 사원 ID — 취소(기안자 회수) 시 null */
    private Long managerId;

    /* 반려 사유 — "반려" 상태에서만 값, 나머지는 null */
    private String rejectReason;
}