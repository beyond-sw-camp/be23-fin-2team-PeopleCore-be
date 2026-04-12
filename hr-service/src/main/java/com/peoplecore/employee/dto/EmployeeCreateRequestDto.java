package com.peoplecore.employee.dto;

import com.peoplecore.employee.domain.EmpGender;
import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.EmpType;
import com.peoplecore.employee.domain.PasswordIssueType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class EmployeeCreateRequestDto {

    @NotBlank
    private String empName;

    @NotBlank
    private String empNameEn;

    @NotNull
    private LocalDate empBirthDate;

    @NotNull
    private EmpGender empGender;

    @NotBlank
    private String empPhone;

    @NotBlank
    @Email
    private String empPersonalEmail;

    @NotBlank
    private String empZipCode;

    @NotBlank
    private String empAddressBase;


    private String empAddressDetail;

    //소속 및 고용 정보

    @NotNull
    private LocalDate empHireDate;

    @NotNull
    private EmpType empType;

    @NotNull
    private String deptName;

    @NotNull
    private String gradeName;

    @NotNull
    private String titleName;

//    권한설정
    @NotNull
    private EmpRole empRole;


    //시스템 계정 설정

    @NotNull
    private PasswordIssueType passwordIssueType;

    private String initialPassword;

    private String empMailboxSize;

    private Long workGroupId;  // 선택된 근무 그룹 ID (null이면 기본 그룹 자동 배정)

}

