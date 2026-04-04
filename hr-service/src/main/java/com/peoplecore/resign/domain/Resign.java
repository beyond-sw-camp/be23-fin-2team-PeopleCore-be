package com.peoplecore.resign.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "resign")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Resign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resign_id")
    private Long resignId;

    @Column(name = "emp_id", nullable = false)
    private Long empId;

    @Column(name = "grade_id", nullable = false)
    private Long gradeId;

    @Column(name = "title_id", nullable = false)
    private Long titleId;

    @Column(name = "emp_name")
    private String empName;

    @Column(name = "resign_reason")
    private String resignReason;

    @Column(name = "resign_date")
    private LocalDate resignDate;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
