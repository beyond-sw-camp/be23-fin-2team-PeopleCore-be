package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.WorkStatus;
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

    /* 자동마감 여부 — true 면 모달에 "자동마감 복구" 뱃지 노출 권장 (workStatus == AUTO_CLOSED 파생 편의 필드) */
    private Boolean isAutoClosed;

    /* 근태 상태 enum — FE 스타일 매핑/뱃지 키 (i18n 안전). nullable */
    private WorkStatus workStatus;

    /* 근태 상태 한글 라벨 — workStatus.getLabel() 값 (deprecated 예정, FE enum 전환 후 제거) */
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