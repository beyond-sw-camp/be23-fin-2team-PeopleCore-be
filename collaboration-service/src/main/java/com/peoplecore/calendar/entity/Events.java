package com.peoplecore.calendar.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "events")
public class Events extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventsId;

    @Column(nullable = false)
    private Long empId;

    @Column(nullable = false)
    private String title;

    private String description;
    private String location;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

//     종일 여부 - 1 : 종일
    private Boolean isAllDay;

//     공개여부
    private Boolean isPublic;

//     삭제일시 - 소프트딜리트
    private LocalDateTime deletedAt;

//     전직원용 - 1 : 전직원
    private Boolean isAllEmployees;

    @Column(nullable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "my_calendars_id")
    private MyCalendars myCalendars;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repeated_rules_id", nullable = false)
    private RepeatedRules repeatedRules;

}
