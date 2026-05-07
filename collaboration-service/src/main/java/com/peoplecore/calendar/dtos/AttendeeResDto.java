package com.peoplecore.calendar.dtos;


import com.peoplecore.calendar.entity.EventAttendees;
import com.peoplecore.calendar.enums.InviteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AttendeeResDto {
    private Long eventAttendeesId;
    private Long invitedEmpId;
    private String empName;
    private String departmentName;
    private InviteStatus inviteStatus; // PENDING/ACCEPTED/DECLINED/MAYBE
    private String rejectReason;
    private LocalDateTime invitedAt;
    private LocalDateTime respondedAt;

    public static AttendeeResDto fromEntity(EventAttendees a) {
        return AttendeeResDto.builder()
                .eventAttendeesId(a.getEvent_attendees_id())
                .invitedEmpId(a.getInvitedEmpId())
                .inviteStatus(a.getInviteStatus())
                .rejectReason(a.getRejectReason())
                .invitedAt(a.getInvitedAt())
                .respondedAt(a.getRespondedAt())
                .build();
    }
}
