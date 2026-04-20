package com.peoplecore.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// 목표 등록/수정 요청 (POST/PUT 공용)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoalRequest {

    @NotBlank
    private String goalType;        // "KPI" or "OKR"

    @NotBlank
    private String grade;           // "HIGH" / "MID" / "LOW"

    // KPI (OKR 일 때 null)
    private Long kpiTemplateId;
    private BigDecimal targetValue;

    // OKR (KPI 일 때 null)
    private String category;
    private String title;
    private String description;
}
