package com.peoplecore.evaluation.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DraftListItemDto {
    private String empNum;         // 사번
    private String name;           // 성명
    private String deptName;       // 부서 (시즌 오픈 시 스냅샷)
    private String position;       // 직급 (스냅샷)
    private BigDecimal totalScore; // 종합점수 — null 이면 미산정
    private String autoGrade;      // 자동등급 — null 이면 미산정
}
