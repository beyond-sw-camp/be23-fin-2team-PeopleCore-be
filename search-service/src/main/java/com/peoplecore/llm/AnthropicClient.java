package com.peoplecore.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API thin wrapper. EmbeddingClient(OpenAI) 패턴을 그대로 따른다.
 * tool_use loop는 호출자(CopilotService)가 담당 — 이 클래스는 단발 messages 호출만.
 */
@Slf4j
@Component
public class AnthropicClient {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int defaultMaxTokens;

    public AnthropicClient(
            RestTemplateBuilder builder,
            @Value("${anthropic.api-key:}") String apiKey,
            @Value("${anthropic.base-url:https://api.anthropic.com/v1}") String baseUrl,
            @Value("${anthropic.model:claude-haiku-4-5-20251001}") String model,
            @Value("${anthropic.max-tokens:1024}") int defaultMaxTokens
    ) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(60))
                .build();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.defaultMaxTokens = defaultMaxTokens;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getModel() {
        return model;
    }

    /**
     * Messages API 단일 호출. system은 별도 필드, messages는 user/assistant turn list.
     * tools 비우면 일반 채팅, 채우면 tool use 활성화. 응답의 stop_reason이 "tool_use"면 호출자가 도구 실행 후 재호출 책임.
     */
    public MessagesResponse messages(String system, List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        if (!isConfigured()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_tokens", defaultMaxTokens);
        if (system != null && !system.isBlank()) body.put("system", system);
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) body.put("tools", tools);

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<MessagesResponse> resp = restTemplate.postForEntity(
                    baseUrl + "/messages", req, MessagesResponse.class);
            return resp.getBody();
        } catch (Exception e) {
            log.error("Anthropic messages call failed: {}", e.getMessage());
            throw new RuntimeException("Anthropic call failed: " + e.getMessage(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessagesResponse {
        public String id;
        public String role;
        public String model;
        public String stop_reason;
        public List<ContentBlock> content;
        public Usage usage;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentBlock {
        public String type;            // "text" | "tool_use"
        public String text;            // when type=text
        public String id;              // when type=tool_use (tool_use_id)
        public String name;            // when type=tool_use (tool name)
        public Map<String, Object> input; // when type=tool_use (tool args)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        public Integer input_tokens;
        public Integer output_tokens;
    }
}
