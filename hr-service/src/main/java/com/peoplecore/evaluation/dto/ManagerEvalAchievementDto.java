package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.KpiDirection;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

// 팀원 달성도 - 팀장평가 화면 플로팅 패널용
//   승인된 목표만 포함, 자기평가 미제출 시 actualValue/selfLevel null
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ManagerEvalAchievementDto {
    private List<KpiItem> kpiList;
    private List<OkrItem> okrList;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class KpiItem {
        private String category;
        private String title;
        private BigDecimal targetValue;    // 목표치 (Goal.targetValue)
        private String targetUnit;         // 단위 (Goal.targetUnit)
        private BigDecimal actualValue;    // 실적 (SelfEvaluation.actualValue, 미제출이면 null)
        private KpiDirection direction;    // 지표 방향성 (프론트 달성률 공식 분기)
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OkrItem {
        private String category;
        private String title;
        private String selfLevel;          // 자기평가 달성수준 (EXCELLENT/GOOD/AVERAGE/POOR/INADEQUATE, 미제출 null)
    }
}
