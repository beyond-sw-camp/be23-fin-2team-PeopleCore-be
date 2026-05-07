package com.peoplecore.employee.dto;

import com.peoplecore.employee.domain.EmpGender;
import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.EmpType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class EmployeeUpdateRequestDto {

    // 기본 인적사항
    @NotBlank
    private String empName;

    private String empNameEn;

    @NotNull
    private LocalDate empBirthDate;

    @NotNull
    private EmpGender empGender;

    @NotBlank
    private String empPhone;

    @Email
    private String empPersonalEmail;

    @NotBlank
    private String empZipCode;

    @NotBlank
    private String empAddressBase;

    private String empAddressDetail;

    @NotBlank
    private String empResidentNumber;

    // 소속 및 고용 정보
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

    // 권한
    @NotNull
    private EmpRole empRole;

    // 폼 설정에서 추가된 동적 fieldKey 들의 값 (프론트가 JSON 문자열로 직렬화)
    private String customFieldsJson;
}
