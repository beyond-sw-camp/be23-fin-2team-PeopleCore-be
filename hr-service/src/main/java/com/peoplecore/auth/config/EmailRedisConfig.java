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
public class EmailRedisConfig {

    @Value("${spring.data.redis.host1}")
    private String redisHost1;

    @Value("${spring.data.redis.port1}")
    private int redisPort1;

    @Bean
    @Qualifier("emailRedisConnectionFactory")
    public RedisConnectionFactory emailRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost1);
        config.setPort(redisPort1);
        config.setDatabase(2);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    @Qualifier("emailRedisTemplate")
    public StringRedisTemplate emailRedisTemplate(
            @Qualifier("emailRedisConnectionFactory") RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
