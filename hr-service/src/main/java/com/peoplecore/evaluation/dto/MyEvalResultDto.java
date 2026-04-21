package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.AchievementLevel;
import com.peoplecore.evaluation.domain.GoalType;
import com.peoplecore.evaluation.domain.MyResultStatus;
import com.peoplecore.evaluation.domain.TaskGrade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// 본인 평가결과 - 특정 시즌 상세 응답 (사원)
//   상위자평가/자동산정/최종 등급 단계 완료 시 일괄 공개 (stage 상태 기반)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MyEvalResultDto {
    private MyResultStatus status;          // IN_PROGRESS / FINALIZED
    private LocalDateTime finalizedAt;      // null = 평가중
    private String autoGrade;               // GRADING stage 완료 후 공개
    private String managerGrade;            // GRADING 시작/종료 후 공개
    private String finalGrade;              // 최종 확정 후 공개
    private String feedback;                // GRADING 시작/종료 후 공개 (comment 제외)
    private List<GoalResult> goals;         // GRADING 시작/종료 후 공개


    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GoalResult {
        private GoalType goalType;
        private String category;
        private String title;
        private TaskGrade grade;
        private BigDecimal targetValue;
        private String targetUnit;
        private BigDecimal actualValue;
        private BigDecimal achievementRate;     // KPI 만 - 서버가 direction 반영해 계산
        private AchievementLevel selfLevel;     // OKR 만
    }
}
