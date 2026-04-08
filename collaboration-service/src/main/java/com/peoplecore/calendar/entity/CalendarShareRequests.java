package com.peoplecore.calendar.entity;

import com.peoplecore.calendar.enums.Permission;
import com.peoplecore.calendar.enums.ShareStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "calendar_share_requests")    //캘린더 공유 요청
public class CalendarShareRequests {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long calendarShareReqid;

//  요청사원ID
    private Long fromEmpId;

//  수신사원ID
    private Long toEmpId;

//  권한
    @Enumerated(EnumType.STRING)
    private Permission permission;

//   요청상태
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ShareStatus shareStatus = ShareStatus.PENDING;

    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;

    @Column(nullable = false)
    private UUID companyId;


    public void approve(){
        this.shareStatus = ShareStatus.APPROVED;
        this.respondedAt = LocalDateTime.now();
    }
    public void reject(){
        this.shareStatus = ShareStatus.REJECTED;
        this.respondedAt = LocalDateTime.now();
    }
    public void cancel(){
        this.shareStatus = ShareStatus.CANCELLED;
        this.respondedAt = LocalDateTime.now();
    }
}
