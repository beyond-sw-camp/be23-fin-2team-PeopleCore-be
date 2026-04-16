package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.ModifyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 근태 정정 상세 Response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceModifyResDto {

    /* AttendanceModify PK */
    private Long attenModiId;

    /* 연결된 collab 문서 ID — 결재 상세 딥링크 */
    private Long approvalDocId;

    /* 대상 CommuteRecord PK */
    private Long comRecId;

    /* 대상 근무 일자 */
    private LocalDate workDate;

    /* 신청자 사원 ID */
    private Long empId;

    /* 신청자 이름 (스냅샷) */
    private String attenEmpName;

    /* 신청자 부서 (스냅샷) */
    private String attenEmpDeptName;

    /* 신청자 직급 (스냅샷) */
    private String attenEmpGrade;

    /* 신청자 직책 (스냅샷) */
    private String attenEmpTitle;

    /* 요청 출근 시각 (nullable) */
    private LocalDateTime attenReqCheckIn;

    /* 요청 퇴근 시각 (nullable) */
    private LocalDateTime attenReqCheckOut;

    /* 정정 사유 */
    private String attenReason;

    /* 처리 상태 */
    private ModifyStatus attenStatus;

    /* 처리자 사원 ID — PENDING/CANCELED 에선 null */
    private Long managerId;

    /* 처리자 이름 — PENDING/CANCELED 에선 null */
    private String managerName;

    /* 반려 사유 — REJECTED 에서만 값 */
    private String attenRejectReason;

    /* 신청 일시 */
    private LocalDateTime createdAt;

    /* 최종 처리 일시 */
    private LocalDateTime updatedAt;
}