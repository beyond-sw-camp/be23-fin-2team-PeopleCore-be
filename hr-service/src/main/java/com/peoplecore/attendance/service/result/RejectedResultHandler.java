package com.peoplecore.attendance.service.result;

import com.peoplecore.alarm.publisher.HrAlarmPublisher;
import com.peoplecore.attendance.entity.AttendanceModify;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.event.AlarmEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/* REJECTED 결과 처리 - 엔티티 전이 + 신청자 반려 알림 */
@Component
public class RejectedResultHandler implements ApprovalResultHandler {

    private final HrAlarmPublisher hrAlarmPublisher;

    @Autowired
    public RejectedResultHandler(HrAlarmPublisher hrAlarmPublisher) {
        this.hrAlarmPublisher = hrAlarmPublisher;
    }

    @Override
    public boolean supports(String status) {
        return "REJECTED".equals(status);
    }

    @Override
    public void handle(AttendanceModify am, Employee manager, String rejectReason) {
        am.reject(manager, rejectReason);
        hrAlarmPublisher.publisher(AlarmEvent.builder()
                .companyId(am.getCompanyId())
                .empIds(List.of(am.getEmployee().getEmpId()))
                .alarmType("ATTENDANCE")
                .alarmTitle("근태 정정 신청이 반려되었습니다.")
                .alarmContent(rejectReason != null ? rejectReason : "반려 사유 없음")
                .alarmLink("/attendance/my")
                .alarmRefType("ATTENDANCE_MODIFY")
                .alarmRefId(am.getAttenModiId())
                .build());
    }
}
