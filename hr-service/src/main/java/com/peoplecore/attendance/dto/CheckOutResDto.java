package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.CheckOutStatus;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.HolidayReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 퇴근 체크아웃 응답 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckOutResDto {

    /** 출퇴근 기록 PK */
    private Long comRecId;

    /** 근무일자 */
    private LocalDate workDate;

    /** 체크인 시각 */
    private LocalDateTime checkInAt;

    /** 체크아웃 시각 */
    private LocalDateTime checkOutAt;

    /** 체크아웃 IP */
    private String checkOutIp;

    /** 당일 근무 분 (raw, 휴게 미차감). 휴게/인정 조정은 배치가 처리 */
    private Long workedMinutes;

    /** 체크인 시점 근무지 외 여부 (참고용) */
    private Boolean isOffsite;

    /** 체크아웃 상태 (EARLY_LEAVE/ON_TIME/HOLIDAY_WORK_END) */
    private CheckOutStatus checkOutStatus;

    /** 체크인 당시 휴일 이유 (참고용) */
    private HolidayReason holidayReason;

    public static CheckOutResDto fromEntity(CommuteRecord r) {
        long minutes = (r.getComRecCheckIn() != null && r.getComRecCheckOut() != null)
                ? Duration.between(r.getComRecCheckIn(), r.getComRecCheckOut()).toMinutes()
                : 0L;
        return CheckOutResDto.builder()
                .comRecId(r.getComRecId())
                .workDate(r.getWorkDate())
                .checkInAt(r.getComRecCheckIn())
                .checkOutAt(r.getComRecCheckOut())
                .checkOutIp(r.getCheckOutIp())
                .workedMinutes(minutes)
                .isOffsite(r.getIsOffsite())
                .checkOutStatus(r.getCheckOutStatus())
                .holidayReason(r.getHolidayReason())
                .build();
    }
}
