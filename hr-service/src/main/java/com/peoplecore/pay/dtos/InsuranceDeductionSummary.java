package com.peoplecore.pay.dtos;

public interface InsuranceDeductionSummary {
// 기공제액 집계 결과

//    프로젝션 인터페이스
//    인터페이스에서 AS empId를 보고 getEmpId로 자동 매핑, 반환타입으로 사용
    Long getEmpId();
    String getPayItemName();
    Long getTotalAmount();

}
