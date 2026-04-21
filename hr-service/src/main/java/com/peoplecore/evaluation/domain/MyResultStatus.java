package com.peoplecore.evaluation.domain;

// 본인 평가결과 공개 상태 (사원 화면용)
public enum MyResultStatus {
    IN_PROGRESS, // 평가중 (결과 공개 전)
    FINALIZED //결과확정 (최종 확정 완료, 공개됨)
}
