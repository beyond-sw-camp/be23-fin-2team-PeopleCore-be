package com.peoplecore.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 사내 마이크로서비스 호출용 RestTemplate.
 * - local 프로필: Eureka 디스커버리 사용 (@LoadBalanced)
 * - prod 프로필(EKS): K8s Service DNS 직접 호출, LB 비활성
 */
@Configuration
public class InternalRestClientConfig {

    /** local 프로필 — Eureka 디스커버리 기반 LB */
    @Bean
    @Profile("local")
    @LoadBalanced
    @Qualifier("internalRestTemplate")
    public RestTemplate internalRestTemplateLocal() {
        return buildRestTemplate();
    }

    /** prod 프로필 — K8s Service DNS 직접 호출 */
    @Bean
    @Profile("prod")
    @Qualifier("internalRestTemplate")
    public RestTemplate internalRestTemplate() {
        return buildRestTemplate();
    }

    private RestTemplate buildRestTemplate() {
        RestTemplate rt = new RestTemplate();
        // 사내 호출은 빠르게 fail-fast — 5s/10s
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        rt.setRequestFactory(factory);
        return rt;
    }
}
