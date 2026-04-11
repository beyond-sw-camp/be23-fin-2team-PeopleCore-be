package com.peoplecore.calendar.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InterestCalendarUpdateReqDto {

    private String displayColor;
    private Boolean isVisible;
    private Integer sortOrder;

}
