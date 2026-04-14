package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.OtStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/* 초과근무 관리(관리자) 화면의 행 DTO
 * 탭: 전체 / 승인대기 / 승인완료 / 반려 — 모든 탭이 동일한 행 구조 사용 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeRequestAdminRowResDto {

    /* 초과근무 신청 PK — 상세 모달/결재문서 딥링크 진입 키 */
    private Long otId;

    /* 신청자 사번 — 사원 상세 진입에 사용 */
    private Long empId;

    /* 신청자 이름 (화면 1열) */
    private String empName;

    /* 부서명 (Employee.dept fetch join 결과) */
    private String deptName;

    /* 근무 유형 — 연장근무 / 야간근무 / 휴일근무 (요일·시간대로 분류) */
    private String otType;

    /* 신청 기준 날짜 (yyyy-MM-dd) */
    private LocalDate otDate;

    /* 신청 시간 라벨 — 예 "2h 30m" (otPlanEnd - otPlanStart) */
    private String durationLabel;

    /* 신청 시간 분 단위 — 정렬/합산 보조용 */
    private Long durationMinutes;

    /* 신청 사유 (otReason 원문) */
    private String otReason;

    /* 신청 상태 — PENDING/APPROVED/REJECTED/CANCELED (결재 결과 캐시) */
    private OtStatus otStatus;

    /* 결재 문서 ID — 결재 화면 딥링크 (사전 신청 시점에는 null 가능) */
    private Long approvalDocId;
}
