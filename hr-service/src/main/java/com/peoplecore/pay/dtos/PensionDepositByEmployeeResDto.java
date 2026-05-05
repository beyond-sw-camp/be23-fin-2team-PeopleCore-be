package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class PensionDepositByEmployeeResDto {
//    사원당 연금적립 집계

    private Long empId;
    private String empNum;
    private String empName;
    private String deptName;

    private Integer monthCount;     // 기간 내 COMPLETED 건수
    private Long totalAmount;       // 기간 내 적립 합계
    private LocalDateTime lastDepositDate;
    private Boolean hasManual;
    private Boolean hasCanceled;

}

