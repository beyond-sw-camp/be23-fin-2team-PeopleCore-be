package com.peoplecore.approval.handler;

import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalLine;
import com.peoplecore.approval.entity.ApprovalRole;

import java.util.Comparator;
import java.util.List;

/* 폼별 결재 라이프사이클 후처리 전략. 모든 콜백은 default noop — 필요한 콜백만 override */
public interface ApprovalFormHandler {

    /* 자기 폼 매칭 — formCode/formName/prefix 등 핸들러가 자유롭게 판단 */
    boolean supports(ApprovalDocument document);

    /* 문서 기안 직후 — Kafka docCreated 발행 등 */
    default void onDocCreated(ApprovalDocument document, List<ApprovalLine> lines) {}

    /* 최종 승인/반려/회수 결과 — Kafka result 발행 */
    default void onResult(ApprovalDocument document, String status, Long managerId, String rejectReason) {}

    /* 최종 승인 후처리 — 캘린더 이벤트, 사직 처리 등 */
    default void onApproved(ApprovalDocument document) {}

    /* 멱등키 검증 필요 폼 (근태정정/휴가부여 등) */
    default boolean requiresIdempotencyKey() { return false; }

    /* 결재선 중 마지막 APPROVER empId — onDocCreated 공통 유틸 */
    static Long findFinalApproverEmpId(List<ApprovalLine> lines) {
        if (lines == null || lines.isEmpty()) return null;
        return lines.stream()
                .filter(l -> l.getApprovalRole() == ApprovalRole.APPROVER)
                .max(Comparator.comparingInt(ApprovalLine::getLineStep))
                .map(ApprovalLine::getEmpId)
                .orElse(null);
    }
}
