package com.peoplecore.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder    //공휴일
public class Holidays extends BaseTimeEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long holidayId;

    @Column(nullable = false)
    private LocalDate date;

    private String holidayName;

    @Enumerated(EnumType.STRING)
    private HolidayType holidayType;

    private Boolean isRepeating;
    private UUID companyId;

    @Column(nullable = false)
    private Long empId;

    private Long empModifyId;

}
