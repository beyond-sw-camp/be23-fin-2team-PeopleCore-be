package com.peoplecore.employee.domain;

import com.peoplecore.common.entity.BaseTimeEntity;
import com.peoplecore.company.domain.Company;
import com.peoplecore.department.domain.Department;
import com.peoplecore.grade.domain.Grade;
import com.peoplecore.title.domain.Title;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "employee", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"company_id", "emp_email"}),
        @UniqueConstraint(columnNames = "emp_num")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Employee extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "emp_id")
    private Long empId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
//    삭제 및 변경

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dept_id", nullable = false)
    private Department dept;

//  직급
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

//    직위
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;

// 업종(산재보험용)
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "jobTypes_id", nullable = false)
//    private JobTypes jobTypes;

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

    @Column(name = "emp_birth_date")
    private LocalDate empBirthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "emp_gender")
    private EmpGender empGender;

    @Column(name = "emp_personal_email")
    private String empPersonalEmail;

    @Column(name = "emp_zip_code")
    private String empZipCode;

    @Column(name = "emp_address_base")
    private String empAddressBase;

    @Column(name = "emp_address_detail")
    private String empAddressDetail;

    @Column(name = "emp_mailbox_size")
    private String empMailboxSize;


    








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