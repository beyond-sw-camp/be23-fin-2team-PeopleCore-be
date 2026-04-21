package com.peoplecore.pay.approval;

public record PayrollItemSummaryDto(
//    PayrollDetails 합계 집계 결과 - 지출결의서 dataMap 빌드용
//    - 항목명 + 과세여부별 금액합계
            String payItemName,
            Boolean isTaxable,
            Long totalAmount
)
{}
