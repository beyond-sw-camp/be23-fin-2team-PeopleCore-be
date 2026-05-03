package com.peoplecore.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/* Spring Batch 잡 실행 1건 응답 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobRunResDto {
    /* JobInstance PK (BATCH_JOB_INSTANCE.JOB_INSTANCE_ID) */
    private Long jobInstanceId;
    /* JobExecution PK (BATCH_JOB_EXECUTION.JOB_EXECUTION_ID) - 같은 인스턴스가 여러 실행 가능. 보통 1:1 */
    private Long jobExecutionId;
    /* 잡 이름 (예: monthlyAccrualJob, autoCloseJob) */
    private String jobName;
    /* 실행 상태 (COMPLETED / FAILED / STOPPED / ABANDONED 등) */
    private String status;
    /* 종료 코드 (COMPLETED / FAILED 등) */
    private String exitCode;
    /* 시작 시각. nullable - 잡 시작 직전 조회 시 null 가능 */
    private LocalDateTime startTime;
    /* 종료 시각. nullable - 실행 중이면 null */
    private LocalDateTime endTime;
    /* JobParameters (companyId / targetDate / workGroupId / stage / monthsBefore 등) */
    private Map<String, String> jobParameters;
    /* Step 상세 - 단건 조회 응답에서만 채움. 검색 응답은 null (성능) */
    private List<StepRunResDto> steps;
}
