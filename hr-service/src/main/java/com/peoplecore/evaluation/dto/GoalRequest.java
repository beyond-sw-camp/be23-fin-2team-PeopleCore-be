package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.GoalType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// 목표 등록/수정 요청 (POST/PUT 공용)
//   가중치(weight)는 받지 않음 — KPI 신규 등록 시 디폴트 10 자동 박힘
//   가중치 변경은 PUT /eval/goals/weights 로 별도 처리
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoalRequest {

    @NotNull
    private GoalType goalType;      // KPI / OKR

    // KPI (OKR 일 때 null)
    private Long kpiTemplateId;
    private BigDecimal targetValue;

    // OKR (KPI 일 때 null)
    private String category;
    private String title;
    private String description;
}
