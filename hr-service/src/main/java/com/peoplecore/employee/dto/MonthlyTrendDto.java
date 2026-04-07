package com.peoplecore.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.YearMonth;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class MonthlyTrendDto {
    private YearMonth month;
    private int hired;
    private int resigned;
}
