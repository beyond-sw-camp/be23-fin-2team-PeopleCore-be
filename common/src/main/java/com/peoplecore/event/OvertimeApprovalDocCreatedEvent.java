package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 초과근무 결재 문서 생성 이벤트 collab -> hr.
 * hr 가 이 이벤트로 OvertimeRequest 를 insert (PENDING) — 결재문서 상신 성공이 진짜 신청 시점.
 *
 * docData 에 담겨 있던 사원 신청값 + 결재 메타를 collab Publisher 가 파싱해서 전달.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class OvertimeApprovalDocCreatedEvent {

    /* 회사 ID */
    private UUID companyId;

    /* collab ApprovalDocument PK */
    private Long approvalDocId;

    /* 기안자(신청 사원) ID */
    private Long empId;

    /* 기안자 부서 ID (NOTIFY 알림 대상 탐색용) */
    private Long deptId;

    /* 신청 기준 날짜 (사전 신청 시 미래 가능) */
    private LocalDateTime otDate;

    /* 계획 시작 시각 */
    private LocalDateTime otPlanStart;

    /* 계획 종료 시각 */
    private LocalDateTime otPlanEnd;

    /* 신청 사유 */
    private String otReason;

    /* 최종 결재자 사원 ID (NOTIFY 초과 알림 수신자 중 하나) */
    private Long finalApproverEmpId;
}
