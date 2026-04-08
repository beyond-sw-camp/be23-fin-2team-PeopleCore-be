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
public class AnnualLeaveSettingReqDto {
//    연차연동설정

//    연동할 캘린더 ID 목록
    private List<Long> calendarIds;
    private Boolean isPublic;
}
