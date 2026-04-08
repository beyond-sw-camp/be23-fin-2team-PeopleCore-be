package com.peoplecore.calendar.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnnualLeaveSettingResDto {
//    연차연동설정

    private List<Long> linkedCalendarIds;
    private Boolean isPublic;
}
