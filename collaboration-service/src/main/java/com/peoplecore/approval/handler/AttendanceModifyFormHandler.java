package com.peoplecore.approval.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.dto.docdata.AttendanceModifyDocData;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalLine;
import com.peoplecore.event.AttendanceModifyDocCreatedEvent;
import com.peoplecore.event.AttendanceModifyResultEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class AttendanceModifyFormHandler implements ApprovalFormHandler {

    private static final String FORM_CODE = "ATTENDANCE_MODIFY";
    private static final String TOPIC_DOC_CREATED = "attendance-modify-doc-created";
    private static final String TOPIC_RESULT = "attendance-modify-result";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public AttendanceModifyFormHandler(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(ApprovalDocument document) {
        return FORM_CODE.equals(document.getFormId().getFormCode());
    }

    @Override
    public boolean requiresIdempotencyKey() { return true; }

    @Override
    public void onDocCreated(ApprovalDocument document, List<ApprovalLine> lines, String htmlContent) {
        try {
            AttendanceModifyDocData data = objectMapper.readValue(document.getDocData(), AttendanceModifyDocData.class);
            AttendanceModifyDocCreatedEvent event = AttendanceModifyDocCreatedEvent.builder()
                    .companyId(document.getCompanyId())
                    .approvalDocId(document.getDocId())
                    .empId(document.getEmpId())
                    .comRecId(data.getComRecId())
                    .workDate(data.getWorkDate())
                    .attenReqCheckIn(data.getAttenReqCheckIn())
                    .attenReqCheckOut(data.getAttenReqCheckOut())
                    .attenReason(data.getAttenReason())
                    .build();
            kafkaTemplate.send(TOPIC_DOC_CREATED, objectMapper.writeValueAsString(event));
            log.info("[Kafka] AttendanceModify docCreated 발행 - docId={}, empId={}", document.getDocId(), document.getEmpId());
        } catch (Exception e) {
            log.error("[Kafka] AttendanceModify docCreated 발행 실패 - docId={}, err={}", document.getDocId(), e.getMessage());
        }
    }

    @Override
    public void onResult(ApprovalDocument document, String status, Long managerId, String rejectReason) {
        try {
            AttendanceModifyResultEvent event = AttendanceModifyResultEvent.builder()
                    .companyId(document.getCompanyId())
                    .approvalDocId(document.getDocId())
                    .status(status)
                    .managerId(managerId)
                    .rejectReason(rejectReason)
                    .build();
            kafkaTemplate.send(TOPIC_RESULT, objectMapper.writeValueAsString(event));
            log.info("[Kafka] AttendanceModify result 발행 - docId={}, status={}", document.getDocId(), status);
        } catch (Exception e) {
            log.error("[Kafka] AttendanceModify result 발행 실패 - docId={}, err={}", document.getDocId(), e.getMessage());
        }
    }
}
