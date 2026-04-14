package com.peoplecore.evaluation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 단계 상태
@Getter
@RequiredArgsConstructor
public enum StageStatus {
    WAITING("대기"),
    IN_PROGRESS("진행중"),
    FINISHED("마감");

    private final String label; // 한글 라벨 (프론트 표시용)
}
