package com.peoplecore.alarm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmSettingsReqDto {
    private String service;
    private Boolean emailEnabled;
    private Boolean pushEnabled;
    private Boolean popupEnabled;
}
