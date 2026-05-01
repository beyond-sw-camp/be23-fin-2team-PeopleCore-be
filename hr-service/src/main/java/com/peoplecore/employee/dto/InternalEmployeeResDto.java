package com.peoplecore.employee.dto;

import com.peoplecore.employee.domain.Employee;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalEmployeeResDto {
    private Long empId;
    private String empName;
    private Long deptId;
    private String deptName;
    private String gradeName;
    private String titleName;

    public static InternalEmployeeResDto fromEntity(Employee employee){
        return InternalEmployeeResDto.builder()
                .empId(employee.getEmpId())
                .empName(employee.getEmpName())
                .deptId(employee.getDept() != null ? employee.getDept().getDeptId() : null)
                .deptName(employee.getDept() != null ? employee.getDept().getDeptName() : null)
                .gradeName(employee.getGrade() != null ? employee.getGrade().getGradeName() : null)
                .titleName(employee.getTitle() != null ? employee.getTitle().getTitleName() : null)
                .build();
    }
}