package com.peoplecore.company.config;

// import org.springframework.cloud.client.loadbalancer.LoadBalanced; // EKS 전환으로 비활성 (Eureka 복귀 시 풀 것)
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class CollaborationApiConfig {

    @Bean
//    @LoadBalanced // EKS 환경에선 K8s Service DNS 가 LB 처리 → 활성 시 디스커버리 조회 실패로 호출 막힘
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
