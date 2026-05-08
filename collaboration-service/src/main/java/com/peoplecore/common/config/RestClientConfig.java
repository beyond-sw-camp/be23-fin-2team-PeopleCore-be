package com.peoplecore.common.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

/**
 * RestClient.Builder 환경별 분기.
 *
 * - local: Eureka 디스커버리 사용 → @LoadBalanced 필요
 * - 그 외 (dev/prod, EKS): K8s Service DNS 가 ClusterIP 로 직접 변환 → @LoadBalanced 비활성
 *
 * 둘 중 한 bean 만 활성화되어 RestClient.Builder 단일 후보로 주입됨.
 */
@Configuration
public class RestClientConfig {

    @Bean
    @Profile("local")
    @LoadBalanced
    public RestClient.Builder restClientBuilderLocal() {
        return RestClient.builder();
    }

    @Bean
    @Profile("!local")
    public RestClient.Builder restClientBuilderProd() {
        return RestClient.builder();
    }

}
