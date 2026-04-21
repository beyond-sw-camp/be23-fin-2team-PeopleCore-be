package com.peoplecore.vacation.batch.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/* 배치 재실행 트리거 응답 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchRerunResponse {

    /* 새로 생성된 JobExecution ID */
    private Long executionId;

    /* 실행 직후 상태 - COMPLETED / FAILED / STARTED (기본 JobLauncher 는 동기라 보통 종료 상태) */
    private String status;

    /* 실제 적용된 모드 - RESTART / FRESH (자동 승격 시 FRESH) */
    private String appliedMode;

    /* 경고/안내 - ex. "동일 파라미터가 이미 COMPLETED - FRESH 로 자동 전환". 평상시 null */
    private String message;
}
