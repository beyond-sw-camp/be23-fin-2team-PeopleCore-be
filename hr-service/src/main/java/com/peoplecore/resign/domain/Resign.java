package com.peoplecore.resign.domain;

import com.peoplecore.department.domain.Department;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.grade.domain.Grade;
import com.peoplecore.title.domain.Title;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dept_id", nullable = false)
    private Department department;

    @Column(name = "emp_name")
    private String empName;

    @Column(name = "resign_reason")
    private String resignReason; //퇴직사유

    @Column(name = "resign_date")
    private LocalDate resignDate; //퇴직 예정일

    @Column(name = "processed_at")
    private LocalDateTime processedAt; //

    @Column(name = "doc_ id")
    private Long docId;
}
