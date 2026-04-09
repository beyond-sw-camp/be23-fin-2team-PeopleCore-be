package com.peoplecore.alarm.dto;

import com.peoplecore.entity.CommonAlarm;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlarmListResponseDto {
    private Long alarmId;
    private String alarmType;
    private String alarmTitle;
    private String alarmContent;
    private String alarmLink;
    private String alarmRefType;
    private Long alarmRefId;
    private Boolean alarmIsRead;
    private LocalDateTime createdAt;

    public static AlarmListResponseDto from(CommonAlarm alarm) {
        return AlarmListResponseDto.builder()
                .alarmId(alarm.getAlarmId())
                .alarmType(alarm.getAlarmType())
                .alarmTitle(alarm.getAlarmTitle())
                .alarmContent(alarm.getAlarmContent())
                .alarmLink(alarm.getAlarmLink())
                .alarmRefType(alarm.getAlarmRefType())
                .alarmRefId(alarm.getAlarmRefId())
                .alarmIsRead(alarm.getAlarmIsRead())
                .createdAt(alarm.getCreatedAt())
                .build();
    }
}
