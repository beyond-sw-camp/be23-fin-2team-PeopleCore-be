package com.peoplecore.vacation.batch.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/* DiscordNotifier 단독 테스트 요청 - 모든 필드 optional (null 이면 기본값으로 대체) */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscordTestRequest {

    /* 테스트용 Job 이름 - null 이면 "testJob" */
    private String jobName;

    /* JobParameters 대용 문자열 - null 이면 기본 샘플 */
    private String params;

    /* 종료 코드 - null 이면 "FAILED" */
    private String exitCode;

    /* 실패 카운트 - null 이면 1 */
    private Integer failureCount;

    /* 루트 원인 메시지 - null 이면 기본 샘플. truncate 테스트 시 900자 넘게 넣어볼 것 */
    private String rootCauseMessage;
}
