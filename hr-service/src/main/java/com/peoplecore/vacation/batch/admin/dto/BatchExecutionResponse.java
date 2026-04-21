package com.peoplecore.vacation.batch.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/* 배치 실행 이력 한 건 - JobExecution + 첫 StepExecution 집계 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchExecutionResponse {

    /* JobExecution ID - 상세 조회/재실행 참조 키 */
    private Long executionId;

    /* JobInstance ID - 같은 파라미터 조합 단위 */
    private Long instanceId;

    /* Job 이름 - balanceExpiryJob / annualGrantFiscalJob / promotionNoticeJob */
    private String jobName;

    /* 실행 상태 - STARTED/COMPLETED/FAILED/STOPPED/ABANDONED/... */
    private String status;

    /* 종료 코드 - COMPLETED/FAILED/NOOP 등 */
    private String exitCode;

    /* 파라미터 요약 문자열 (Spring Batch 기본 toString) */
    private String parameters;

    /* 시작/종료 시각 - 실행 중이면 endTime 은 null */
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /* 첫 StepExecution 카운터 - 현재 프로젝트 Job 은 모두 단일 Step */
    private long readCount;
    private long writeCount;
    private long skipCount;

    /* 실패 예외 첫 줄 요약 - 성공이면 null */
    private String rootCauseMessage;
}
