package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveAllowanceSummaryResDto {

//    연차수당 산정 목록 - 상단요약 + 사원 리스트

    private Integer totalTarget;        //대상자 수
    private Integer calculatedCount;    //산정완료수
    private Integer appliedCount;       //급여반영수
    private Long totalAllowanceAmount;  //총산정액

    private List<LeaveAllowanceResDto> employees;

}
