package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PensionDepositEmployeeResDto {
//    사원 퇴직연금 조회요청

    private Long empId;
    private String empName;
    private String deptName;
    private String retirementType;
    private Long totalDeposited;                     //누적적립액
    private List<PensionDepositResDto> deposits;     //월별 이력

}
