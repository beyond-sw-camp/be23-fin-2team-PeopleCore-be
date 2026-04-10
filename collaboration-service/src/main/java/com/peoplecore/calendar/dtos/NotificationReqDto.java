package com.peoplecore.calendar.dtos;

import com.peoplecore.calendar.enums.EventsNotiMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationReqDto {

    private EventsNotiMethod method;
    private Integer minutesBefore;  //n분전

}
