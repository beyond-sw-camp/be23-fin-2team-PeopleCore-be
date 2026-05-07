package com.peoplecore.config;

import org.springframework.beans.factory.annotation.Qualifier;
// import org.springframework.cloud.client.loadbalancer.LoadBalanced; // EKS 전환으로 비활성 (Eureka 복귀 시 풀 것)
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 사내 마이크로서비스 호출용 RestTemplate. K8s Service DNS(예: "http://collaboration-service")로
 * 직접 호출하면 kube-dns 가 ClusterIP 로 해석하고 K8s Service 가 파드 LB 를 처리.
 * AnthropicClient/EmbeddingClient 처럼 외부 API 호출하는 자체 RestTemplate 과는 분리 —
 * 사내 호출 전용 timeout 정책이 다르기 때문.
 */
@Configuration
public class InternalRestClientConfig {

    @Bean
//    @LoadBalanced // EKS 환경에선 K8s Service DNS 가 LB 처리 → 활성 시 디스커버리 조회 실패로 호출 막힘
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
