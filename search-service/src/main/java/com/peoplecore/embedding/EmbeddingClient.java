package com.peoplecore.embedding;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class EmbeddingClient {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final String baseUrl;
    private final int batchSize;

    public EmbeddingClient(
            RestTemplateBuilder builder,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.embedding.model}") String model,
            @Value("${openai.embedding.dimensions}") int dimensions,
            @Value("${openai.embedding.base-url}") String baseUrl,
            @Value("${openai.embedding.batch-size:64}") int batchSize
    ) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30))
                .build();
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.baseUrl = baseUrl;
        this.batchSize = batchSize;
    }

    public float[] embed(String text) {
        List<float[]> result = embedBatch(List.of(text));
        return result.isEmpty() ? null : result.get(0);
    }

    public List<float[]> embedBatch(List<String> texts) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured");
        }
        if (texts == null || texts.isEmpty()) return List.of();

        List<float[]> results = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            results.addAll(callApi(batch));
        }
        return results;
    }

    public int getDimensions() {
        return dimensions;
    }

    private List<float[]> callApi(List<String> batch) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", batch);
        body.put("dimensions", dimensions);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<EmbeddingResponse> response = restTemplate.postForEntity(
                    baseUrl + "/embeddings",
                    request,
                    EmbeddingResponse.class
            );
            EmbeddingResponse resBody = response.getBody();
            if (resBody == null || resBody.data == null) {
                throw new IllegalStateException("OpenAI embedding response is empty");
            }
            List<float[]> vectors = new ArrayList<>(resBody.data.size());
            for (Datum d : resBody.data) {
                vectors.add(d.embedding);
            }
            return vectors;
        } catch (Exception e) {
            log.error("OpenAI embedding call failed (batch size={}): {}", batch.size(), e.getMessage());
            throw new RuntimeException("Embedding failed", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EmbeddingResponse {
        public List<Datum> data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Datum {
        public float[] embedding;
    }
}
