package com.peoplecore.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/* Spring Batch 스텝 실행 1건 응답 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepRunResDto {
    /* StepExecution PK (BATCH_STEP_EXECUTION.STEP_EXECUTION_ID) */
    private Long stepExecutionId;
    /* 스텝 이름 (예: monthlyAccrualStep / autoCloseStep / absentStep) */
    private String stepName;
    /* 실행 상태 (COMPLETED / FAILED / STOPPED 등) */
    private String status;
    /* Reader 가 읽은 item 수 */
    private long readCount;
    /* Writer 가 쓴 item 수 (skip 제외) */
    private long writeCount;
    /* skip 된 item 수 (부분 실패 — 0 이면 정상) */
    private long skipCount;
    /* 청크 commit 횟수 */
    private long commitCount;
    /* 청크 rollback 횟수 */
    private long rollbackCount;
}
