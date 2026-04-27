package com.peoplecore.employee.dto;

import com.peoplecore.employee.domain.EmpGender;
import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.EmpType;
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

    @NotBlank
    private String empResidentNumber;

    //소속 및 고용 정보

    @NotNull
    private LocalDate empHireDate;

    @NotNull
    private EmpType empType;

    @NotNull
    private Long deptId;

    @NotNull
    private Long gradeId;

    @NotNull
    private Long titleId;

    @NotBlank
    private String insuranceJobTypeName;

//    권한설정
    @NotNull
    private EmpRole empRole;


    //시스템 계정 설정

    @NotBlank
    @Pattern(
            regexp = "^[a-z0-9._-]{3,15}$",
            message = "사내 이메일 아이디는 영문 소문자/숫자/._- 조합 3~15자여야 합니다"
    )
    private String empEmailLocal;

    @NotBlank
    private String initialPassword;

    private Long workGroupId;

}

