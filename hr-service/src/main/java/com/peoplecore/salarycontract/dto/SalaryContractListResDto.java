package com.peoplecore.salarycontract.dto;


import com.peoplecore.employee.domain.EmpType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joda.time.DateTime;

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
    private DateTime contractStart; //계약일자


}
