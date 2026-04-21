package com.peoplecore.vacation.batch.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/* 배치 재실행 트리거 요청 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchRerunRequest {

    /* 대상 회사 ID - balanceExpiryJob 은 null 허용(전사 공용), 나머지 Job 은 필수 */
    private UUID companyId;

    /* 대상 날짜 - 모든 Job 필수. JobParameters 의 targetDate 로 박힘 */
    private LocalDate targetDate;

    /* promotionNoticeJob 전용 - "FIRST" / "SECOND" */
    private String stage;

    /* promotionNoticeJob 전용 - 몇 개월 전 통지할지 */
    private Long monthsBefore;

    /* 재실행 모드 - null 이면 RESTART 로 간주 */
    private RerunMode mode;

    public enum RerunMode {
        /* 동일 JobParameters → Spring Batch 가 실패 지점부터 재개. COMPLETED 면 FRESH 로 자동 승격 */
        RESTART,
        /* rerunAt tiebreaker 추가 → 새 JobInstance 로 처음부터. 멱등 보장된 Job 에만 안전 */
        FRESH
    }
}
