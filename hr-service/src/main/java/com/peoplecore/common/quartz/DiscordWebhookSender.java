package com.peoplecore.common.quartz;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/* Discord webhook 발사 유틸 — 잡 실패 알림 단일 채널 */
/* application-*.yml 의 discord.batch-webhook 재활용. 노출 시 Discord 에서 폐기·재발급 */
/* 발사 실패는 ERROR 로그만 — 알림이 잡 흐름 막지 않게 */
@Slf4j
@Component
public class DiscordWebhookSender {

    private final String webhookUrl;

    /* RestClient 는 thread-safe — 인스턴스 1회 생성 후 재사용 */
    private final RestClient restClient = RestClient.create();

    @Autowired
    public DiscordWebhookSender(@Value("${discord.batch-webhook}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /* Discord 메시지 전송. 실패 시 ERROR 로그만 */
    public void send(String content) {
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", content))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("[DiscordWebhook] 발사 실패 - {}", e.getMessage(), e);
        }
    }
}
