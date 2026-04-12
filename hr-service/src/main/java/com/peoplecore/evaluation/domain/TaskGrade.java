package com.peoplecore.evaluation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 업무 난이도 등급
@Getter
@RequiredArgsConstructor
public enum TaskGrade {
    HIGH("상"),  // 상
    MID("중"),   // 중
    LOW("하");   // 하

    private final String label; // 한글 라벨
}
