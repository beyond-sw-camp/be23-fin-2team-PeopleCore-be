package com.peoplecore.calendar.dtos;

import com.peoplecore.calendar.entity.InterestCalendars;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterestCalendarResDto {

    private Long interestCalendarId;
    private Long targetEmpId;
    private String targetEmpName;
    private String displayColor;
    private Boolean isVisible;
    private Integer sortOrder;
    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;

    public static InterestCalendarResDto fromEntity(InterestCalendars i, String empName){
        return InterestCalendarResDto.builder()
                .interestCalendarId(i.getInterestCalendarsId())
                .targetEmpId(i.getTargetEmpId())
                .targetEmpName(empName)
                .displayColor(i.getShareDisplayColor())
                .isVisible(i.getIsVisible())
                .sortOrder(i.getSortOrder())
                .requestedAt(i.getCalendarShareRequest().getRequestedAt())
                .respondedAt(i.getCalendarShareRequest().getRespondedAt())
                .build();
    }
}
