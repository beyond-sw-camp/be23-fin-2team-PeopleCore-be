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

import java.util.ArrayList;
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
    public void onDocCreated(ApprovalDocument document, List<ApprovalLine> lines, String htmlContent) {
        try {
            List<Long> sevIds = extractSevIds(document);
            SeveranceApprovalDocCreatedEvent event = SeveranceApprovalDocCreatedEvent.builder()
                    .companyId(document.getCompanyId())
                    .approvalDocId(document.getDocId())
                    .sevIds(sevIds)
                    .drafterId(document.getEmpId())
                    .finalApproverEmpId(ApprovalFormHandler.findFinalApproverEmpId(lines))
                    .htmlContent(htmlContent)
                    .build();
            kafkaTemplate.send(TOPIC_DOC_CREATED, objectMapper.writeValueAsString(event));
            log.info("[Kafka] Severance docCreated 발행 - docId={}, sevIds={}",
                    document.getDocId(), sevIds);
        } catch (Exception e) {
            log.error("[Kafka] Severance docCreated 발행 실패 - docId={}", document.getDocId(), e);
        }
    }

    @Override
    public void onResult(ApprovalDocument document, String status, Long managerId, String rejectReason) {
        try {
            SeveranceApprovalResultEvent event = SeveranceApprovalResultEvent.builder()
                    .companyId(document.getCompanyId())
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

    private List<Long> extractSevIds(ApprovalDocument document) {
        try {
            JsonNode tree = objectMapper.readTree(document.getDocData());
            JsonNode arr = tree.get("sevIds");
            if (arr == null || !arr.isArray()) return List.of();
            List<Long> ids = new ArrayList<>(arr.size());
            for (JsonNode n : arr) ids.add(n.asLong());
            return ids;
        } catch (Exception e) {
            log.error("[Severance] docData sevIds 파싱 실패: {}", document.getDocData(), e);
            return List.of();
        }
    }
}
