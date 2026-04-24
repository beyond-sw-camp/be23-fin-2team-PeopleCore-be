package com.peoplecore.approval.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.dto.DocumentCreateRequest;
import com.peoplecore.approval.dto.EmpDetailResponse;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.repository.ApprovalDocumentRepository;
import com.peoplecore.approval.service.ApprovalDocumentService;
import com.peoplecore.client.component.HrServiceClient;
import com.peoplecore.event.PayrollApprovalDocCreatedEvent;
import com.peoplecore.event.SeveranceApprovalDocCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class SeveranceApprovalDocCreatedConsumer {
    private static final String HR_REF_TYPE = "SEVERANCE";

    private final ApprovalDocumentService approvalDocumentService;
    private final ObjectMapper objectMapper;
    private final ApprovalDocumentRepository approvalDocumentRepository;
    private final HrServiceClient hrServiceClient;


    @Autowired
    public SeveranceApprovalDocCreatedConsumer(ApprovalDocumentService approvalDocumentService, ObjectMapper objectMapper, ApprovalDocumentRepository approvalDocumentRepository, HrServiceClient hrServiceClient) {
        this.approvalDocumentService = approvalDocumentService;
        this.objectMapper = objectMapper;
        this.approvalDocumentRepository = approvalDocumentRepository;
        this.hrServiceClient = hrServiceClient;
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2))
    @KafkaListener(
            topics = "severance-approval-doc-created", groupId = "collaboration-approval"
    )
    public void consume(String message) {
        SeveranceApprovalDocCreatedEvent event;
        try {
            event = objectMapper.readValue(message, SeveranceApprovalDocCreatedEvent.class);
        } catch (Exception e) {
            log.error("[Collab] 퇴직급여결의서 역직렬화 실패 - 메시지 스킵: {}", message, e);
            return;  // 포맷 오류는 재시도해도 해결 불가 → DLT 넘기지 말고 skip
        }

        try {
            Optional<ApprovalDocument> existing = approvalDocumentRepository
                    .findByCompanyIdAndHrRefTypeAndHrRefId(
                            event.getCompanyId(), HR_REF_TYPE, event.getSevId());
            if (existing.isPresent()) {
                log.info("[Collab] 급여결의서 중복 수신 - skip. payrollRunId={}, docId={}",
                        event.getSevId(), existing.get().getDocId());
                return;
            }

            EmpDetailResponse drafter = hrServiceClient.getEmployee(
                    event.getCompanyId(), event.getDrafterId());
            List<DocumentCreateRequest.ApprovalLineRequest> lineRequests =
                    event.getApprovalLine().stream()
                            .map(line -> {
                                EmpDetailResponse approver = hrServiceClient.getEmployee(
                                        event.getCompanyId(), line.getApproverId());
                                return DocumentCreateRequest.ApprovalLineRequest.builder()
                                        .empId(line.getApproverId())
                                        .empName(approver.getEmpName())
                                        .empDeptId(approver.getDeptId())
                                        .empDeptName(approver.getDeptName())
                                        .empGrade(approver.getGradeName())
                                        .empTitle(approver.getTitleName())
                                        .approvalRole(line.getApprovalType())
                                        .lineStep(line.getOrder())
                                        .build();
                            })
                            .toList();

            DocumentCreateRequest request = DocumentCreateRequest.builder()
                    .formId(event.getFormId())
                    .docType("NEW")
                    .docTitle(String.format("퇴직급여결의서 (#%d)", event.getSevId()))
                    .docData(objectMapper.writeValueAsString(
                            java.util.Map.of("html", event.getHtmlContent())))
                    .approvalLines(lineRequests)
                    .hrRefType(HR_REF_TYPE)
                    .hrRefId(event.getSevId())
                    .build();

            Long docId = approvalDocumentService.createDocument(
                    event.getCompanyId(),
                    event.getDrafterId(),
                    drafter.getEmpName(),
                    drafter.getDeptId(),
                    drafter.getGradeName(),
                    drafter.getTitleName(),
                    request
            );

            log.info("[Collab] 퇴직급여결의서 ApprovalDocument 생성 - runId={}, docId={}, formId={}",
                    event.getSevId(), docId, event.getFormId());
        } catch (DataIntegrityViolationException dup) {
            log.warn("[Collab] 퇴직급여결의서 unique 충돌 - 동시성 중복 skip. sevId={}", event.getSevId());
        } catch (Exception e) {
            log.error("[Collab] 퇴직급여결의서 처리 실패 - retry 대상: sevId={}, err={}", event.getSevId(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
