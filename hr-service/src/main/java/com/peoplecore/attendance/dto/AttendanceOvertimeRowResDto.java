package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 초과근무 탭(/attendance/admin/overtime) 한 행.
 * 주간 근무시간 계산식:
 *   Σ(근무그룹 소정근무시간 - 지각분 - 조퇴분) + 당일 승인 초과근무 분
 *   (휴가일/비근무요일은 승인 OT 만 합산, 결근일은 0)
 * 초과 기준: OvertimePolicy.otPolicyWeeklyMaxHour
 * 경고 기준: OvertimePolicy.otPolicyWarningHour
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceOvertimeRowResDto {

    /** 사원 PK */
    private Long empId;

    /** 사번 */
    private String empNum;

    /** 사원명 */
    private String empName;

    /** 부서명 */
    private String deptName;

    /** 직급명 */
    private String gradeName;

    /** 주간 총 근무시간 h (소수 1자리) */
    private double weeklyWorkHours;

    /** 정책상 주간 최대 근무시간 h (OvertimePolicy.otPolicyWeeklyMaxHour) */
    private int weeklyMaxHour;

    /** 정책상 경고 기준 h (OvertimePolicy.otPolicyWarningHour) */
    private int weeklyWarningHour;

    /** 초과분 h = max(0, weeklyWorkHours - weeklyMaxHour), 소수 1자리 */
    private double overtimeHours;

    /** 상태 라벨 — "정상" / "경고" / "초과" */
    private String status;
}
