package com.peoplecore.calendar.entity;

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
@Table(name = "interest_calendars")    // 관심캘린더 뷰설정
public class InterestCalendars {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long interestCalendarsId;

//    보는사원ID - API조회
    private Long viewerEmpId;

//    보여지는사원ID - API조회
    private Long targetEmpId;

    private Boolean isVisible;

    @Column(length = 7)
    private String shareDisplayColor;

//    사이드바순서
    private Integer sortOrder;

//    생성일시 - 승인시
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn( nullable = false)
    private CalendarShareRequests calendarShareRequest;


    public void updateColor(String color){
        this.shareDisplayColor = color;
    }
    public void toggleVisible(){
        this.isVisible = !Boolean.TRUE.equals(this.isVisible);
    }
    public void updateSortOrder(Integer sortOrder){
        this.sortOrder = sortOrder;
    }
}
