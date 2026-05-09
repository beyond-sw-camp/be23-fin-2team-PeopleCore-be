package com.peoplecore.company.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

@Configuration
public class CollaborationApiConfig {

    /** local 프로필 — Eureka 디스커버리 기반 LB */
    @Bean
    @Profile("local")
    @LoadBalanced
    public RestClient.Builder restClientBuilderLocal() {
        return RestClient.builder();
    }

    /** prod 프로필 — K8s Service DNS 직접 호출 */
    @Bean
    @Profile("prod")
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
