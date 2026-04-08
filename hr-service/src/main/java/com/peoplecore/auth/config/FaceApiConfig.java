package com.peoplecore.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class FaceApiConfig {

    @Value("${face-api.base-url:http://localhost:8001}")
    private String faceApiBaseUrl;

    @Bean
    public WebClient faceApiWebClient() {
        return WebClient.builder()
                .baseUrl(faceApiBaseUrl)
                .build();
    }
}
