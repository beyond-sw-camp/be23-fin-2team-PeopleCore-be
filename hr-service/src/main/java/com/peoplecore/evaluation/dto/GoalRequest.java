package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.GoalType;
import com.peoplecore.evaluation.domain.TaskGrade;
import jakarta.validation.constraints.NotNull;
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

    @NotNull
    private GoalType goalType;      // KPI / OKR

    @NotNull
    private TaskGrade grade;        // HIGH / MID / LOW

    // KPI (OKR 일 때 null)
    private Long kpiTemplateId;
    private BigDecimal targetValue;

    // OKR (KPI 일 때 null)
    private String category;
    private String title;
    private String description;
}
