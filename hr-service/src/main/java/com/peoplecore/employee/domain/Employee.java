package com.peoplecore.employee.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.department.domain.Department;
import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.grade.domain.Grade;
import com.peoplecore.pay.domain.InsuranceJobTypes;
import com.peoplecore.title.domain.Title;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.cglib.core.Local;

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
    @JoinColumn(name = "title_id")
    private Title title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurance_job_types", nullable = true)
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
    private LocalDate empResignDate;

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


    @Column(name = "emp_mailbox_size")
    @Builder.Default
    private String empMailboxSize= "5GB";

//    사원이 비밀번호 변경을 필수로 해야하는지 여부
    @Builder.Default
    @Column(nullable = false)
    private Boolean mustChangePassword = false;

//    사원 softDelete
    @Column(name = "delete_at")
    private LocalDate deleteAt;

//    계약 만료일
    @Column(name="contract_end_date")
    private LocalDate contractEndDate;





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

    public void updateInfo(String empName, String empNameEn, LocalDate empBirthDate,
                           EmpGender empGender, String empPhone, String empPersonalEmail,
                           String empZipCode, String empAddressBase, String empAddressDetail,
                           LocalDate empHireDate, EmpType empType,
                           Department dept, Grade grade, Title title,
                           EmpRole empRole, String empMailboxSize) {
        this.empName = empName;
        this.empNameEn = empNameEn;
        this.empBirthDate = empBirthDate;
        this.empGender = empGender;
        this.empPhone = empPhone;
        this.empPersonalEmail = empPersonalEmail;
        this.empZipCode = empZipCode;
        this.empAddressBase = empAddressBase;
        this.empAddressDetail = empAddressDetail;
        this.empHireDate = empHireDate;
        this.empType = empType;
        this.dept = dept;
        this.grade = grade;
        this.title = title;
        this.empRole = empRole;
        this.empMailboxSize = empMailboxSize;
    }


//    사원 softdelete -> db영구 삭제 기한 고려
    public void softDelete(){
        if(this.empStatus != EmpStatus.RESIGNED){
            throw new IllegalStateException("퇴직 상태인 사원만 삭제가 가능합니다");
        }
        this.deleteAt = LocalDate.now(); //날짜로만 표시
    }

//    사원 삭제 여부 조회 편의용
    public boolean isDelete(){
        return this.deleteAt !=null;
    }

//    재직상태 변경
    public void updateStatus(EmpStatus status){
        this.empStatus = status;
    }

//    퇴직일 세팅
    public void updateResignDate(LocalDate resignDate){
        this.empResignDate = resignDate;
    }
//    일괄 사원정보 업데이트(인사발령)
    public void updateDept(Department department){
        this.dept = department;
    }
    public void updateGrade(Grade grade){
        this.grade =grade;
    }
    public void updateTitle(Title title){
        this.title = title;
    }

}