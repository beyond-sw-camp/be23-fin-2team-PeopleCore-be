package com.peoplecore.approval.publisher;

import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalLine;
import com.peoplecore.approval.handler.ApprovalFormHandlerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/*
 * 결재 이벤트 발행 디스패처 — 폼별 발행 로직은 ApprovalFormHandler 구현체로 분산
 *
 * 발행 시점:
 *  - ApprovalDocumentService.createDocument() 직후 → publishDocCreated
 *  - ApprovalLineService 최종 승인 → publishResult(APPROVED)
 *  - ApprovalLineService.rejectDocument() → publishResult(REJECTED)
 *  - ApprovalDocumentService.recallDocument() → publishResult(CANCELED)
 */
@Component
@Slf4j
public class ApprovalEventPublisher {

    private final ApprovalFormHandlerRegistry handlerRegistry;

    @Autowired
    public ApprovalEventPublisher(ApprovalFormHandlerRegistry handlerRegistry) {
        this.handlerRegistry = handlerRegistry;
    }

    /* 문서 기안 직후 — 매칭 핸들러의 onDocCreated 호출 */
    public void publishDocCreated(ApprovalDocument document, List<ApprovalLine> lines) {
        handlerRegistry.find(document)
                .ifPresent(h -> h.onDocCreated(document, lines));
    }

    /* 최종 승인/반려/회수 결과 — 매칭 핸들러의 onResult 호출 */
    public void publishResult(ApprovalDocument document, String status, Long managerId, String rejectReason) {
        handlerRegistry.find(document)
                .ifPresent(h -> h.onResult(document, status, managerId, rejectReason));
    }
}
