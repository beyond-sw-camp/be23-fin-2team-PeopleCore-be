package com.peoplecore.calendar.dtos;

import com.peoplecore.calendar.entity.CalendarShareRequests;
import com.peoplecore.calendar.enums.ShareStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareRequestResDto {

    private Long calendarShareReqId;
    private Long fromEmpId;
    private String fromEmpName;
    private Long toEmpId;
    private String toEmpName;
    private ShareStatus shareStatus;
    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;

    public static  ShareRequestResDto fromEntity(CalendarShareRequests req, String fromName, String toName) {

        return ShareRequestResDto.builder()
                .calendarShareReqId(req.getCalendarShareReqid())
                .fromEmpId(req.getFromEmpId())
                .fromEmpName(fromName)
                .toEmpId(req.getToEmpId())
                .toEmpName(toName)
                .shareStatus(req.getShareStatus())
                .requestedAt(req.getRequestedAt())
                .respondedAt(req.getRespondedAt())
                .build();
    }
}
