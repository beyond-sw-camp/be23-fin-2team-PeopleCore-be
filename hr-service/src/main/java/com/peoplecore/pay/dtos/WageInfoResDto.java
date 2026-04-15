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
//    일당/시급 기준 패널 응답

    private Long hourlyWage;    //시급 (통상임금 % 209)
    private Long dailyWage;     //일당 (시급 * 일근무시간(사원별 근무그룹))
//    private Long overtimeHourlyWage;    //가산 시급 (시급 * 1.5, 단일 유형 기준
}
