package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WageInfoResDto {

    private Long hourlyWage;    //시급 (통상임금 % 209)
    private Long dailyWage;     //일단 (시급 * 8)
//    private Long overtimeHourlyWage;    //가산 시급 (시급 * 1.5, 단일 유형 기준
}
