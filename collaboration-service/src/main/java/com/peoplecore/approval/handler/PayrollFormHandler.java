package com.peoplecore.approval.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.event.PayrollApprovalResultEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PayrollFormHandler implements ApprovalFormHandler {

    private static final String FORM_NAME_PREFIX = "급여지급결의서";
    private static final String TOPIC_RESULT = "payroll-approval-result";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public PayrollFormHandler(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(ApprovalDocument document) {
        String formName = document.getFormId().getFormName();
        return formName != null && formName.startsWith(FORM_NAME_PREFIX);
    }

    @Override
    public void onResult(ApprovalDocument document, String status, Long managerId, String rejectReason) {
        try {
            Long payrollRunId = parseLong(document.getDocData(), "payrollRunId");
            PayrollApprovalResultEvent event = PayrollApprovalResultEvent.builder()
                    .companyId(document.getCompanyId())
                    .payrollRunId(payrollRunId)
                    .approvalDocId(document.getDocId())
                    .status(status)
                    .managerId(managerId)
                    .rejectReason(rejectReason)
                    .build();
            kafkaTemplate.send(TOPIC_RESULT, objectMapper.writeValueAsString(event));
            log.info("[Kafka] Payroll result 발행 - docId={}, status={}", document.getDocId(), status);
        } catch (Exception e) {
            log.error("[Kafka] Payroll result 발행 실패 - docId={}, err={}", document.getDocId(), e.getMessage());
        }
    }

    private Long parseLong(String docData, String field) {
        if (docData == null || docData.isBlank()) return null;
        try {
            JsonNode n = objectMapper.readTree(docData).get(field);
            return (n != null && n.isNumber()) ? n.asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
