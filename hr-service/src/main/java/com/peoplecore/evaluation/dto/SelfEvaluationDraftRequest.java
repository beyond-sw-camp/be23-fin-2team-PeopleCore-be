package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.AchievementLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

// 자기평가 임시저장/제출 요청 - 화면의 모든 항목 state 일괄 upsert
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelfEvaluationDraftRequest {

    @NotNull
    @Valid
    private List<Item> items;

    // 목표 1건 단위 입력값 (KPI 는 actualValue, OKR 은 achievementLevel)
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @NotNull
        private Long goalId;
        private BigDecimal actualValue;             // KPI 전용 실적값
        private AchievementLevel achievementLevel;  // OKR 달성수준
        private String achievementDetail;           // 달성 상세내용
        private String evidence;                    // 근거자료 (텍스트)
    }
}
