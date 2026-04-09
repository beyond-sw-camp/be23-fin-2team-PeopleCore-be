package com.peoplecore.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeSimpleResDto {
    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private String titleName;
}
