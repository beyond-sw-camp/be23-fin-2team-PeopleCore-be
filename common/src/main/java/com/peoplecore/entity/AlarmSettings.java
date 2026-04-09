package com.peoplecore.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder        //개인별 알람설정
public class AlarmSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long alarmId;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private Long empId;

    @Column(length = 50)
    private String service;
    @Column(length = 50)
    private String eventTypes;

    @Builder.Default
    private Boolean emailEnabled = true;
    @Builder.Default
    private Boolean pushEnabled = true;
    @Builder.Default
    private Boolean popupEnabled = true;
}
