package com.peoplecore.calendar.entity;

import com.peoplecore.calendar.enums.EventsNotiMethod;
import com.peoplecore.calendar.enums.HolidayType;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventsNotifications {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    private EventsNotiMethod eventsNotiMethod;

    private Integer minutesBefore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn( nullable = false)
    private Events events;

    @Getter
    @Entity
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Table(name = "holidays")
    public static class Holidays extends BaseTimeEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long holidaysId;

        @Column(nullable = false)
        private LocalDate date;

        @Column(length = 100)
        private String name;

    //    구분 - 법정공휴일, 사내일정
        @Enumerated(EnumType.STRING)
        private HolidayType holidayType;

    //    반복여부
        private Boolean isRepeating;

        @Column(nullable = false)
        private UUID companyId;

    //    /** 등록자 id */
        @Column(nullable = false)
        private Long empId;

    //    /** 수정자 id */
        private Long empModifyId;

    }
}
