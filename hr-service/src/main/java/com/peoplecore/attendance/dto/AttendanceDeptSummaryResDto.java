package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 부서별현황(/attendance/admin/dept-summary) 한 행.
 * 주간(월~일) 단위 부서별 근태 집계.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceDeptSummaryResDto {

    /** 부서 PK */
    private Long deptId;

    /** 부서명 */
    private String deptName;

    /** 부서 총 인원 (주간 내 한 번이라도 등장한 사원 기준, employmentFilter 반영) */
    private int totalEmp;

    /**
     * 주간 출근율(%) = 출근한 (사원×소정근무일수) / 전체 (사원×소정근무일수) × 100.
     * 휴가일은 분모/분자 모두 제외. 소수 1자리.
     */
    private double attendRate;

    /**
     * 주간 지각률(%) = 지각건수 / 전체 (사원×소정근무일수) × 100.
     * 분모는 attendRate 와 동일 (근무일수×인원). 소수 1자리.
     */
    private double lateRate;

    /** 주간 결근 건수 (중복 포함 — 한 사원이 여러 날 결근하면 모두 카운트) */
    private int absentCount;

    /** 부서 1인당 평균 주간 초과근무 시간 h (초과분 합 / totalEmp) */
    private double avgOvertimeHours;

    /** 주간 초과근무 발생 인원 수 (weekly > weeklyMaxHour 인 사원 수, 중복 없음) */
    private int overtimeCount;

    /** 부서 주간 평균 근무시간 h (전체 사원 주간 근무시간 합 / totalEmp) */
    private double weeklyAvg;
}
