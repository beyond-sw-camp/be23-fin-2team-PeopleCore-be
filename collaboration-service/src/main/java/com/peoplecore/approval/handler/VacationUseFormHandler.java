package com.peoplecore.approval.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.dto.docdata.VacationUseDocData;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalLine;
import com.peoplecore.calendar.entity.Events;
import com.peoplecore.calendar.entity.MyCalendars;
import com.peoplecore.calendar.repository.EventsRepository;
import com.peoplecore.calendar.service.MyCalendarService;
import com.peoplecore.event.VacationApprovalDocCreatedEvent;
import com.peoplecore.event.VacationApprovalResultEvent;
import com.peoplecore.event.VacationSlotItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@Slf4j
public class VacationUseFormHandler implements ApprovalFormHandler {

    private static final String FORM_CODE = "VACATION_REQUEST";
    private static final String TOPIC_DOC_CREATED = "vacation-approval-doc-created";
    private static final String TOPIC_RESULT = "vacation-approval-result";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MyCalendarService myCalendarService;
    private final EventsRepository eventsRepository;

    @Autowired
    public VacationUseFormHandler(KafkaTemplate<String, String> kafkaTemplate,
                                  ObjectMapper objectMapper,
                                  MyCalendarService myCalendarService,
                                  EventsRepository eventsRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.myCalendarService = myCalendarService;
        this.eventsRepository = eventsRepository;
    }

    @Override
    public boolean supports(ApprovalDocument document) {
        return FORM_CODE.equals(document.getFormId().getFormCode());
    }

    @Override
    public void onDocCreated(ApprovalDocument document, List<ApprovalLine> lines, String htmlContent) {
        try {
            VacationUseDocData data = objectMapper.readValue(document.getDocData(), VacationUseDocData.class);
            VacationApprovalDocCreatedEvent event = VacationApprovalDocCreatedEvent.builder()
                    .companyId(document.getCompanyId())
                    .approvalDocId(document.getDocId())
                    .empId(document.getEmpId())
                    .empName(document.getEmpName())
                    .deptId(document.getEmpDeptId())
                    .deptName(document.getEmpDeptName())
                    .empGrade(document.getEmpGrade())
                    .empTitle(document.getEmpTitle())
                    .infoId(data.getInfoId())
                    .vacReqReason(data.getVacReqReason())
                    .items(data.getVacReqItems())
                    .finalApproverEmpId(ApprovalFormHandler.findFinalApproverEmpId(lines))
                    .build();
            kafkaTemplate.send(TOPIC_DOC_CREATED, objectMapper.writeValueAsString(event));
            log.info("[Kafka] Vacation docCreated 발행 - docId={}, empId={}, slotCount={}",
                    document.getDocId(), document.getEmpId(),
                    data.getVacReqItems() == null ? 0 : data.getVacReqItems().size());
        } catch (Exception e) {
            log.error("[Kafka] Vacation docCreated 발행 실패 - docId={}, err={}", document.getDocId(), e.getMessage());
        }
    }

    @Override
    public void onResult(ApprovalDocument document, String status, Long managerId, String rejectReason) {
        try {
            VacationApprovalResultEvent event = VacationApprovalResultEvent.builder()
                    .companyId(document.getCompanyId())
                    .approvalDocId(document.getDocId())
                    .status(status)
                    .managerId(managerId)
                    .rejectReason(rejectReason)
                    .build();
            kafkaTemplate.send(TOPIC_RESULT, objectMapper.writeValueAsString(event));
            log.info("[Kafka] Vacation result 발행 - docId={}, status={}", document.getDocId(), status);
        } catch (Exception e) {
            log.error("[Kafka] Vacation result 발행 실패 - docId={}, err={}", document.getDocId(), e.getMessage());
        }
    }

    /* 휴가 사용 최종 승인 — 슬롯별 캘린더 이벤트 생성. 실패해도 승인 플로우 롤백 X */
    @Override
    public void onApproved(ApprovalDocument document) {
        try {
            VacationUseDocData data = objectMapper.readValue(document.getDocData(), VacationUseDocData.class);
            List<VacationSlotItem> items = data.getVacReqItems();
            if (items == null || items.isEmpty()) {
                log.warn("[CalendarEvent] 휴가 슬롯 비어있음 - docId={} 이벤트 생성 skip", document.getDocId());
                return;
            }

            MyCalendars vacationCalendar = myCalendarService.ensureVacationCalendar(
                    document.getCompanyId(), document.getEmpId());

            items.forEach(item -> {
                BigDecimal useDay = item.getUseDay();
                boolean isAllDay = useDay != null && useDay.stripTrailingZeros().scale() <= 0;

                Events event = Events.builder()
                        .empId(document.getEmpId())
                        .title("[휴가] " + document.getDocTitle())
                        .description(data.getVacReqReason())
                        .startAt(item.getStartAt())
                        .endAt(item.getEndAt())
                        .isAllDay(isAllDay)
                        .isPublic(true)
                        .isAllEmployees(false)
                        .companyId(document.getCompanyId())
                        .myCalendars(vacationCalendar)
                        .build();
                eventsRepository.save(event);
                log.info("[CalendarEvent] 휴가 슬롯 이벤트 생성 - docId={}, empId={}, eventsId={}, start={}, isAllDay={}",
                        document.getDocId(), document.getEmpId(), event.getEventsId(), item.getStartAt(), isAllDay);
            });
        } catch (Exception e) {
            log.error("[CalendarEvent] 휴가 이벤트 생성 실패 - docId={}, err={}",
                    document.getDocId(), e.getMessage(), e);
        }
    }
}
