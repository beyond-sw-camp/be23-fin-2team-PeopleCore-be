package com.peoplecore.attendance.service.result;

import com.peoplecore.attendance.entity.AttendanceModify;
import com.peoplecore.employee.domain.Employee;

/* 근태 정정 결재 결과 핸들러.
 * collab 의 ApprovalFormHandler 와 동일한 List + supports() 패턴.
 * status 별 동작 분기를 객체 다형성으로 풀어 switch 제거. */
public interface ApprovalResultHandler {

    /* 이벤트 status 와 매칭되는 핸들러 식별 */
    boolean supports(String status);

    /* 결재 결과 반영 - 엔티티 전이 + CommuteRecord 갱신(필요 시) + 알림 */
    void handle(AttendanceModify am, Employee manager, String rejectReason);
}
