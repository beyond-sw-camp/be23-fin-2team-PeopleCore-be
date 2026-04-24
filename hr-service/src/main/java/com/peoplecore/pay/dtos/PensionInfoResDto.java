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
public class PensionInfoResDto {
//    퇴직연금 적립금

    private String retirementType;       // "DB" / "DC" / "severance"
    private Long monthlyDeposit;         // DC 형만 값 존재
    private Long totalDeposited;         // 누적 적립금 (COMPLETED 합산)
    private LocalDateTime lastDepositDate;
}