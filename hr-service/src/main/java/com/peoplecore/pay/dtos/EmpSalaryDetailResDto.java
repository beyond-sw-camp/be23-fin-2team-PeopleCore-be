package com.peoplecore.pay.dtos;

import com.peoplecore.pay.enums.PensionType;
import com.peoplecore.pay.enums.RetirementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmpSalaryDetailResDto {

    private Long empId;
    private String empName;
    private String empNum;
    private String empEmail;
    private String empStatus;   //재직상태
    private String deptName;
    private String gradeName;
    private String titleName;
    private LocalDate empHireDate;
    private LocalDate empResignDate;
    private String empType;     //직원구분

    private Integer dependentsCount;

    private BigDecimal annualSalary;
    private Long monthlySalary;

    private LocalDate contractStartDate;
    private LocalDate contractEndDate;

    // 고정수당 항목
    private List<ContractPayItemResDto> fixedPayItems;

    private Long empAccountId;
    private String bankName;
    private String accountNumber;
    private String accountHolder;

    private PensionType companyPensionType;    // 회사 퇴직연금 설정 (severance/DB/DC/DB_DC)
    private RetirementType empRetirementType;     // 사원 퇴직연금 유형 (severance/DB/DC)

    // 회사 통합 운용사/계좌 (DB/DB_DC일 때만 의미 있음)
    private String companyPensionProvider;

    private Long retirementAccountId;
    private String pensionProvider;     //퇴직연금 운용사 : 개인용 DC
    private String retirementAccountNumber; // 개인용 DC

}

