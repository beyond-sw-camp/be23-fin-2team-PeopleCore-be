package com.peoplecore.employee.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.department.domain.Department;
import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.grade.domain.Grade;
import com.peoplecore.pay.domain.InsuranceJobTypes;
import com.peoplecore.title.domain.Title;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

//    부서
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurance_job_types", nullable = false)
    private InsuranceJobTypes jobTypes;

    @Column(name = "emp_name", nullable = false, length = 50)
    private String empName;

    @Column(name = "emp_email", nullable = false, updatable = false)
    private String empEmail;

    @Column(name = "emp_name_en", length = 100)
    private String empNameEn;

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

    @Column(nullable = false)
    private Long workGroupId;

    @Column(nullable = false)
    @Builder.Default
    private Integer dependentsCount = 1;

    @Column(nullable = false)
    @Builder.Default
    private Integer taxRateOption = 100;    //80 or 100 or 120

    @Column(nullable = false)
    @Builder.Default
    private RetirementType retirementType = RetirementType.DC;


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


    //하드코딩 커스텀 고려
    @Column(name = "emp_mailbox_size")
    @Builder.Default
    private String empMailboxSize= "5GB";

//    사원이 비밀번호 변경을 필수로 해야하는지 여부
    @Builder.Default
    @Column(nullable = false)
    private Boolean mustChangePassword = false;









    public void updateLastLoginAt() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void changePassword(String encodedPassword) {
        this.empPassword = encodedPassword;
    }

    public void updateRole(EmpRole role) {
        this.empRole = role;
    }

    public void clearMustChangePassword() {
        this.mustChangePassword = false;
    }
}