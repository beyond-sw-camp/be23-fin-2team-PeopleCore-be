package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.DiffStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

// 6번 - 실제 vs 목표 분포 + 보정 건수 응답 DTO
// 프론트 GradeCalibration 진입 시 초기 1회 호출 -> 상단 카드 + 불일치 배지 + 보정 건수 렌더
// 이후 사용자 변경은 프론트 로컬 state 에서 실시간 재계산 (백엔드 재호출 X)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DistributionDiffDto {

    private List<GradeDiff> grades;   // 등급별 목표/실제 (스냅샷 gradeRules 순서 유지)
    private int totalCount;           // finalGrade != null row 총 인원 (배분 받은 인원)
    private int mismatchCount;        // 목표 != 실제 등급 수 ("N개 등급 불일치" 배지)
    private int calibrationCount;     // 현재 보정된 사원 수 ("현재 보정 건수 N건")
    private boolean isAllMatch;       // 전 등급 일치 여부 (확정 버튼 활성 조건)


    // ─── 등급별 분포 항목 ───
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GradeDiff {
        private String label;             // S/A/B/C/D
        private String color;             // 카드 색상 (스냅샷 값)
        private BigDecimal targetRatio;   // 목표 %
        private int targetCount;          // 목표 인원 (마지막 등급은 잔여 몰기)
        private int actualCount;          // 실제 인원 (finalGrade 집계 = 보정 반영)
        private int diff;                 // actual - target (음수=부족, 양수=초과)
        private DiffStatus status;        // MATCH | OVER | UNDER
    }
}
