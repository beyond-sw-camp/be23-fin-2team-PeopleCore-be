package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AlarmEvent {
    private UUID companyId;
    /*Approval, attendance, board, Hr, System*/
    private String alarmType;

    private String alarmTitle;

    private String alarmContent;
    private String alarmLink;
    private String alarmRefType;
    private Long alarmRefId;

    private List<Long> empIds;
}
