package com.peoplecore.auth.config;

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
public class SmsRedisConfig {

    @Value("${spring.data.redis.host1}")
    private String redisHost1;

    @Value("${spring.data.redis.port1}")
    private int redisPort1;

    @Bean
    @Qualifier("smsRedisConnectionFactory")
    public RedisConnectionFactory smsRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost1);
        config.setPort(redisPort1);
        config.setDatabase(1);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    @Qualifier("smsRedisTemplate")
    public StringRedisTemplate smsRedisTemplate(
            @Qualifier("smsRedisConnectionFactory") RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
