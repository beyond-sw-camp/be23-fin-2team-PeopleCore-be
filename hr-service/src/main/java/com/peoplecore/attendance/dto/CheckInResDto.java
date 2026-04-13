package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.CheckInStatus;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.HolidayReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 출근 체크인 응답 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInResDto {

    /** 출퇴근 기록 PK */
    private Long comRecId;

    /** 근무일자 */
    private LocalDate workDate;

    /** 체크인 시각 */
    private LocalDateTime checkInAt;

    /** 체크인 IP */
    private String checkInIp;

    /** 근무지 외 여부 (허용 IP 대역 밖) */
    private Boolean isOffsite;

    /** 체크인 상태 (ON_TIME/LATE/HOLIDAY_WORK) */
    private CheckInStatus checkInStatus;

    /** 휴일 이유 (NATIONAL/COMPANY/WEEKLY_OFF). 평일이면 null */
    private HolidayReason holidayReason;

    public static CheckInResDto fromEntity(CommuteRecord r) {
        return CheckInResDto.builder()
                .comRecId(r.getComRecId())
                .workDate(r.getWorkDate())
                .checkInAt(r.getComRecCheckIn())
                .checkInIp(r.getCheckInIp())
                .isOffsite(r.getIsOffsite())
                .checkInStatus(r.getCheckInStatus())
                .holidayReason(r.getHolidayReason())
                .build();
    }
}
