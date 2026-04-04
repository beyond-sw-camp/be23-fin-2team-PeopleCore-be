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
    private String    empAddressBase;
    private String    empAddressDetail;

    // 소속 및 고용 정보
    private LocalDate empHireDate;
    private String    empType;
    private String    deptName;
    private String    gradeName;
    private String    titleName;
    private String    empStatus;

    // 시스템 계정 정보
    private String    empNum;
    private String    empEmail;
    private String    empMailboxSize;

    // 권한 정보
    private String    empRole;


    public static EmpDetailResponseDto from(Employee emp){
        return EmpDetailResponseDto.builder()
                .empName(emp.getEmpName())
                .empNameEn(emp.getEmpNameEn())
                .empBirthDate(emp.getEmpBirthDate())
                .empGender(emp.getEmpGender().name())
                .empPhone(emp.getEmpPhone())
                .empPersonalEmail(emp.getEmpPersonalEmail())
                .empAddressBase(emp.getEmpAddressBase())
                .empAddressDetail(emp.getEmpAddressDetail())
                .empHireDate(emp.getEmpHireDate())
                .empType(emp.getEmpType().name())
                .deptName(emp.getDept().getDeptName())
                .gradeName(emp.getGrade().getGradeName())
                .titleName(emp.getTitle().getTitleName())
                .empStatus(emp.getEmpStatus().name())
                .empNum(emp.getEmpNum())
                .empEmail(emp.getEmpEmail())
                .empMailboxSize(emp.getEmpMailboxSize())
                .empRole(emp.getEmpRole().name())
                .build();
    }
}

