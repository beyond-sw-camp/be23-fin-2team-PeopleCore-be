package com.peoplecore.attendance.service.result;

import com.peoplecore.alarm.publisher.HrAlarmPublisher;
import com.peoplecore.attendance.entity.AttendanceModify;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.event.AlarmEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/* CANCELED 결과 처리 - 엔티티 전이 + 신청자 회수 알림 */
@Component
public class CanceledResultHandler implements ApprovalResultHandler {

    private final HrAlarmPublisher hrAlarmPublisher;

    @Autowired
    public CanceledResultHandler(HrAlarmPublisher hrAlarmPublisher) {
        this.hrAlarmPublisher = hrAlarmPublisher;
    }

    @Override
    public boolean supports(String status) {
        return "CANCELED".equals(status);
    }

    @Override
    public void handle(AttendanceModify am, Employee manager, String rejectReason) {
        am.cancel();
        hrAlarmPublisher.publisher(AlarmEvent.builder()
                .companyId(am.getCompanyId())
                .empIds(List.of(am.getEmployee().getEmpId()))
                .alarmType("ATTENDANCE")
                .alarmTitle("근태 정정 신청이 회수되었습니다.")
                .alarmContent("기안자가 문서를 회수했습니다.")
                .alarmLink("/attendance/my")
                .alarmRefType("ATTENDANCE_MODIFY")
                .alarmRefId(am.getAttenModiId())
                .build());
    }
}
