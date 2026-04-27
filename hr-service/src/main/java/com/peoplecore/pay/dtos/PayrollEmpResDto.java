package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollEmpResDto {
//    급여대장 사원 행

    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private String empType;
    private String status;

    private Long totalPay;          //지급합계
    private Long totalDeduction;    //공제합계
    private Long netPay;            //공제후 금액
    private Long unpaid;            //미지급액

    private String payrollEmpStatus;   // CALCULATING / CONFIRMED 산정중/확정
    private String empStatus;          // ACTIVE / ON_LEAVE / RESIGNED 재직/휴직/퇴직
}
