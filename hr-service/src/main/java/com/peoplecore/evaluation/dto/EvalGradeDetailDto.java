package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.AchievementLevel;
import com.peoplecore.evaluation.domain.GoalType;
import com.peoplecore.evaluation.domain.TaskGrade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// 15번 - 평가 결과 상세 (HR 전용) - 한 사원의 단계별 타임라인
//   확정 전/후 모두 조회 가능. 미진행 단계는 null/빈 배열 -> 프론트에서 해당 섹션 숨김 처리
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalGradeDetailDto {

    // ─── 패널 ───
    private String empNum;
    private String empName;
    private String deptName;              // 스냅샷 (평가 당시 부서)
    private String position;              // 스냅샷 (평가 당시 직급)
    private String seasonName;
    private String finalGrade;
    private Integer rankInSeason;

    // ─── Section 1 - 목표 등록 ───
    private List<GoalEntry> goals;

    // ─── Section 2 - 평가 입력 내역 ───
    private List<ItemScore> itemScores;                // 자기/상위자 요약 카드용
    private List<SelfEvalEntry> selfEvalEntries;       // 자기평가 상세 (모달)
    private ManagerEvalEntry managerEvalEntry;         // 상위자평가 상세 (모달)

    // ─── Section 3 - 종합점수 ───
    // TODO: 근태 발생건수 집계 구현 후 채우기. 현재는 빈 배열
    private List<AdjustmentItem> adjustments;
    private BigDecimal rawScore;

    // ─── Section 4 - Z-score (미실행 시 null) ───
    private BigDecimal teamAvg;
    private BigDecimal teamStd;
    private BigDecimal companyAvg;
    private BigDecimal companyStd;
    private BigDecimal adjustedScore;

    // ─── Section 5 - 등급 산정 ───
    private String autoGrade;

    // ─── Section 6 - 보정 이력 (없으면 빈 배열) ───
    private List<CalibrationEntry> calibrations;

    // ─── Section 7 - 최종 확정 (미확정 시 null) ───
    private LocalDateTime lockedAt;


    // ═══════════════ 내부 구조 ═══════════════

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GoalEntry {
        private GoalType goalType;        // KPI / OKR
        private String category;
        private String title;
        private TaskGrade grade;          // HIGH / MID / LOW
        private BigDecimal ratio;         // 승인 목표 중 비율(%)
        private BigDecimal targetValue;
        private String targetUnit;
    }


    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ItemScore {
        private String itemId;            // "self" / "manager"
        private String itemName;
        private BigDecimal score;
        private BigDecimal weight;
    }


    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SelfEvalEntry {
        private GoalType goalType;
        private String title;
        private TaskGrade grade;
        private BigDecimal targetValue;
        private String targetUnit;
        private BigDecimal actualValue;
        private AchievementLevel achievementLevel;
        private String achievementDetail;
        private List<SelfEvaluationResponse.FileResponse> files;  // 자기평가 공유 value object 재사용
    }


    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ManagerEvalEntry {
        private String grade;             // S / A / B / C / D
        private String comment;           // HR 내부용
        private String feedback;          // 사원 공개용
    }


    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AdjustmentItem {
        private String name;
        private BigDecimal points;
    }


    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CalibrationEntry {
        private LocalDateTime date;
        private String fromGrade;
        private String toGrade;
        private String reason;
        private String actor;             // 보정 수행자 이름
    }


}
