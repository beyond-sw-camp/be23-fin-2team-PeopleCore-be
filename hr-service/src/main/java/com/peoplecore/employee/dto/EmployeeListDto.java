package com.peoplecore.employee.dto;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.EmpType;
import com.peoplecore.employee.domain.Employee;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class EmployeeListDto {

    private Long empId;
    private String empNum;
    private String empName;
    private String deptName;
    private String gradeName;
    private String titleName;
    private EmpType empType;
    private LocalDate empHireDate;
    private EmpStatus empStatus;

    public static EmployeeListDto fromEntity(Employee employee) {
//grade, title, company entity깔리는거 확인
        return EmployeeListDto.builder()
                .empId(employee.getEmpId())
                .empNum(employee.getEmpNum())
                .empName(employee.getEmpName())
                .deptName(employee.getDept().getDeptName())
                .gradeName(employee.getGrade().getGradeName())
                .titleName(employee.getTitle() != null ? employee.getTitle().getTitleName() : null)
                .empType(employee.getEmpType())
                .empHireDate(employee.getEmpHireDate())
                .empStatus(employee.getEmpStatus())
                .build();
    }
}
