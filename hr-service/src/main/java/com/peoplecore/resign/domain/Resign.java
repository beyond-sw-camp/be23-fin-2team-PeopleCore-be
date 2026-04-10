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

    @Column(name = "processed_at")
    private LocalDateTime processedAt; //퇴직처리 일시

    @Column(name = "doc_id")
    private Long docId; //결제문서 id(상세)

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status")
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING; //결제대기 기본값

    @Enumerated(EnumType.STRING)
    @Column(name = "retire_status")
    @Builder.Default
    private RetireStatus retireStatus = RetireStatus.ACTIVE; //재직자 기본값

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false; //softDelete

    @Column(name = "registered_date")
    private LocalDate registeredDate; //신청일


    public void processRetire() {
        this.retireStatus = RetireStatus.RESIGNED;
        this.processedAt = LocalDateTime.now();
    }

    public void softDelete(){
        this.isDeleted = true;
    }

}