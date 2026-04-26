package com.peoplecore.employee.dto;

import com.peoplecore.employee.domain.Employee;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.checkerframework.checker.units.qual.N;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class EmpDetailResponseDto {

    private String    empName;
    private String    empNameEn;
    private LocalDate empBirthDate;
    private String    empGender;
    private String    empPhone;
    private String    empPersonalEmail;
    private String    empZipCode;
    private String    empAddressBase;
    private String    empAddressDetail;
    private String    empResidentNumber;

    // 소속 및 고용 정보
    private LocalDate empHireDate;
    private String    empType;
    private String    jobType;
    private Long      deptId;
    private String    deptName;
    private Long      gradeId;
    private String    gradeName;
    private Long      titleId;
    private String    titleName;
    private String    insuranceJobTypeName;
    private String    empStatus;

    // 시스템 계정 정보
    private String    empNum;
    private String    empEmail;

    // 권한 정보
    private String    empRole;


    public static EmpDetailResponseDto from(Employee emp){
        return EmpDetailResponseDto.builder()
                .empName(emp.getEmpName())
                .empNameEn(emp.getEmpNameEn())
                .empBirthDate(emp.getEmpBirthDate())
                .empGender(emp.getEmpGender() != null ? emp.getEmpGender().name() : null)
                .empPhone(emp.getEmpPhone())
                .empPersonalEmail(emp.getEmpPersonalEmail())
                .empZipCode(emp.getEmpZipCode())
                .empAddressBase(emp.getEmpAddressBase())
                .empAddressDetail(emp.getEmpAddressDetail())
                .empResidentNumber(emp.getEmpResidentNumber())
                .empHireDate(emp.getEmpHireDate())
                .empType(emp.getEmpType() != null ? emp.getEmpType().name() : null)
                .deptId(emp.getDept() != null ? emp.getDept().getDeptId() : null)
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .gradeId(emp.getGrade() != null ? emp.getGrade().getGradeId() : null)
                .gradeName(emp.getGrade() != null ? emp.getGrade().getGradeName() : null)
                .titleId(emp.getTitle() != null ? emp.getTitle().getTitleId() : null)
                .titleName(emp.getTitle() != null ? emp.getTitle().getTitleName() : null)
                .insuranceJobTypeName(emp.getJobTypes() != null ? emp.getJobTypes().getJobTypeName() : null)
                .empStatus(emp.getEmpStatus() != null ? emp.getEmpStatus().name() : null)
                .empNum(emp.getEmpNum())
                .empEmail(emp.getEmpEmail())
                .empRole(emp.getEmpRole() != null ? emp.getEmpRole().name() : null)
                .build();
    }
}

