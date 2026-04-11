package com.peoplecore.alarm.dto;

import com.peoplecore.entity.AlarmSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmSettingsResDto {
    private Long alarmId;
    private String service;
    private String eventTypes;
    private Boolean emailEnabled;
    private Boolean pushEnabled;
    private Boolean popupEnabled;

    public static AlarmSettingsResDto fromEntity(AlarmSettings settings){
        return AlarmSettingsResDto.builder()
                .alarmId(settings.getAlarmId())
                .service(settings.getService())
                .eventTypes(settings.getEventTypes())
                .emailEnabled(settings.getEmailEnabled())
                .pushEnabled(settings.getPushEnabled())
                .popupEnabled(settings.getPopupEnabled())
                .build();
    }
}
