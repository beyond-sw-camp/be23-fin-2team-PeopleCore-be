package com.peoplecore.attendance.service.result;

import com.peoplecore.alarm.publisher.HrAlarmPublisher;
import com.peoplecore.attendance.entity.AttendanceModify;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.HolidayReason;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.entity.WorkStatus;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import com.peoplecore.attendance.service.HolidayReasonResolver;
import com.peoplecore.attendance.service.PayrollMinutesCalculator;
import com.peoplecore.attendance.service.WorkStatusResolver;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/* APPROVED 결과 처리 - CommuteRecord native UPDATE 또는 신규 INSERT + 분 컬럼 재계산 + 신청자 알림 */
@Component
@Slf4j
public class ApprovedResultHandler implements ApprovalResultHandler {

    private final CommuteRecordRepository commuteRecordRepository;
    private final PayrollMinutesCalculator payrollMinutesCalculator;
    private final HolidayReasonResolver holidayReasonResolver;
    private final WorkStatusResolver workStatusResolver;
    private final HrAlarmPublisher hrAlarmPublisher;

    @Autowired
    public ApprovedResultHandler(CommuteRecordRepository commuteRecordRepository,
                                 PayrollMinutesCalculator payrollMinutesCalculator,
                                 HolidayReasonResolver holidayReasonResolver,
                                 WorkStatusResolver workStatusResolver,
                                 HrAlarmPublisher hrAlarmPublisher) {
        this.commuteRecordRepository = commuteRecordRepository;
        this.payrollMinutesCalculator = payrollMinutesCalculator;
        this.holidayReasonResolver = holidayReasonResolver;
        this.workStatusResolver = workStatusResolver;
        this.hrAlarmPublisher = hrAlarmPublisher;
    }

    @Override
    public boolean supports(String status) {
        return "APPROVED".equals(status);
    }

    @Override
    public void handle(AttendanceModify am, Employee manager, String rejectReason) {
        am.approve(manager);
        applyApprovedRecord(am);
        notifyRequester(am, "근태 정정 신청이 승인되었습니다.",
                "수정된 출퇴근 시각이 반영되었습니다.");
    }

    /* 정정 승인 반영
     *  - comRecId 있으면 native UPDATE (check-in/out + workStatus 일괄, work_date 포함)
     *  - 없으면 신규 INSERT (휴일근무 미입력 정정 등)
     * 두 케이스 모두 끝에 PayrollMinutesCalculator.applyApprovedRecognition 호출 → native UPDATE 로 분 컬럼 재계산. */
    private void applyApprovedRecord(AttendanceModify am) {
        Employee emp = am.getEmployee();
        WorkGroup wg = emp.getWorkGroup();
        HolidayReason reason = holidayReasonResolver.resolve(am.getCompanyId(), am.getWorkDate(), wg);
        WorkStatus newStatus = resolveCorrectedStatus(
                am.getAttenReqCheckIn(), am.getAttenReqCheckOut(), wg, reason);

        if (am.getComRecId() != null) {
            int updated = commuteRecordRepository.applyAttendanceModify(
                    am.getComRecId(), am.getWorkDate(),
                    am.getAttenReqCheckIn(), am.getAttenReqCheckOut(), newStatus.name());
            if (updated != 1) {
                log.error("[AttendanceModify] CommuteRecord UPDATE 실패 - attenModiId={}, affected={}",
                        am.getAttenModiId(), updated);
                throw new CustomException(ErrorCode.ATTENDANCE_MODIFY_APPLY_FAILED);
            }
            CommuteRecord reloaded = commuteRecordRepository
                    .findByComRecIdAndWorkDate(am.getComRecId(), am.getWorkDate())
                    .orElseThrow(() -> new CustomException(ErrorCode.ATTENDANCE_MODIFY_APPLY_FAILED));
            payrollMinutesCalculator.applyApprovedRecognition(
                    reloaded, PayrollMinutesCalculator.RecognitionSource.ATTENDANCE_MODIFY);
            log.info("[AttendanceModify] UPDATE + 상태/분 재판정 - attenModiId={}, comRecId={}, status={}",
                    am.getAttenModiId(), am.getComRecId(), newStatus);
            return;
        }

        CommuteRecord saved = commuteRecordRepository.save(CommuteRecord.builder()
                .workDate(am.getWorkDate())
                .companyId(am.getCompanyId())
                .employee(emp)
                .comRecCheckIn(am.getAttenReqCheckIn())
                .comRecCheckOut(am.getAttenReqCheckOut())
                .holidayReason(reason)
                .workStatus(newStatus)
                .build());
        payrollMinutesCalculator.applyApprovedRecognition(
                saved, PayrollMinutesCalculator.RecognitionSource.ATTENDANCE_MODIFY);
        log.info("[AttendanceModify] INSERT + 상태/분 재판정 - attenModiId={}, comRecId={}, status={}",
                am.getAttenModiId(), saved.getComRecId(), newStatus);
    }

    /* 새 checkIn/checkOut 기준 final WorkStatus 산출.
     * checkIn null → NORMAL 폴백(체크인 없는 정정은 비정상 케이스).
     * checkOut null → initial 만 반환(조퇴 판정 불가). */
    private WorkStatus resolveCorrectedStatus(LocalDateTime ci, LocalDateTime co,
                                              WorkGroup wg, HolidayReason reason) {
        if (ci == null) return WorkStatus.NORMAL;
        WorkStatus initial = workStatusResolver.resolveInitial(
                ci.toLocalTime(), wg.getGroupStartTime(), reason);
        if (co == null) return initial;
        return workStatusResolver.resolveFinal(initial, co.toLocalTime(), wg.getGroupEndTime());
    }

    /* 신청자 본인 알림 - 승인 결과 통지 */
    private void notifyRequester(AttendanceModify am, String title, String content) {
        hrAlarmPublisher.publisher(AlarmEvent.builder()
                .companyId(am.getCompanyId())
                .empIds(List.of(am.getEmployee().getEmpId()))
                .alarmType("ATTENDANCE")
                .alarmTitle(title)
                .alarmContent(content)
                .alarmLink("/attendance/my")
                .alarmRefType("ATTENDANCE_MODIFY")
                .alarmRefId(am.getAttenModiId())
                .build());
    }
}
