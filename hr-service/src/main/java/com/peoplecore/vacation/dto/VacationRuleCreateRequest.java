package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/* 연차 발생 규칙 생성/수정 요청 DTO */
/* 화면: "근속 N년~M년 사이엔 D일 발생" 규칙 편집 모달 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationRuleCreateRequest {

    /* 근속 연수 이상 (포함). 필수 */
    private Integer minYears;

    /* 근속 연수 미만 (미포함). NULL = 상한 없음 (예: 21년 이상 케이스) */
    private Integer maxYears;

    /* 발생 연차 일수. 필수 */
    private Integer days;

    /* 비고 (nullable) */
    private String desc;
}