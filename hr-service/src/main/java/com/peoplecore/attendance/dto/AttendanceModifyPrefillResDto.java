package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 근태 정정 모달 프리필 Response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceModifyPrefillResDto {

    /* collab ApprovalForm PK — hr-service 가 formCode=ATTENDANCE_MODIFY 로 조회 (회사별 캐시) */
    private Long formId;

    /* 양식 코드 — 고정값 "ATTENDANCE_MODIFY" */
    private String formCode;

    /* CommuteRecord PK — 모달 hidden → doc_data.comRecId */
    private Long comRecId;

    /* 대상 근무 일자 — 모달 workDate 확정값 */
    private LocalDate workDate;

    /* 현재 출근 시각 (nullable) */
    private LocalDateTime currentCheckIn;

    /* 현재 퇴근 시각 (nullable) */
    private LocalDateTime currentCheckOut;

    /* 자동마감 여부 — true 면 모달에 "자동마감 복구" 뱃지 노출 권장 */
    private Boolean isAutoClosed;

    /* 근태 상태 라벨 — "정상"/"지각"/"자동마감" 등 WorkStatus.getLabel() 값 (모달 태그 UI, nullable) */
    private String workStatusLabel;

    /* 신청자 사원 ID */
    private Long empId;

    /* 신청자 이름 */
    private String empName;

    /*신청자 부서명 */
    private String deptName;

    /* 신청자 직급명 */
    private String gradeName;

    /* 신청자 직책명 */
    private String titleName;
}