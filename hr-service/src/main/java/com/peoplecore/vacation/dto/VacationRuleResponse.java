package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.VacationGrantRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/* 연차 발생 규칙 응답 DTO - 조회/생성/수정 API 공통 반환 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationRuleResponse {

    /* 규칙 ID (PK) */
    private Long id;

    /* 근속 연수 이상 (포함) */
    private Integer minYears;

    /* 근속 연수 미만 (미포함). NULL = 상한 없음 (예: 21년 이상) */
    private Integer maxYears;

    /* 발생 연차 일수 */
    private Integer days;

    /* 비고 (nullable) */
    private String desc;

    /* VacationGrantRule 엔티티 → 응답 DTO 변환 */
    public static VacationRuleResponse from(VacationGrantRule rule) {
        return VacationRuleResponse.builder()
                .id(rule.getRuleId())
                .minYears(rule.getMinYear())
                .maxYears(rule.getMaxYear())
                .days(rule.getGrantDays())
                .desc(rule.getDescription())
                .build();
    }
}