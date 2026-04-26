package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.HolidayReason;
import com.peoplecore.attendance.entity.WorkStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/* 출근 체크인 응답 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInResDto {

    /* 출퇴근 기록 PK */
    private Long comRecId;

    /* 근무일자 */
    private LocalDate workDate;

    /* 체크인 시각 */
    private LocalDateTime checkInAt;

    /* 체크인 IP */
    private String checkInIp;

    /* 하루 초기 근태 상태 (NORMAL/LATE/HOLIDAY_WORK) */
    private WorkStatus workStatus;

    /* 휴일 이유 (NATIONAL/COMPANY/WEEKLY_OFF). 평일이면 null */
    private HolidayReason holidayReason;

    public static CheckInResDto fromEntity(CommuteRecord r) {
        return CheckInResDto.builder()
                .comRecId(r.getComRecId())
                .workDate(r.getWorkDate())
                .checkInAt(r.getComRecCheckIn())
                .checkInIp(r.getCheckInIp())
                .workStatus(r.getWorkStatus())
                .holidayReason(r.getHolidayReason())
                .build();
    }
}
