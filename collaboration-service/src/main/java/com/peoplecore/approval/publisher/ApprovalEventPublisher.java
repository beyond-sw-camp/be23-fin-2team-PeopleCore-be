package com.peoplecore.approval.publisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.OvertimeApprovalDocCreatedEvent;
import com.peoplecore.event.OvertimeApprovalResultEvent;
import com.peoplecore.event.VacationApprovalDocCreatedEvent;
import com.peoplecore.event.VacationApprovalResultEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 초과근무/휴가 결재 이벤트 Kafka 발행 통합 컴포넌트.
 *
 * 호출 위치:
 *  - ApprovalDocumentService.createDocument() 직후 → publishDocCreated
 *  - ApprovalLineService.approve() 최종 승인 시 → publishResult(APPROVED)
 *  - ApprovalLineService.rejectDocument() → publishResult(REJECTED)
 *
 * formName 매핑:
 *  - "초과근로신청서" → overtime-*
 *  - "휴가원" prefix → vacation-*
 *  - 그 외 (사직서/기타) → 발행 안 함
 */
@Component
@Slf4j
public class ApprovalEventPublisher {

    private static final String FORM_NAME_OVERTIME = "초과근로신청서";
    private static final String FORM_NAME_VACATION_PREFIX = "휴가원";

    private static final String TOPIC_OT_DOC_CREATED = "overtime-approval-doc-created";
    private static final String TOPIC_OT_RESULT = "overtime-approval-result";
    private static final String TOPIC_VAC_DOC_CREATED = "vacation-approval-doc-created";
    private static final String TOPIC_VAC_RESULT = "vacation-approval-result";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ApprovalEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                  ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /** 문서 생성 직후 호출 — docData 에서 otId/vacReqId 파싱해 hr 에 전달 */
    public void publishDocCreated(UUID companyId, Long docId, String formName, String docData) {
        try {
            if (FORM_NAME_OVERTIME.equals(formName)) {
                Long otId = extractLong(docData, "otId");
                if (otId == null) {
                    log.warn("[Kafka] OT docCreated skip - docData 에 otId 없음. docId={}", docId);
                    return;
                }
                OvertimeApprovalDocCreatedEvent event = OvertimeApprovalDocCreatedEvent.builder()
                        .companyId(companyId).otId(otId).approvalDocId(docId).build();
                kafkaTemplate.send(TOPIC_OT_DOC_CREATED, objectMapper.writeValueAsString(event));
                log.info("[Kafka] OT docCreated 발행 - docId={}, otId={}", docId, otId);
            } else if (formName != null && formName.startsWith(FORM_NAME_VACATION_PREFIX)) {
                Long vacReqId = extractLong(docData, "vacReqId");
                if (vacReqId == null) {
                    log.warn("[Kafka] Vacation docCreated skip - docData 에 vacReqId 없음. docId={}", docId);
                    return;
                }
                VacationApprovalDocCreatedEvent event = VacationApprovalDocCreatedEvent.builder()
                        .companyId(companyId).vacReqId(vacReqId).approvalDocId(docId).build();
                kafkaTemplate.send(TOPIC_VAC_DOC_CREATED, objectMapper.writeValueAsString(event));
                log.info("[Kafka] Vacation docCreated 발행 - docId={}, vacReqId={}", docId, vacReqId);
            }
        } catch (Exception e) {
            log.error("[Kafka] docCreated 발행 실패 - docId={}, err={}", docId, e.getMessage());
        }
    }

    /** 최종 승인/반려 시 호출 (APPROVED 또는 REJECTED) */
    public void publishResult(UUID companyId, Long docId, String formName, String docData,
                              String status, Long managerId, String rejectReason) {
        try {
            if (FORM_NAME_OVERTIME.equals(formName)) {
                Long otId = extractLong(docData, "otId");
                if (otId == null) {
                    log.warn("[Kafka] OT result skip - docData 에 otId 없음. docId={}", docId);
                    return;
                }
                OvertimeApprovalResultEvent event = OvertimeApprovalResultEvent.builder()
                        .companyId(companyId).otId(otId).approvalDocId(docId)
                        .status(status).managerId(managerId).rejectReason(rejectReason)
                        .build();
                kafkaTemplate.send(TOPIC_OT_RESULT, objectMapper.writeValueAsString(event));
                log.info("[Kafka] OT result 발행 - docId={}, otId={}, status={}", docId, otId, status);
            } else if (formName != null && formName.startsWith(FORM_NAME_VACATION_PREFIX)) {
                Long vacReqId = extractLong(docData, "vacReqId");
                if (vacReqId == null) {
                    log.warn("[Kafka] Vacation result skip - docData 에 vacReqId 없음. docId={}", docId);
                    return;
                }
                VacationApprovalResultEvent event = VacationApprovalResultEvent.builder()
                        .companyId(companyId).vacReqId(vacReqId).approvalDocId(docId)
                        .status(status).managerId(managerId).rejectReason(rejectReason)
                        .build();
                kafkaTemplate.send(TOPIC_VAC_RESULT, objectMapper.writeValueAsString(event));
                log.info("[Kafka] Vacation result 발행 - docId={}, vacReqId={}, status={}",
                        docId, vacReqId, status);
            }
        } catch (Exception e) {
            log.error("[Kafka] result 발행 실패 - docId={}, err={}", docId, e.getMessage());
        }
    }

    /** docData JSON 문자열에서 Long 필드 안전 추출. 파싱 실패/필드 없음 → null */
    private Long extractLong(String docData, String fieldName) {
        if (docData == null || docData.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(docData).get(fieldName);
            return (node != null && node.isNumber()) ? node.asLong() : null;
        } catch (Exception e) {
            log.warn("[Kafka] docData JSON 파싱 실패 - field={}, err={}", fieldName, e.getMessage());
            return null;
        }
    }
}
