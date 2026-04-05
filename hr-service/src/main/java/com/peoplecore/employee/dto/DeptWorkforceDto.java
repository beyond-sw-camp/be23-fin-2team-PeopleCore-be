package com.peoplecore.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class DeptWorkforceDto {
    private String deptName;
    private int total;
    private List<GradeCountDto> gradeCounts;
    private int avgYears;
    private int avgMonths;
}
