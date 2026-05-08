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
 * Ollama (로컬 sLLM) thin wrapper. EXAONE 3.5 같은 로컬 모델 호출에 사용.
 * <p>
 * AnthropicClient 와 같은 시그니처(messages 단발 호출) 를 제공하지만 wire format 이 다르다:
 * <ul>
 *   <li>요청: {@code POST /api/chat} with {@code messages: [{role, content}]}</li>
 *   <li>응답: {@code {message: {content, tool_calls?}, done_reason}}</li>
 *   <li>tool 형식: OpenAI 호환 ({@code function: {name, parameters}})</li>
 * </ul>
 * tool_use 루프는 호출자(CopilotService) 책임.
 */
@Slf4j
@Component
public class OllamaClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String model;
    private final int numCtx;

    public OllamaClient(
            RestTemplateBuilder builder,
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:exaone3.5:7.8b}") String model,
            @Value("${ollama.read-timeout-seconds:120}") int readTimeoutSec,
            @Value("${ollama.num-ctx:8192}") int numCtx
    ) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(readTimeoutSec))
                .build();
        this.baseUrl = baseUrl;
        this.model = model;
        this.numCtx = numCtx;
    }

    public String getModel() {
        return model;
    }

    /**
     * /api/chat 단일 호출. messages 는 {@code [{role, content}, ...]} 형식,
     * tools 는 OpenAI 호환 {@code [{type: "function", function: {name, description, parameters}}]} 형식.
     * 응답 stream=false 로 한 번에 받음.
     */
    public ChatResponse chat(String system, List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // system 메시지를 messages 앞에 끼워넣음 (Ollama 가 별도 system 필드도 지원하지만 messages 형식이 더 호환성 좋음)
        List<Map<String, Object>> withSystem = new java.util.ArrayList<>();
        if (system != null && !system.isBlank()) {
            withSystem.add(Map.of("role", "system", "content", system));
        }
        withSystem.addAll(messages);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", withSystem);
        body.put("stream", false);
        body.put("options", Map.of("num_ctx", numCtx));
        if (tools != null && !tools.isEmpty()) body.put("tools", tools);

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<ChatResponse> resp = restTemplate.postForEntity(
                    baseUrl + "/api/chat", req, ChatResponse.class);
            return resp.getBody();
        } catch (Exception e) {
            log.error("Ollama chat call failed: {}", e.getMessage());
            throw new RuntimeException("Ollama call failed: " + e.getMessage(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatResponse {
        public String model;
        public Message message;
        public String done_reason;     // "stop" | "length" | ...
        public Boolean done;
        public Long prompt_eval_count;
        public Long eval_count;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        public String role;            // "assistant"
        public String content;         // 자연어 응답 (도구만 호출할 땐 비어있거나 짧음)
        public List<ToolCall> tool_calls;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolCall {
        public Function function;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Function {
        public String name;
        public Map<String, Object> arguments;
    }
}
