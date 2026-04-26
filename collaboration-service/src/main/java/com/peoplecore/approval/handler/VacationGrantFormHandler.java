package com.peoplecore.approval.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.dto.docdata.VacationGrantDocData;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalLine;
import com.peoplecore.event.VacationApprovalResultEvent;
import com.peoplecore.event.VacationGrantApprovalDocCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class VacationGrantFormHandler implements ApprovalFormHandler {

    private static final String FORM_CODE = "VACATION_GRANT_REQUEST";
    private static final String TOPIC_DOC_CREATED = "vacation-grant-approval-doc-created";
    private static final String TOPIC_RESULT = "vacation-grant-approval-result";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public VacationGrantFormHandler(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
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
    public void onDocCreated(ApprovalDocument document, List<ApprovalLine> lines) {
        try {
            VacationGrantDocData data = objectMapper.readValue(document.getDocData(), VacationGrantDocData.class);
            VacationGrantApprovalDocCreatedEvent event = VacationGrantApprovalDocCreatedEvent.builder()
                    .companyId(document.getCompanyId())
                    .approvalDocId(document.getDocId())
                    .empId(document.getEmpId())
                    .empName(document.getEmpName())
                    .deptId(document.getEmpDeptId())
                    .deptName(document.getEmpDeptName())
                    .empGrade(document.getEmpGrade())
                    .empTitle(document.getEmpTitle())
                    .infoId(data.getInfoId())
                    .vacReqUseDay(data.getVacReqUseDay())
                    .vacReqReason(data.getVacReqReason())
                    .pregnancyWeeks(data.getPregnancyWeeks())
                    .finalApproverEmpId(ApprovalFormHandler.findFinalApproverEmpId(lines))
                    .build();
            kafkaTemplate.send(TOPIC_DOC_CREATED, objectMapper.writeValueAsString(event));
            log.info("[Kafka] VacationGrant docCreated 발행 - docId={}, empId={}", document.getDocId(), document.getEmpId());
        } catch (Exception e) {
            log.error("[Kafka] VacationGrant docCreated 발행 실패 - docId={}, err={}", document.getDocId(), e.getMessage());
        }
    }

    @Override
    public void onResult(ApprovalDocument document, String status, Long managerId, String rejectReason) {
        try {
            /* vacReqId 는 USE 전용 식별자 — GRANT 발행에서 세팅 X. hr 측은 approvalDocId 로 조회 */
            VacationApprovalResultEvent event = VacationApprovalResultEvent.builder()
                    .companyId(document.getCompanyId())
                    .approvalDocId(document.getDocId())
                    .status(status)
                    .managerId(managerId)
                    .rejectReason(rejectReason)
                    .build();
            kafkaTemplate.send(TOPIC_RESULT, objectMapper.writeValueAsString(event));
            log.info("[Kafka] VacationGrant result 발행 - docId={}, status={}", document.getDocId(), status);
        } catch (Exception e) {
            log.error("[Kafka] VacationGrant result 발행 실패 - docId={}, err={}", document.getDocId(), e.getMessage());
        }
    }
}
