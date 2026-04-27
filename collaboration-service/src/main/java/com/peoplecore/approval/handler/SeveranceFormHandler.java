package com.peoplecore.approval.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalLine;
import com.peoplecore.event.SeveranceApprovalDocCreatedEvent;
import com.peoplecore.event.SeveranceApprovalResultEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class SeveranceFormHandler implements ApprovalFormHandler {

    private static final String FORM_NAME = "퇴직급여지급결의서";
    private static final String TOPIC_DOC_CREATED = "severance-approval-doc-created";
    private static final String TOPIC_RESULT = "severance-approval-result";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public SeveranceFormHandler(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(ApprovalDocument document) {
        return FORM_NAME.equals(document.getFormId().getFormName());
    }

    @Override
    public void onDocCreated(ApprovalDocument document, List<ApprovalLine> lines) {
        try {
            Long sevId = extractSevId(document);
            SeveranceApprovalDocCreatedEvent event = SeveranceApprovalDocCreatedEvent.builder()
                    .companyId(document.getCompanyId())
                    .approvalDocId(document.getDocId())
                    .sevId(sevId)
                    .drafterId(document.getEmpId())
                    .finalApproverEmpId(ApprovalFormHandler.findFinalApproverEmpId(lines))
                    .build();
            kafkaTemplate.send(TOPIC_DOC_CREATED, objectMapper.writeValueAsString(event));
            log.info("[Kafka] Severance docCreated 발행 - docId={}, sevId={}",
                    document.getDocId(), sevId);
        } catch (Exception e) {
            log.error("[Kafka] Severance docCreated 발행 실패 - docId={}, err={}",
                    document.getDocId(), e.getMessage());
        }
    }

    @Override
    public void onResult(ApprovalDocument document, String status, Long managerId, String rejectReason) {
        try {
            SeveranceApprovalResultEvent event = SeveranceApprovalResultEvent.builder()
                    .companyId(document.getCompanyId())
                    .sevId(extractSevId(document))
                    .approvalDocId(document.getDocId())
                    .status(status)
                    .managerId(managerId)
                    .rejectReason(rejectReason)
                    .build();
            kafkaTemplate.send(TOPIC_RESULT, objectMapper.writeValueAsString(event));
            log.info("[Kafka] Severance result 발행 - docId={}, status={}",
                    document.getDocId(), status);
        } catch (Exception e) {
            log.error("[Kafka] Severance result 발행 실패 - docId={}, err={}",
                    document.getDocId(), e.getMessage());
        }
    }

    private Long extractSevId(ApprovalDocument document) {
        try {
            JsonNode tree = objectMapper.readTree(document.getDocData());
            JsonNode n = tree.get("sevId");
            return (n != null && n.isNumber()) ? n.asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
