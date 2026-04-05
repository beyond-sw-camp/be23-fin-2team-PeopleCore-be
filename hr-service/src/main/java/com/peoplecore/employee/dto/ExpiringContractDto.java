package com.peoplecore.employee.dto;

import com.peoplecore.employee.domain.EmpType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ExpiringContractDto {
    private String empNum;
    private String empName;
    private String deptName;
    private EmpType empType;
    private LocalDate expiryDate;
    private int daysLeft;       // 만료까지 남은 일수 (30일 이내만 조회)
}
