package com.peoplecore.pay.dtos;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.pay.domain.EmpAccounts;
import com.peoplecore.salarycontract.domain.SalaryContract;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmpSalaryResDto {

    private Long empId;
    private String empNum;
    private String empStatus;   //재직상태 ACTIVE, ON_LEAVE, RESIGNED
    private String empName;
    private String deptName;
    private String titleName;
    private LocalDate empHireDate;  //입사일
    private LocalDate empResignDate; //퇴사일
    private String empType; //직원구분 FULL, CONTRACT, DISPATCHED 정규직, 계약직, 파견직

    private BigDecimal annualSalary;
    private Long monthlySalary;

    private LocalDate contractStartDate;   // applyFrom
    private LocalDate contractEndDate;     // applyTo (정규직 등은 null)

    private String bankName;
    private String accountNumber;


    public static EmpSalaryResDto fromEmployee(Employee emp, SalaryContract contract, EmpAccounts accounts) {
        BigDecimal annualSalary = contract != null ? contract.getTotalAmount() : null;
        Long monthlySalary = annualSalary != null
                ? annualSalary.divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP).longValue()
                : null;

        return EmpSalaryResDto.builder()
                .empId(emp.getEmpId())
                .empNum(emp.getEmpNum())
                .empStatus(emp.getEmpStatus().name())
                .empName(emp.getEmpName())
                .deptName(emp.getDept().getDeptName())
                .titleName(emp.getTitle() != null ? emp.getTitle().getTitleName() : null)
                .empHireDate(emp.getEmpHireDate())
                .empResignDate(emp.getEmpResignDate() != null ? emp.getEmpResignDate() : null)
                .empType(emp.getEmpType().name())
                .annualSalary(annualSalary)
                .monthlySalary(monthlySalary)
                .contractStartDate(contract != null ? contract.getApplyFrom() : null)
                .contractEndDate(contract != null ? contract.getApplyTo() : null)
                .bankName(accounts != null ? accounts.getBankName() : null)
                .accountNumber(accounts != null ? accounts.getAccountNumber() : null)
                .build();
    }

}

