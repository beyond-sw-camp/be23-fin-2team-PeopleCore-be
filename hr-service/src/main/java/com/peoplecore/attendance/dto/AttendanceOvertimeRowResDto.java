package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.WeeklyWorkStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/* * 초과근무 탭(/attendance/admin/overtime) 한 행.
 * 주간 근무시간 계산식:
 *   Σ(근무그룹 소정근무시간 - 지각분 - 조퇴분) + 당일 승인 초과근무 분
 *   (휴가일/비근무요일은 승인 OT 만 합산, 결근일은 0)
 * 초과 기준: OvertimePolicy.otPolicyWeeklyMaxMinutes
 * 경고 기준: OvertimePolicy.otPolicyWarningMinutes
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

    /** 주간 총 근무분 m (소수 1자리) */
    private double weeklyWorkMinutes;

    /** 정책상 주간 최대 근무분 m (OvertimePolicy.otPolicyWeeklyMaxminute) */
    private int weeklyMaxMinutes;

    /** 정책상 경고 기준 m (OvertimePolicy.otPolicyWarningMinute) */
    private int weeklyWarningMinutes;

    /** 초과분 m = max(0, weeklyWorkMinutes - weeklyMaxMinutes), 소수 1자리 */
    private double overtimeMinutes;

    /** 상태 라벨 — "정상" / "경고" / "초과" */
    private WeeklyWorkStatus status;
}
