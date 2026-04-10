package com.peoplecore.calendar.dtos;

import com.peoplecore.calendar.entity.Events;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyEventResDto {

    private Long eventsId;
    private String title;
    private String description;
    private String location;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean isAllDay;
    private UUID companyId;
    private String creatorName;
    private LocalDateTime createAt;

    public static CompanyEventResDto fromEntity(Events event, String creatorName){
        return CompanyEventResDto.builder()
                .eventsId(event.getEventsId())
                .title(event.getTitle())
                .description(event.getDescription())
                .location(event.getLocation())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .isAllDay(event.getIsAllDay())
                .companyId(event.getCompanyId())
                .creatorName(creatorName)
                .createAt(event.getCreatedAt())
                .build();
    }
}
