package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 근태 정정 결재 문서 생성 이벤트 (collaboration-service → hr-service).
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AttendanceModifyDocCreatedEvent {

    /* 회사 ID */
    private UUID companyId;

    /* collab ApprovalDocument PK — result 이벤트에서도 같은 값으로 역조회 */
    private Long approvalDocId;

    /* 기안자(신청자) 사원 ID */
    private Long empId;

    /* 대상 CommuteRecord PK — doc_data 에서 파싱 */
    private Long comRecId;

    /* 대상 근무 일자 — CommuteRecord.workDate 동일값 */
    private LocalDate workDate;

    /* 요청 출근 시각 (nullable — 퇴근만 정정 시) */
    private LocalDateTime attenReqCheckIn;

    /* 요청 퇴근 시각 (nullable — 출근만 정정 시) */
    private LocalDateTime attenReqCheckOut;

    /* 정정 사유 */
    private String attenReason;
}