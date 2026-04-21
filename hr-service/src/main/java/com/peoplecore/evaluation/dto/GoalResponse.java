package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.Goal;
import com.peoplecore.evaluation.domain.GoalApprovalStatus;
import com.peoplecore.evaluation.domain.GoalType;
import com.peoplecore.evaluation.domain.TaskGrade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 목표 1건 응답
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoalResponse {
    private Long id;
    private GoalType goalType;            // KPI / OKR
    private String category;
    private String title;
    private String description;
    private TaskGrade grade;              // HIGH / MID / LOW
    private Long kpiTemplateId;
    private BigDecimal targetValue;
    private String targetUnit;
    private GoalApprovalStatus approval;  // DRAFT / PENDING / APPROVED / REJECTED
    private LocalDateTime submittedAt;    // 제출 이력 (null = 미제출)
    private String rejectReason;
    private BigDecimal ratio;             // 승인된 목표 중 비율(%). 미승인/단건응답은 null

    public static GoalResponse from(Goal g) {
        return GoalResponse.builder()
                .id(g.getGoalId())
                .goalType(g.getGoalType())
                .category(g.getCategory())
                .title(g.getTitle())
                .description(g.getDescription())
                .grade(g.getTaskGrade())
                .kpiTemplateId(g.getKpiTemplate() != null ? g.getKpiTemplate().getKpiId() : null)
                .targetValue(g.getTargetValue())
                .targetUnit(g.getTargetUnit())
                .approval(g.getApprovalStatus())
                .submittedAt(g.getSubmittedAt())
                .rejectReason(g.getRejectReason())
                .build();
    }
}
