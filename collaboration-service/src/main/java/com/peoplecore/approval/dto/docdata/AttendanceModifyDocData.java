package com.peoplecore.approval.dto.docdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/* 근태정정(ATTENDANCE_MODIFY) docData 파싱 대상 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttendanceModifyDocData {
    /* 대상 출퇴근 기록 PK */
    private Long comRecId;
    /* 근무 일자 */
    private LocalDate workDate;
    /* 정정 요청 출근 시각 */
    private LocalDateTime attenReqCheckIn;
    /* 정정 요청 퇴근 시각 */
    private LocalDateTime attenReqCheckOut;
    /* 정정 사유 */
    private String attenReason;
}
