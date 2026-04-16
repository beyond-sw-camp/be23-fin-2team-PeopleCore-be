package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.ModifyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 근태 정정 목록 행 DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceModifyListResDto {

    /* AttendanceModify PK */
    private Long attenModiId;

    /* 연결 결재 문서 ID — 행 클릭 시 결재 상세 딥링크 */
    private Long approvalDocId;

    /*대상 근무 일자 */
    private LocalDate workDate;

    /* 신청자 이름 (스냅샷) */
    private String attenEmpName;

    /* 신청자 부서 (스냅샷) */
    private String attenEmpDeptName;

    /* 신청자 직급 (스냅샷) */
    private String attenEmpGrade;

    /* 요청 출근 시각 (nullable) */
    private LocalDateTime attenReqCheckIn;

    /* 요청 퇴근 시각 (nullable) */
    private LocalDateTime attenReqCheckOut;

    /* 정정 사유 */
    private String attenReason;

    /*처리 상태 */
    private ModifyStatus attenStatus;

    /* 신청 일시 */
    private LocalDateTime createdAt;
}