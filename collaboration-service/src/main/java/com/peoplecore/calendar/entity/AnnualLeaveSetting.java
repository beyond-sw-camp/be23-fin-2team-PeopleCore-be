package com.peoplecore.calendar.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "annual_leave_settings")    //연차 연동 설정
public class AnnualLeaveSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long annualLeaveSettingsId;

    @Column(nullable = false)
    private Long empId;

    @Column(nullable = false)
    private UUID companyId;

//    연동할 캘린더 ID
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "my_calendars_id", nullable = false)
    private MyCalendars myCalendar;

//    연차 일정 공개 여부
    private Boolean isPublic;


    public void updatePublic(Boolean isPublic){
        this.isPublic = isPublic;
    }
}
