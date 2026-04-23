package com.peoplecore.approval.publisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalLine;
import com.peoplecore.approval.entity.ApprovalRole;
import com.peoplecore.event.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/*
 * 초과근무/휴가 결재 이벤트 Kafka 발행 통합 컴포넌트.
 *
 * 발행 시점:
 *  - ApprovalDocumentService.createDocument() 직후 → publishDocCreated
 *  - ApprovalLineService.approve() 최종 승인 → publishResult(APPROVED)
 *  - ApprovalLineService.rejectDocument() → publishResult(REJECTED)
 *  - ApprovalDocumentService.recallDocument() → publishResult(CANCELED
 *
 */
@Component
@Slf4j
public class ApprovalEventPublisher {

    private static final String FORM_NAME_OVERTIME = "초과근로신청서";
    private static final String FORM_NAME_VACATION_PREFIX = "휴가원";
    private static final String FORM_CODE_ATTENDANCE_MODIFY = "ATTENDANCE_MODIFY"; // formCode 기반 분기
    private static final String TOPIC_OT_DOC_CREATED = "overtime-approval-doc-created";
    private static final String TOPIC_OT_RESULT = "overtime-approval-result";
    private static final String TOPIC_VAC_DOC_CREATED = "vacation-approval-doc-created";
    private static final String TOPIC_VAC_RESULT = "vacation-approval-result";
    private static final String TOPIC_ATTEN_MODIFY_DOC_CREATED = "attendance-modify-doc-created";
    private static final String TOPIC_ATTEN_MODIFY_RESULT = "attendance-modify-result";
    private static final String FORM_NAME_SEVERANCE_PREFIX = "퇴직급여지급결의서";
    private static final String FORM_NAME_PAYROLL_PREFIX   = "급여지급결의서";
    private static final String TOPIC_SEVERANCE_RESULT     = "severance-approval-result";
    private static final String TOPIC_PAYROLL_RESULT       = "payroll-approval-result";


    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ApprovalEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                  ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /* 문서 생성 직후 — 양식 본문(docData) + 결재선에서 정보 파싱해 이벤트 발행 */
    public void publishDocCreated(ApprovalDocument document, List<ApprovalLine> lines) {
        String formName = document.getFormId().getFormName();
        String docData = document.getDocData();
        String formCode = document.getFormId().getFormCode();

        try {
            if (FORM_NAME_OVERTIME.equals(formName)) {
                OvertimeApprovalDocCreatedEvent event = OvertimeApprovalDocCreatedEvent.builder()
                        .companyId(document.getCompanyId())
                        .approvalDocId(document.getDocId())
                        .empId(document.getEmpId())
                        .deptId(document.getEmpDeptId())
                        .otDate(parseDateTime(docData, "otDate"))
                        .otPlanStart(parseDateTime(docData, "otPlanStart"))
                        .otPlanEnd(parseDateTime(docData, "otPlanEnd"))
                        .otReason(parseString(docData, "otReason"))
                        .finalApproverEmpId(findFinalApproverEmpId(lines))
                        .build();
                kafkaTemplate.send(TOPIC_OT_DOC_CREATED, objectMapper.writeValueAsString(event));
                log.info("[Kafka] OT docCreated 발행 - docId={}, empId={}", document.getDocId(), document.getEmpId());
            } else if (formName != null && formName.startsWith(FORM_NAME_VACATION_PREFIX)) {
                VacationApprovalDocCreatedEvent event = VacationApprovalDocCreatedEvent.builder()
                        .companyId(document.getCompanyId())
                        .approvalDocId(document.getDocId())
                        .empId(document.getEmpId())
                        .empName(document.getEmpName())
                        .deptId(document.getEmpDeptId())
                        .deptName(document.getEmpDeptName())
                        .empGrade(document.getEmpGrade())
                        .empTitle(document.getEmpTitle())
                        .infoId(parseLong(docData, "infoId"))
                        .vacReqStartat(parseDateTime(docData, "vacReqStartat"))
                        .vacReqEndat(parseDateTime(docData, "vacReqEndat"))
                        .vacReqUseDay(parseDecimal(docData, "vacReqUseDay"))
                        .vacReqReason(parseString(docData, "vacReqReason"))
                        .finalApproverEmpId(findFinalApproverEmpId(lines))
                        // 이벤트 기반 휴가 메타 - 해당 유형 아닌 경우 docData 에 없어 null 파싱
                        .proofFileUrl(parseString(docData, "proofFileUrl"))
                        .pregnancyWeeks(parseInteger(docData, "pregnancyWeeks"))
                        .officialLeaveReason(parseString(docData, "officialLeaveReason"))
                        .relatedBirthDate(parseDate(docData, "relatedBirthDate"))
                        .build();
                kafkaTemplate.send(TOPIC_VAC_DOC_CREATED, objectMapper.writeValueAsString(event));
                log.info("[Kafka] Vacation docCreated 발행 - docId={}, empId={}", document.getDocId(), document.getEmpId());
            } else if (FORM_CODE_ATTENDANCE_MODIFY.equals(formCode)) { // 근태 정정 — formCode 기반 분기
                AttendanceModifyDocCreatedEvent event = AttendanceModifyDocCreatedEvent.builder()
                        .companyId(document.getCompanyId())
                        .approvalDocId(document.getDocId())
                        .empId(document.getEmpId())
                        .comRecId(parseLong(docData, "comRecId"))
                        .workDate(parseDate(docData, "workDate"))
                        .attenReqCheckIn(parseDateTime(docData, "attenReqCheckIn"))
                        .attenReqCheckOut(parseDateTime(docData, "attenReqCheckOut"))
                        .attenReason(parseString(docData, "attenReason"))
                        .build();
                kafkaTemplate.send(TOPIC_ATTEN_MODIFY_DOC_CREATED, objectMapper.writeValueAsString(event));
                log.info("[Kafka] AttendanceModify docCreated 발행 - docId={}, empId={}", document.getDocId(), document.getEmpId());
            }
        } catch (Exception e) {
            log.error("[Kafka] docCreated 발행 실패 - docId={}, err={}", document.getDocId(), e.getMessage());
        }
    }

