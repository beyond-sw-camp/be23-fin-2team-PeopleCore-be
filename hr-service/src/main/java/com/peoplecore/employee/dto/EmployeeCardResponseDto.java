package com.peoplecore.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class EmployeeCardResponseDto {
    private int total;
    private int active;
    private int onLeave;
    private int hiredThisMonth;
}
