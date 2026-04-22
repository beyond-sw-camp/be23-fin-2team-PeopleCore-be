package com.peoplecore.calendar.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "my_calendars")
public class MyCalendars extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long myCalendarsId;

    @Column(nullable = false)
    private Long empId;

    @Column(nullable = false, length = 100)
    private String calendarName;

    @Column(length = 7)
    private String myDisplayColor;

    private Boolean isVisible;
    private Integer sortOrder;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    //일정 공개 여부
    @Column(nullable = false)
    @Builder.Default
    private Boolean isPublic = true;


    public void updateName(String calendarName){
        this.calendarName = calendarName;
    }
    public void updateColor(String color){
        this.myDisplayColor = color;
    }
    public void toggleVisible(){
        this.isVisible = !Boolean.TRUE.equals(this.isVisible);
    }
    public void updateSortOrder(Integer sortOrder){
        this.sortOrder = sortOrder;
    }

    public boolean isDefaultCalendar(){
        return Boolean.TRUE.equals(this.isDefault);
    }
}
