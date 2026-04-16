package com.peoplecore.evaluation.domain;

// 6번 - 등급별 목표 vs 실제 분포 상태
public enum DiffStatus {
    MATCH,   // 목표 = 실제
    OVER,    // 실제 > 목표 (초과)
    UNDER    // 실제 < 목표 (부족)
}
