package com.peoplecore.employee.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class EmployeeCreateRequestDto {

    private String empName;
    private String empNameEn;
    private String empPhone;

}
