package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PensionDepositResDto {

    private Long depId;
    private Long empId;
    private String empName;
    private String deptName;
    private String payYearMonth;
    private Long baseAmount;
    private Long depositAmount;
    private String depStatus;
    private LocalDateTime depositDate;
    private Long payrollRunId;     // null이면 수동
    private Boolean isManual;

}
