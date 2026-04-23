package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeveranceEstimateSummaryResDto {

    private LocalDate baseDate;
    private Integer totalEmployees;
    private Long totalEstimateAmount;

    // 유형별 집계
    private Integer severanceCount;
    private Long severanceAmount;
    private Integer dbCount;
    private Long dbAmount;
    private Integer dcCount;
    private Long dcDiffAmount;

    private List<SeveranceEstimateRowDto> employees;

}
