package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.Goal;
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
    private String goalType;         // "KPI" or "OKR"
    private String category;
    private String title;
    private String description;
    private String grade;            // "HIGH"/"MID"/"LOW" - 프론트 한글 매핑
    private Long kpiTemplateId;
    private BigDecimal targetValue;
    private String targetUnit;
    private String status;           // "작성중" or "제출완료" - submittedAt 기반 파생
    private String approval;
    private LocalDateTime submittedAt;
    private String rejectReason;
    private BigDecimal ratio;        // 승인된 목표 중 비율(%). 미승인/단건응답은 null

    public static GoalResponse from(Goal g) {
        return GoalResponse.builder()
                .id(g.getGoalId())
                .goalType(g.getGoalType())
                .category(g.getCategory())
                .title(g.getTitle())
                .description(g.getDescription())
                .grade(g.getTaskGrade() != null ? g.getTaskGrade().name() : null)
                .kpiTemplateId(g.getKpiTemplate() != null ? g.getKpiTemplate().getKpiId() : null)
                .targetValue(g.getTargetValue())
                .targetUnit(g.getTargetUnit())
                .status(g.getSubmittedAt() != null ? "제출완료" : "작성중")
                .approval(g.getApprovalStatus() != null ? g.getApprovalStatus().name() : null)
                .submittedAt(g.getSubmittedAt())
                .rejectReason(g.getRejectReason())
                .build();
    }
}
