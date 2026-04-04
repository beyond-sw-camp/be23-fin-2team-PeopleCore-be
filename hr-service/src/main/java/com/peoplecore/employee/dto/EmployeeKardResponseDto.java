package com.peoplecore.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class EmployeeKardResponseDto {
    private long total;
    private long active;
    private long onLeave;
    private long hiredThisMonth;
}
