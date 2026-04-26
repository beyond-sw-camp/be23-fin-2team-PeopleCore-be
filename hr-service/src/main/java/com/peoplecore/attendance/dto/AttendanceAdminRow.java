package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.HolidayReason;
import com.peoplecore.attendance.entity.WorkStatus;
import com.peoplecore.employee.domain.EmpStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

/*
 * 관리자 근태 현황 API 의 1차 쿼리 결과 행 (QueryDSL Projection 대상).
 * Employee + Dept + Grade + WorkGroup + 오늘 CommuteRecord 를 JOIN 으로 한 번에 가져온 뒤
 * Summary/List/Card API 공통 입력으로 쓴다.
 * 휴가/OT/주간 누적분은 별도 맵 조회 후 서비스 레이어에서 병합 (N+1 없이 empId IN 절 1쿼리씩).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceAdminRow {

    /* ===== Employee 기본 ===== */

    /* 사원 PK */
    private Long empId;
    /* 사번 */
    private String empNum;
    /* 사원명 */
    private String empName;
    /* 재직상태 (ACTIVE / ON_LEAVE 만 들어옴. RESIGNED 는 쿼리에서 제외) */
    private EmpStatus empStatus;

    /* ===== 부서 ===== */

    /* 부서 PK */
    private Long deptId;
    /* 부서명 */
    private String deptName;

    /* ===== 직급 ===== */

    /* 직급 PK */
    private Long gradeId;
    /* 직급명 */
    private String gradeName;

    /* ===== 근무그룹 (LEFT JOIN — 미배정 사원 있을 수 있음) ===== */

    /* 근무그룹 PK (미배정 시 null) */
    private Long workGroupId;
    /* 근무그룹명 (미배정 시 null) */
    private String workGroupName;
    /* 근무그룹 출근시간 */
    private LocalTime groupStartTime;
    /* 근무그룹 퇴근시간 */
    private LocalTime groupEndTime;
    /* 근무요일 비트마스크 (월1, 화2, 수4, 목8, 금16, 토32, 일64). 오늘이 근무일인지 판정용 */
    private Integer groupWorkDay;

    /* ===== 오늘 CommuteRecord (LEFT JOIN cr.workDate = :date 로 파티션 프루닝) ===== */

    /* 오늘 출퇴근 레코드 PK (미출근 시 null — 이 값으로 레코드 존재 여부 판정) */
    private Long comRecId;
    /* 출근 시각 (ABSENT 레코드이거나 미출근 시 null) */
    private LocalDateTime checkInAt;
    /* 퇴근 시각 (체크아웃 전엔 null) */
    private LocalDateTime checkOutAt;
    /* 체크인 IP — 로그/drilldown 표시용 */
    private String checkInIp;
    /* 하루 최종 근태 상태 (미출근 시 null, ABSENT 배치 삽입 후에는 ABSENT) */
    private WorkStatus workStatus;
    /* 휴일 이유 */
    private HolidayReason holidayReason;

    /* ===== 후처리로 채워지는 필드 (맵 병합 단계) ===== */

    /* 오늘 APPROVED 휴가 존재 여부 */
    private Boolean hasApprovedVacationToday;
    /* 오늘 APPROVED 휴가 유형명 (VacationType.typeName). 없으면 null */
    private String vacationTypeName;
    /* 오늘 APPROVED 초과근무 분 (otPlanStart~otPlanEnd 합산). 없으면 0 */
    private Long approvedOtMinutesToday;
    /* 이번주(월~일) 누적 근무 분 (CommuteRecord checkOut-checkIn 합산). 없으면 0 */
    private Long weekWorkedMinutes;
}
