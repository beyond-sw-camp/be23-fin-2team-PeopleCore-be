package com.peoplecore.client.dto;

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
    private String deptName;
    private String gradeName;
    private String titleName;

    public static InternalEmployeeResDto fromEntity(Employee employee){
        return InternalEmployeeResDto.builder()
                .empId(employee.getEmpId())
                .empName(employee.getEmpName())
                .deptName(employee.getDept() != null ? employee.getDept().getDeptName() : null)
                .gradeName(employee.getGrade() != null ? employee.getGrade().getGradeName() : null)
                .titleName(employee.getTitle() != null ? employee.getTitle().getTitleName() : null)
                .build();
    }
}