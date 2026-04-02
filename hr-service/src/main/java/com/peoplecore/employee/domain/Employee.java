package com.peoplecore.employee.domain;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "employee")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "emp_id")
    private Long empId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "dept_id", nullable = false)
    private Long deptId;

    @Column(name = "grade_id", nullable = false)
    private Long gradeId;

    @Column(name = "title_id", nullable = false)
    private Long titleId;

    @Column(name = "job_types_id", nullable = false)
    private Long jobTypesId;

    @Column(name = "emp_name", nullable = false, length = 50)
    private String empName;

    @Column(name = "emp_email", nullable = false, updatable = false)
    private String empEmail;

    @Column(name = "emp_phone", nullable = false)
    private String empPhone;

    @Column(name = "emp_num", nullable = false, length = 20)
    private String empNum;

    @Column(name = "emp_hire_date", nullable = false)
    private LocalDate empHireDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "emp_type", nullable = false)
    private EmpType empType;

    @Column(name = "emp_resign")
    private LocalDate empResign;

    @Enumerated(EnumType.STRING)
    @Column(name = "emp_status", nullable = false)
    private EmpStatus empStatus;

    @Column(name = "emp_profile_image_url", length = 500)
    private String empProfileImageUrl;

    @Column(name = "emp_password", nullable = false)
    private String empPassword;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "emp_role", nullable = false)
    @Builder.Default
    private EmpRole empRole = EmpRole.EMPLOYEE;

    @Column(name = "simple_password")
    private String simplePassword;

    public void updateLastLoginAt() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void changePassword(String encodedPassword) {
        this.empPassword = encodedPassword;
    }

    public void updateRole(EmpRole role) {
        this.empRole = role;
    }
}