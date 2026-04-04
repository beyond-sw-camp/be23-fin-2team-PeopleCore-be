package com.peoplecore.calendar.entity;

import com.peoplecore.calendar.enums.Frequency;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "recurrence_rules")    //반복규칙
public class RepeatedRules {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long repeatedRulesId;

    @Enumerated(EnumType.STRING)
    private Frequency frequency;

//   간격
    private Integer intervalVal;

//    요일별
    @Column(length = 50)
    private String byDay;

//    일별
    @Column( length = 50)
    private String byMonthDay;

//    반복종료일
    private LocalDate until;

//    반복횟수
    private Integer count;

    @Column(nullable = false)
    private UUID companyId;

}
