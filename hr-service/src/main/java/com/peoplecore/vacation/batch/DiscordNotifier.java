package com.peoplecore.vacation.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/* Discord 웹훅 알림 - 배치 실패 / 중요 이벤트 전송 */
/* webhook URL 비어있으면 no-op (로컬 환경변수 미설정 시 빌드/테스트 영향 없음) */
@Component
@Slf4j
public class DiscordNotifier {

    /* Discord embed 색상 - Red=실패 / Yellow=경고 / Green=성공 */
    private static final int COLOR_FAIL = 15158332;
    private static final int COLOR_WARN = 16776960;

    private final String webhookUrl;
    private final WebClient webClient;

    @Autowired
    public DiscordNotifier(@Value("${discord.batch-webhook:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        // timeout 5초 / 재시도 2회 - 알림 실패가 배치 본체를 방해하면 안 됨
        this.webClient = WebClient.builder().build();
    }

    /* 배치 실패 알림 - 실패 건별 호출. 웹훅 미설정 시 조용히 skip */
    /* companyLabel - 회사 식별 라벨 (예: "우리회사 (c0ffee12)" 또는 "전사 통합"). null/blank 면 "N/A" */
    public void notifyBatchFailure(String jobName, String companyLabel, String params,
                                   String exitCode, int failureCount, String rootCauseMessage) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("[DiscordNotifier] webhook URL 미설정 - skip. job={}", jobName);
            return;
        }

        // Discord embed 1개 포함한 payload 구성 - embeds 최대 10개, field 최대 25개 제한
        Map<String, Object> payload = Map.of(
                "content", ":rotating_light: **배치 실패** — `" + jobName + "` / " + nullToNA(companyLabel),
                "embeds", List.of(Map.of(
                        "title", "[FAIL] " + jobName,
                        "color", COLOR_FAIL,
                        "fields", List.of(
                                Map.of("name", "Company", "value", nullToNA(companyLabel), "inline", true),
                                Map.of("name", "Exit Code", "value", nullToNA(exitCode), "inline", true),
                                Map.of("name", "Failure Count", "value", String.valueOf(failureCount), "inline", true),
                                Map.of("name", "Parameters", "value", "```" + truncate(params, 900) + "```"),
                                Map.of("name", "Root Cause", "value", "```" + truncate(nullToNA(rootCauseMessage), 900) + "```")
                        ),
                        "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString()
                ))
        );

        sendAsync(payload, "FAIL:" + jobName);
    }

    /* 배치 부분 실패 경고 - Step 은 COMPLETED 지만 skipCount > 0 일 때 호출. 노란색 embed */
    /* 웹훅 미설정 시 조용히 skip */
    public void notifyBatchWarning(String jobName, String companyLabel, String params,
                                   long readCount, long writeCount, long skipCount) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("[DiscordNotifier] webhook URL 미설정 - skip. job={}", jobName);
            return;
        }

        Map<String, Object> payload = Map.of(
                "content", ":warning: **배치 부분 실패** — `" + jobName + "` / " + nullToNA(companyLabel),
                "embeds", List.of(Map.of(
                        "title", "[WARN] " + jobName,
                        "color", COLOR_WARN,
                        "fields", List.of(
                                Map.of("name", "Company", "value", nullToNA(companyLabel), "inline", true),
                                Map.of("name", "Read", "value", String.valueOf(readCount), "inline", true),
                                Map.of("name", "Write", "value", String.valueOf(writeCount), "inline", true),
                                Map.of("name", "Skip", "value", String.valueOf(skipCount), "inline", true),
                                Map.of("name", "Parameters", "value", "```" + truncate(params, 900) + "```")
                        ),
                        "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString()
                ))
        );

        sendAsync(payload, "WARN:" + jobName);
    }

    /* 비동기 전송 - .subscribe() 로 main 스레드 블록 방지 */
    /* 네트워크 일시 장애 대비 2회 재시도. 최종 실패해도 배치 상태엔 영향 없음 (로그만) */
    private void sendAsync(Map<String, Object> payload, String tag) {
        webClient.post()
                .uri(webhookUrl)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)))
                .subscribe(
                        resp -> log.debug("[DiscordNotifier] 전송 성공 - tag={}, status={}", tag, resp.getStatusCode()),
                        err -> log.warn("[DiscordNotifier] 전송 실패 (알림만 실패, 배치 본체 영향 없음) - tag={}, err={}", tag, err.getMessage())
                );
    }

    /* Discord field value 최대 길이(1024) 대비 안전 컷 */
    private static String truncate(String s, int max) {
        if (s == null) return "N/A";
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }

    private static String nullToNA(String s) {
        return s == null || s.isBlank() ? "N/A" : s;
    }
}
