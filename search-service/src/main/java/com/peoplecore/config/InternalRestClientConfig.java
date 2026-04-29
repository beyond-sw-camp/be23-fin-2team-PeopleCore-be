package com.peoplecore.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 사내 마이크로서비스 호출용 RestTemplate. Eureka 서비스명(예: "collaboration-service")으로
 * 호출하면 LoadBalancer 가 인스턴스를 골라준다. AnthropicClient/EmbeddingClient 처럼
 * 외부 API 호출하는 자체 RestTemplate 과는 분리 — 이 Bean 은 @LoadBalanced 가 붙어 있어
 * 외부 도메인 호출에 쓰면 안 된다.
 */
@Configuration
public class InternalRestClientConfig {

    @Bean
    @LoadBalanced
    @Qualifier("internalRestTemplate")
    public RestTemplate internalRestTemplate() {
        RestTemplate rt = new RestTemplate();
        // 사내 호출은 빠르게 fail-fast — 5s/10s 면 일반 CRUD 충분, copilot 응답 시간 폭주 방지
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        rt.setRequestFactory(factory);
        return rt;
    }
}
