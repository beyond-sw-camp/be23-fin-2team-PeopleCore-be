package com.peoplecore.pay.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class AccountVerifyRedisConfig {

    @Value("${spring.data.redis.host1}")
    private String redisHost;

    @Value("${spring.data.redis.port1}")
    private int redisPort;

    @Bean
    @Qualifier("accountVerifyRedisConnectionFactory")
    public RedisConnectionFactory accountVerifyRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(3);   // SMS=1, Email=2 사용 중 → 3 사용
        return new LettuceConnectionFactory(config);
    }

    @Bean
    @Qualifier("accountVerifyRedisTemplate")
    public StringRedisTemplate accountVerifyRedisTemplate(
            @Qualifier("accountVerifyRedisConnectionFactory") RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
