package com.peoplecore.common.configs;

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
public class RedisConfig {

    @Value("${spring.data.redis.host1}")
    private String redisHost1;
    @Value("${spring.data.redis.port1}")
    private int redisPort1;

    // =====================================================================
    //  Redis 1 - DB0: RefreshToken
    // =====================================================================
    @Bean
    @Qualifier("refreshTokenRedisConnectionFactory")
    public RedisConnectionFactory refreshTokenRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost1);
        config.setPort(redisPort1);
        config.setDatabase(0);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    @Qualifier("refreshTokenRedisTemplate")
    public StringRedisTemplate refreshTokenRedisTemplate(
            @Qualifier("refreshTokenRedisConnectionFactory") RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
