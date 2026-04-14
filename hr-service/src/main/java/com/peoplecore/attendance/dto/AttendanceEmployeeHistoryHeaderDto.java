package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.AttendanceCardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사원 일별 근무 현황(상세 모달) 상단 4카드 영역.
 * - 주간 근무시간: 요청 date 가 속한 주(월~일) 실근무분 합
 * - 카테고리: 호출자가 쿼리로 넘긴 cardType 을 그대로 에코 (드릴다운 컨텍스트 유지)
 * - 52시간 현황: OvertimePolicy.otPolicyWeeklyMaxHour 기준 정상/경고/초과
 * - 사유/비고 컬럼은 프론트에서 제거하기로 합의 → 응답 미포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceEmployeeHistoryHeaderDto {

    /** 사원 PK */
    private Long empId;

    /** 사번 */
    private String empNum;

    /** 사원명 */
    private String empName;

    /** 부서명 (nullable — 미배정 사원 대응) */
    private String deptName;

    /** 직급명 (nullable) */
    private String gradeName;

    /** 주간 실근무 분 (월~일 check-in~check-out 합) */
    private Long weeklyWorkMinutes;

    /** 주간 실근무 포맷 문자열 "Xh Ym" */
    private String weeklyWorkText;

    /** 드릴다운 대상 카드 타입 (nullable — 호출자가 안 넘기면 null) */
    private AttendanceCardType cardType;

    /** 정책 주간 최대 근무시간 h */
    private int weeklyMaxHour;

    /** 정책 경고 기준 h */
    private int weeklyWarningHour;

    /** 52시간 현황 — "정상" / "경고" / "초과" */
    private String weeklyStatus;
}
