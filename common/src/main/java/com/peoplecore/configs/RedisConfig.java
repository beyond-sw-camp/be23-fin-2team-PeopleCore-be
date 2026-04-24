package com.peoplecore.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host1}")
    private String redisHost1;
    @Value("${spring.data.redis.port1}")
    private int redisPort1;

    @Value("${spring.data.redis.host2}")
    private String redisHost2;
    @Value("${spring.data.redis.port2}")
    private int redisPort2;

    // =====================================================================
    //  Redis 1 - DB0: RefreshToken
    // =====================================================================
    @Primary
    @Bean
    @Qualifier("refreshTokenRedisConnectionFactory")
    public RedisConnectionFactory refreshTokenRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost1);
        config.setPort(redisPort1);
        config.setDatabase(0);
        return new LettuceConnectionFactory(config);
    }

    @Primary
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

    // =====================================================================
//  Redis 2 - DB0: HR 캐시 (부서, 회사 정보)
// =====================================================================
    @Bean
    @Qualifier("hrCacheRedisConnectionFactory")
    public RedisConnectionFactory hrCacheRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost2);
        config.setPort(redisPort2);
        config.setDatabase(0);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    @Qualifier("hrCacheRedisTemplate")
    public RedisTemplate<String, Object> hrCacheRedisTemplate(
            @Qualifier("hrCacheRedisConnectionFactory") RedisConnectionFactory factory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // @class 타입 정보를 JSON 에 심어서 역직렬화 시 LinkedHashMap 아닌 실제 DTO 로 복원
        // 보안: 허용 패키지만 역직렬화 가능
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.peoplecore")
                        .allowIfSubType("java.util")
                        .allowIfSubType("java.time")
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL);

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        return template;
    }

}
