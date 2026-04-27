package com.peoplecore.approval.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.dto.DocumentCreateRequest;
import com.peoplecore.approval.dto.EmpDetailResponse;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.repository.ApprovalDocumentRepository;
import com.peoplecore.approval.service.ApprovalDocumentService;
import com.peoplecore.client.component.HrServiceClient;
import com.peoplecore.event.PayrollApprovalDocCreatedEvent;
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
public class PayrollApprovalDocCreatedConsumer {


    private static final String HR_REF_TYPE = "PAYROLL_RUN";

    private final ApprovalDocumentService approvalDocumentService;
    private final ObjectMapper objectMapper;
    private final ApprovalDocumentRepository approvalDocumentRepository;
    private final HrServiceClient hrServiceClient;

    @Autowired
    public PayrollApprovalDocCreatedConsumer(ApprovalDocumentService approvalDocumentService, ObjectMapper objectMapper, ApprovalDocumentRepository approvalDocumentRepository, HrServiceClient hrServiceClient) {
        this.approvalDocumentService = approvalDocumentService;
        this.objectMapper = objectMapper;
        this.approvalDocumentRepository = approvalDocumentRepository;
        this.hrServiceClient = hrServiceClient;
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2))
    @KafkaListener(
            topics = "payroll-approval-doc-created", groupId = "collaboration-approval"
    )
    public void consume(String message) {
            // 1) 메시지를 자바 객체로 변환 (JSON → Event DTO)
            PayrollApprovalDocCreatedEvent event;
            try {
                event = objectMapper.readValue(message, PayrollApprovalDocCreatedEvent.class);
            } catch (Exception e) {
                log.error("[Collab] 급여결의서 역직렬화 실패 - 메시지 스킵: {}", message, e);
                return;  // 포맷 오류는 재시도해도 해결 불가 → DLT 넘기지 말고 skip
            }

            try {
                // 2) 사전 멱등(중복) 체크
                Optional<ApprovalDocument> existing = approvalDocumentRepository
                        .findByCompanyIdAndHrRefTypeAndHrRefId(
                                event.getCompanyId(), HR_REF_TYPE, event.getPayrollRunId());
                if (existing.isPresent()) {
                    log.info("[Collab] 급여결의서 중복 수신 - skip. payrollRunId={}, docId={}",
                            event.getPayrollRunId(), existing.get().getDocId());
                    return;
                }

                // 3) 기안자 정보 조회
                EmpDetailResponse drafter = hrServiceClient.getEmployee(
                        event.getCompanyId(), event.getDrafterId());

                // 결재선 변환 (결재자별 hr 조회) — 7-7-4 참조
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


                // 4) DocumentCreateRequest 로 변환
                DocumentCreateRequest request = DocumentCreateRequest.builder()
                        .formId(event.getFormId())
                        .docType("NEW")
                        .docTitle(String.format("급여결의서 (#%d)", event.getPayrollRunId()))
                        .docData(objectMapper.writeValueAsString(
                                java.util.Map.of("html", event.getHtmlContent())))
                        .approvalLines(lineRequests)
                        .hrRefType(HR_REF_TYPE)
                        .hrRefId(event.getPayrollRunId())
                        .build();

                // 5) 기존 createDocument 재사용 → DB 에 ApprovalDocument 저장
                //    hr 이벤트 기반 상신은 첨부파일이 없으므로 files 는 빈 리스트
                Long docId = approvalDocumentService.createDocument(
                        event.getCompanyId(),
                        event.getDrafterId(),
                        drafter.getEmpName(),
                        drafter.getDeptId(),
                        drafter.getGradeName(),
                        drafter.getTitleName(),
                        request,
                        java.util.Collections.emptyList()
                );

                log.info("[Collab] 급여결의서 ApprovalDocument 생성 - runId={}, docId={}, formId={}",
                        event.getPayrollRunId(), docId, event.getFormId());

            } catch (DataIntegrityViolationException dup) {
                log.warn("[Collab] 급여결의서 unique 충돌 - 동시성 중복 skip. payrollRunId={}", event.getPayrollRunId());
            } catch (Exception e) {
                log.error("[Collab] 급여결의서 처리 실패 - retry 대상: payrollRunId={}, err={}", event.getPayrollRunId(), e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }



}
