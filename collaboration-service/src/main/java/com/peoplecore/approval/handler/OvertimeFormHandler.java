package com.peoplecore.approval.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.dto.docdata.OvertimeDocData;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalLine;
import com.peoplecore.event.OvertimeApprovalDocCreatedEvent;
import com.peoplecore.event.OvertimeApprovalResultEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class OvertimeFormHandler implements ApprovalFormHandler {

    private static final String FORM_NAME = "초과근로신청서";
    private static final String TOPIC_DOC_CREATED = "overtime-approval-doc-created";
    private static final String TOPIC_RESULT = "overtime-approval-result";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public OvertimeFormHandler(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
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
            OvertimeDocData data = objectMapper.readValue(document.getDocData(), OvertimeDocData.class);
            OvertimeApprovalDocCreatedEvent event = OvertimeApprovalDocCreatedEvent.builder()
                    .companyId(document.getCompanyId())
                    .approvalDocId(document.getDocId())
                    .empId(document.getEmpId())
                    .deptId(document.getEmpDeptId())
                    .otDate(data.getOtDate())
                    .otPlanStart(data.getOtPlanStart())
                    .otPlanEnd(data.getOtPlanEnd())
                    .otReason(data.getOtReason())
                    .finalApproverEmpId(ApprovalFormHandler.findFinalApproverEmpId(lines))
                    .build();
            kafkaTemplate.send(TOPIC_DOC_CREATED, objectMapper.writeValueAsString(event));
            log.info("[Kafka] OT docCreated 발행 - docId={}, empId={}", document.getDocId(), document.getEmpId());
        } catch (Exception e) {
            log.error("[Kafka] OT docCreated 발행 실패 - docId={}, err={}", document.getDocId(), e.getMessage());
        }
    }

    @Override
    public void onResult(ApprovalDocument document, String status, Long managerId, String rejectReason) {
        try {
            OvertimeApprovalResultEvent event = OvertimeApprovalResultEvent.builder()
                    .companyId(document.getCompanyId())
                    .otId(extractOtId(document))
                    .approvalDocId(document.getDocId())
                    .status(status)
                    .managerId(managerId)
                    .rejectReason(rejectReason)
                    .build();
            kafkaTemplate.send(TOPIC_RESULT, objectMapper.writeValueAsString(event));
            log.info("[Kafka] OT result 발행 - docId={}, status={}", document.getDocId(), status);
        } catch (Exception e) {
            log.error("[Kafka] OT result 발행 실패 - docId={}, err={}", document.getDocId(), e.getMessage());
        }
    }

    /* hr 측은 docId 로 찾는 게 주경로 — null 허용 */
    private Long extractOtId(ApprovalDocument document) {
        try {
            JsonNode tree = objectMapper.readTree(document.getDocData());
            JsonNode n = tree.get("otId");
            return (n != null && n.isNumber()) ? n.asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
