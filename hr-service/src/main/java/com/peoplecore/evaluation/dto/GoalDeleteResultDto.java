package com.peoplecore.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 목표 삭제 결과 - cascade 확인 플로우 지원
//   - 일반 삭제:     { success: true, requiresConfirm: false, cascadedOkrs: [] }
//   - cascade 필요: { success: false, requiresConfirm: true, cascadedOkrs: [...] }

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class GoalDeleteResultDto {
    private Boolean success;                    // true = 실제 삭제 완료
    private Boolean requiresConfirm;            // true = 프론트 다이얼로그 노출 + confirm=true 재호출 필요
    private List<CascadedGoal> cascadedOkrs;    // cascade 대상 OKR 목록 (다이얼로그 표시용)

    // cascade 되는 OKR 1건 - 식별자 + 다이얼로그용 제목
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class CascadedGoal {
        private Long goalId;
        private String title;
    }
}
