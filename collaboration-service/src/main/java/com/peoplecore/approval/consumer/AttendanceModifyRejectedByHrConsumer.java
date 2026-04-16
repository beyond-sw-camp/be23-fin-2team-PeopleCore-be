package com.peoplecore.approval.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalLine;
import com.peoplecore.approval.entity.ApprovalLineStatus;
import com.peoplecore.approval.entity.ApprovalStatus;
import com.peoplecore.approval.repository.ApprovalDocumentRepository;
import com.peoplecore.approval.repository.ApprovalLineRepository;
import com.peoplecore.event.AttendanceModifyRejectedByHrEvent;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/* hr-service 가 중복 PENDING 감지 시 발행하는 자동 반려 이벤트 수신.
 * approvalDocId 로 결재 문서를 찾아 반려 처리 */
@Component
@Slf4j
public class AttendanceModifyRejectedByHrConsumer {

    private final ApprovalDocumentRepository documentRepository;
    private final ApprovalLineRepository lineRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public AttendanceModifyRejectedByHrConsumer(ApprovalDocumentRepository documentRepository,
                                                 ApprovalLineRepository lineRepository,
                                                 ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.lineRepository = lineRepository;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2))
    @KafkaListener(topics = "attendance-modify-rejected-by-hr", groupId = "collaboration-service")
    @Transactional
    public void handleRejectedByHr(String message) {
        try {
            AttendanceModifyRejectedByHrEvent event = objectMapper.readValue(message, AttendanceModifyRejectedByHrEvent.class);

            ApprovalDocument document = documentRepository.findByDocIdAndCompanyId(event.getApprovalDocId(), event.getCompanyId())
                    .orElseThrow(() -> new BusinessException("자동 반려 대상 문서를 찾을 수 없습니다."));

            if (document.getApprovalStatus() != ApprovalStatus.PENDING) {
                log.info("[Kafka] 이미 처리된 문서 — 자동 반려 스킵. docId={}", event.getApprovalDocId());
                return;
            }

            document.reject(); // 상태 패턴: PENDING → REJECTED

            /* 모든 PENDING 결재선도 반려 처리 */
            List<ApprovalLine> lines = lineRepository.findByDocId_DocIdOrderByLineStep(document.getDocId());
            lines.stream()
                    .filter(l -> l.getApprovalLineStatus() == ApprovalLineStatus.PENDING)
                    .forEach(l -> l.reject(event.getRejectReason()));

            log.info("[Kafka] 근태정정 자동 반려 완료 - docId={}, reason={}", event.getApprovalDocId(), event.getRejectReason());
        } catch (Exception e) {
            log.error("[Kafka] 근태정정 자동 반려 처리 실패: {}", e.getMessage());
            throw new BusinessException("근태정정 자동 반려 처리 실패");
        }
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("[DLT] 근태정정 자동 반려 최종 실패, message: {}", message);
    }
}