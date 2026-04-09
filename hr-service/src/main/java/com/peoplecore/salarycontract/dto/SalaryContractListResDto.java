package com.peoplecore.salarycontract.dto;


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
public class SalaryContractListResDto {
    private Long id;
    private String empNum;
    private String empName;
    private String department;
    private String rank; //직급
    private String position;    //직책
    private EmpType employmentType; //근로형태
    private LocalDate contractStart; //계약일자


}
