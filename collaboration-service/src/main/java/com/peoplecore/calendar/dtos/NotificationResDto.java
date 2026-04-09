package com.peoplecore.calendar.dtos;

import com.peoplecore.calendar.entity.EventsNotifications;
import com.peoplecore.calendar.enums.EventsNotiMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResDto {

    private Long notificationId;
    private EventsNotiMethod method;
    private Integer minutesBefore;

    public static NotificationResDto fromEntity(EventsNotifications n) {
        return NotificationResDto.builder()
                .notificationId(n.getNotificationId())
                .method(n.getEventsNotiMethod())
                .minutesBefore(n.getMinutesBefore())
                .build();
    }
}
