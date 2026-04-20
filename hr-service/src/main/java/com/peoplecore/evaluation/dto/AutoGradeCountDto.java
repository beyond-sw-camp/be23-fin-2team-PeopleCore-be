package com.peoplecore.evaluation.dto;

import lombok.*;

// 등급별 인원 집계 DTO
// - finalGrade 기준 집계 (= 보정 반영된 현재 분포)
// - 6번 getDistributionDiff / 9번 batchSaveCalibration 시뮬레이션에서 사용
// - autoGrade(불변 원본)가 아닌 finalGrade(보정 후)로 집계해야 "실제 분포 vs 목표 ratio" 검증 정확
@NoArgsConstructor
@Data
@Builder
@AllArgsConstructor
public class AutoGradeCountDto {
    private String label;  // 등급 라벨 (S/A/B/C/D) - finalGrade 값
    private long count;    // 해당 등급 인원
}