    /* 최종 승인/반려/회수 시 호출 (status: APPROVED / REJECTED / CANCELED) */
    public void publishResult(ApprovalDocument document, String status, Long managerId, String rejectReason) {
        String formName = document.getFormId().getFormName();
        String formCode = document.getFormId().getFormCode();

        try {
            if (FORM_NAME_OVERTIME.equals(formName)) {
                OvertimeApprovalResultEvent event = OvertimeApprovalResultEvent.builder()
                        .companyId(document.getCompanyId())
                        .otId(extractOtId(document))
                        .approvalDocId(document.getDocId())
                        .status(status)
                        .managerId(managerId)
                        .rejectReason(rejectReason)
                        .build();
                kafkaTemplate.send(TOPIC_OT_RESULT, objectMapper.writeValueAsString(event));
                log.info("[Kafka] OT result 발행 - docId={}, status={}", document.getDocId(), status);
            } else if (formName != null && formName.startsWith(FORM_NAME_VACATION_PREFIX)) {
                VacationApprovalResultEvent event = VacationApprovalResultEvent.builder()
                        .companyId(document.getCompanyId())
                        .vacReqId(extractVacReqId(document))
                        .approvalDocId(document.getDocId())
                        .status(status)
                        .managerId(managerId)
                        .rejectReason(rejectReason)
                        .build();
                kafkaTemplate.send(TOPIC_VAC_RESULT, objectMapper.writeValueAsString(event));
                log.info("[Kafka] Vacation result 발행 - docId={}, status={}", document.getDocId(), status);
            } else if (FORM_CODE_ATTENDANCE_MODIFY.equals(formCode)) { // 근태 정정 결과
                AttendanceModifyResultEvent event = AttendanceModifyResultEvent.builder()
                        .companyId(document.getCompanyId())
                        .approvalDocId(document.getDocId())
                        .status(status)
                        .managerId(managerId)
                        .rejectReason(rejectReason)
                        .build();
                kafkaTemplate.send(TOPIC_ATTEN_MODIFY_RESULT, objectMapper.writeValueAsString(event));
                log.info("[Kafka] AttendanceModify result 발행 - docId={}, status={}", document.getDocId(), status);
            } else if (formName != null && formName.startsWith(FORM_NAME_PAYROLL_PREFIX)) {  // 급여 결과
                Long payrollRunId = parseLong(document.getDocData(), "payrollRunId");
                PayrollApprovalResultEvent event = PayrollApprovalResultEvent.builder()
                        .companyId(document.getCompanyId())
                        .payrollRunId(payrollRunId)
                        .approvalDocId(document.getDocId())
                        .status(status)
                        .managerId(managerId)
                        .rejectReason(rejectReason)
                        .build();
                kafkaTemplate.send(TOPIC_PAYROLL_RESULT, objectMapper.writeValueAsString(event));
                log.info("[Kafka] Payroll result 발행 - docId={}, status={}", document.getDocId(), status);
            } else if (formName != null && formName.startsWith(FORM_NAME_SEVERANCE_PREFIX)) {  // 퇴직금 결과
                Long sevId = parseLong(document.getDocData(), "sevId");
                SeveranceApprovalResultEvent event = SeveranceApprovalResultEvent.builder()
                        .companyId(document.getCompanyId())
                        .sevId(sevId)
                        .approvalDocId(document.getDocId())
                        .status(status)
                        .managerId(managerId)
                        .rejectReason(rejectReason)
                        .build();
                kafkaTemplate.send(TOPIC_SEVERANCE_RESULT, objectMapper.writeValueAsString(event));
                log.info("[Kafka] Severance result 발행 - docId={}, status={}", document.getDocId(), status);
            }
        } catch (Exception e) {
            log.error("[Kafka] result 발행 실패 - docId={}, err={}", document.getDocId(), e.getMessage());
        }
    }



