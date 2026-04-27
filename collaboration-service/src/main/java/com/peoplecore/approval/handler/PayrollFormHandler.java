package com.peoplecore.approval.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalLine;
import com.peoplecore.event.PayrollApprovalDocCreatedEvent;
import com.peoplecore.event.PayrollApprovalResultEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class PayrollFormHandler implements ApprovalFormHandler {

    private static final String FORM_NAME = "급여지급결의서";
    private static final String TOPIC_DOC_CREATED = "payroll-approval-doc-created";
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
        return FORM_NAME.equals(document.getFormId().getFormName());
    }

    @Override
    public void onDocCreated(ApprovalDocument document, List<ApprovalLine> lines) {
        try {
            Long payrollRunId = extractPayrollRunId(document);
            PayrollApprovalDocCreatedEvent event = PayrollApprovalDocCreatedEvent.builder()
                    .companyId(document.getCompanyId())
                    .approvalDocId(document.getDocId())
                    .payrollRunId(payrollRunId)
                    .drafterId(document.getEmpId())
                    .finalApproverEmpId(ApprovalFormHandler.findFinalApproverEmpId(lines))
                    .build();
            kafkaTemplate.send(TOPIC_DOC_CREATED, objectMapper.writeValueAsString(event));
            log.info("[Kafka] Payroll docCreated 발행 - docId={}, payrollRunId={}",
                    document.getDocId(), payrollRunId);
        } catch (Exception e) {
            log.error("[Kafka] Payroll docCreated 발행 실패 - docId={}, err={}",
                    document.getDocId(), e.getMessage());
        }
    }

    @Override
    public void onResult(ApprovalDocument document, String status, Long managerId, String rejectReason) {
        try {
            PayrollApprovalResultEvent event = PayrollApprovalResultEvent.builder()
                    .companyId(document.getCompanyId())
                    .payrollRunId(extractPayrollRunId(document))
                    .approvalDocId(document.getDocId())
                    .status(status)
                    .managerId(managerId)
                    .rejectReason(rejectReason)
                    .build();
            kafkaTemplate.send(TOPIC_RESULT, objectMapper.writeValueAsString(event));
            log.info("[Kafka] Payroll result 발행 - docId={}, status={}", document.getDocId(), status);
        } catch (Exception e) {
            log.error("[Kafka] Payroll result 발행 실패 - docId={}, err={}",
                    document.getDocId(), e.getMessage());
        }
    }

    private Long extractPayrollRunId(ApprovalDocument document) {
        try {
            JsonNode tree = objectMapper.readTree(document.getDocData());
            JsonNode n = tree.get("payrollRunId");
            return (n != null && n.isNumber()) ? n.asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
