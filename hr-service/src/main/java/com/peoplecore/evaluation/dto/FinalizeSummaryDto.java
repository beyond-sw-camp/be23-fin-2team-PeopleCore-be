package com.peoplecore.evaluation.dto;

import lombok.*;

// 10번 - 최종 확정 페이지 상단 요약 지표
//   시즌 잠금 상태(finalizedAt)는 시즌 조회 API/스토어에서 별도 로드
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FinalizeSummaryDto {
    private int totalCount;        // 시즌 전체 대상 인원
    private int assignedCount;     // 배정 완료 (finalGrade IS NOT NULL)
    private int unassignedCount;   // 미산정 (finalGrade IS NULL)
    private int calibratedCount;   // 보정된 인원 (isCalibrated = true)
}
