package com.peoplecore.pay.dtos;

import com.peoplecore.employee.domain.Employee;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmpSalaryResDto {

    private Long empId;
    private String empStatus;   //재직상태 ACTIVE, ON_LEAVE, RESIGNED
    private String empName;
    private String deptName;
    private String titleName;
    private LocalDate empHireDate;  //입사일
    private LocalDate empResignDate; //퇴사일
    private String empType; //직원구분 FULL, CONTRACT, DISPATCHED 정규직, 계약직, 파견직

    private BigDecimal annualSalary;
    private Long monthlySalary;

    private String bankName;
    private String accountNumber;


    public static  EmpSalaryResDto fromEmployee(Employee emp, BigDecimal annualSalary, Long monthlySalary, String bankName, String accountNumber){
        return EmpSalaryResDto.builder()
                .empId(emp.getEmpId())
                .empStatus(emp.getEmpStatus().name())
                .empName(emp.getEmpName())
                .deptName(emp.getDept().getDeptName())
                .titleName(emp.getTitle() != null ? emp.getTitle().getTitleName() : null)
                .empHireDate(emp.getEmpHireDate())
                .empResignDate(emp.getEmpResignDate() != null ? emp.getEmpResignDate() : null)
                .empType(emp.getEmpType().name())
                .annualSalary(annualSalary)
                .monthlySalary(monthlySalary)
                .bankName(bankName)
                .accountNumber(accountNumber)
                .build();
    }

}

