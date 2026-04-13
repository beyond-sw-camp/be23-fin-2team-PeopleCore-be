package com.peoplecore.attendance.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/* 주간 1차 쿼리 결과 -> 회사 + 재직 필터 조건 만족하는 사원과 근무그룹의 비트 마스크
*
* 분모 집계에서 근무 예정 여부 판정용
*
* 근무 그룹 미배정 사원은 근무 예정 아님으로 간주
* */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WeekEmpRow {
    /*사원 PK*/
    private Long empId;

    /*근뮤 요일 비트 마스크 */
    private Integer groupWorkDay;
}