    /* 결재선 중 APPROVER 역할의 max lineStep 사원 empId. 없으면 null */
    private Long findFinalApproverEmpId(List<ApprovalLine> lines) {
        if (lines == null || lines.isEmpty()) return null;
        return lines.stream()
                .filter(l -> l.getApprovalRole() == ApprovalRole.APPROVER)
                .max(Comparator.comparingInt(ApprovalLine::getLineStep))
                .map(ApprovalLine::getEmpId)
                .orElse(null);
    }

    /* result 발행 시 otId — hr 측이 docId 로 찾는 것이 주경로라 null 허용. docData 에 있으면 파싱 */
    private Long extractOtId(ApprovalDocument document) {
        return parseLong(document.getDocData(), "otId");
    }

    private Long extractVacReqId(ApprovalDocument document) {
        return parseLong(document.getDocData(), "vacReqId");
    }

    private String parseString(String docData, String field) {
        JsonNode n = readField(docData, field);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private Long parseLong(String docData, String field) {
        JsonNode n = readField(docData, field);
        return (n != null && n.isNumber()) ? n.asLong() : null;
    }

    /* Integer 필드 파싱 - 유산사산 주수 등. 숫자 아니면 null */
    private Integer parseInteger(String docData, String field) {
        JsonNode n = readField(docData, field);
        return (n != null && n.isNumber()) ? n.asInt() : null;
    }

    private BigDecimal parseDecimal(String docData, String field) {
        JsonNode n = readField(docData, field);
        if (n == null || n.isNull()) return null;
        try {
            return new BigDecimal(n.asText());
        } catch (Exception e) {
            return null;
        }
    }

    /* ISO-8601 datetime 파싱. 문자열 or null */
    private LocalDateTime parseDateTime(String docData, String field) {
        JsonNode n = readField(docData, field);
        if (n == null || n.isNull()) return null;
        try {
            return LocalDateTime.parse(n.asText());
        } catch (Exception e) {
            log.warn("[Publisher] datetime 파싱 실패 - field={}, val={}", field, n.asText());
            return null;
        }
    }

    private JsonNode readField(String docData, String field) {
        if (docData == null || docData.isBlank()) return null;
        try {
            return objectMapper.readTree(docData).get(field);
        } catch (Exception e) {
            return null;
        }
    }

    /* ISO-8601 date 파싱 (yyyy-MM-dd). 문자열 or null */
    private LocalDate parseDate(String docData, String field) {
        JsonNode n = readField(docData, field);
        if (n == null || n.isNull()) return null;
        try {
            return LocalDate.parse(n.asText());
        } catch (Exception e) {
            log.warn("[Publisher] date 파싱 실패 - field={}, val={}", field, n.asText());
            return null;
        }
    }

}
