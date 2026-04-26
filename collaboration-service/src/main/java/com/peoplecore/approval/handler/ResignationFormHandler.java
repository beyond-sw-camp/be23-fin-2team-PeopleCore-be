package com.peoplecore.approval.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.event.ResignApprovedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ResignationFormHandler implements ApprovalFormHandler {

    private static final String FORM_CODE = "RESIGNATION";
    private static final String TOPIC_RESIGN_APPROVED = "resign-approved";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ResignationFormHandler(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(ApprovalDocument document) {
        return FORM_CODE.equals(document.getFormId().getFormCode());
    }

    /* 사직서 최종 승인 시 hr-service 로 이벤트 발행 */
    @Override
    public void onApproved(ApprovalDocument document) {
        try {
            ResignApprovedEvent event = ResignApprovedEvent.builder()
                    .companyId(document.getCompanyId())
                    .docId(document.getDocId())
                    .empId(document.getEmpId())
                    .docData(document.getDocData())
                    .build();
            kafkaTemplate.send(TOPIC_RESIGN_APPROVED, objectMapper.writeValueAsString(event));
            log.info("[Kafka] Resignation approved 발행 - docId={}, empId={}", document.getDocId(), document.getEmpId());
        } catch (Exception e) {
            log.error("[Kafka] 퇴직 승인 이벤트 발행 실패 - docId={}, err={}", document.getDocId(), e.getMessage());
        }
    }
}
