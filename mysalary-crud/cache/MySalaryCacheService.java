package com.peoplecore.pay.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 내 급여 조회 Redis 캐시 서비스
 * - 급여 정보, 명세서 목록, 퇴직연금 정보를 Redis에 캐싱
 * - Kafka 이벤트 수신 시 캐시 무효화
 *
 * 캐시 키 규칙:
 *   salary:info:{companyId}:{empId}           → 급여 정보 (TTL 60분)
 *   salary:stubs:{companyId}:{empId}:{year}   → 명세서 목록 (TTL 30분)
 *   salary:stub:{companyId}:{empId}:{stubId}  → 명세서 상세 (TTL 60분)
 *   salary:pension:{companyId}:{empId}        → 퇴직연금 정보 (TTL 120분)
 */
@Slf4j
@Service
public class MySalaryCacheService {

    private static final String SALARY_INFO_KEY = "salary:info:%s:%d";
    private static final String STUB_LIST_KEY = "salary:stubs:%s:%d:%s";
    private static final String STUB_DETAIL_KEY = "salary:stub:%s:%d:%d";
    private static final String PENSION_KEY = "salary:pension:%s:%d";

    private static final long SALARY_INFO_TTL = 60;     // 60분
    private static final long STUB_LIST_TTL = 30;       // 30분
    private static final long STUB_DETAIL_TTL = 60;     // 60분
    private static final long PENSION_TTL = 120;         // 120분

    @Autowired
    @Qualifier("hrCacheRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ── 급여 정보 캐싱 ──

    public <T> void cacheSalaryInfo(UUID companyId, Long empId, T data) {
        String key = String.format(SALARY_INFO_KEY, companyId, empId);
        setCacheValue(key, data, SALARY_INFO_TTL);
    }

    public <T> Optional<T> getSalaryInfoCache(UUID companyId, Long empId, Class<T> type) {
        String key = String.format(SALARY_INFO_KEY, companyId, empId);
        return getCacheValue(key, type);
    }

    // ── 급여명세서 목록 캐싱 ──

    public <T> void cacheStubList(UUID companyId, Long empId, String year, T data) {
        String key = String.format(STUB_LIST_KEY, companyId, empId, year);
        setCacheValue(key, data, STUB_LIST_TTL);
    }

    public <T> Optional<T> getStubListCache(UUID companyId, Long empId, String year, TypeReference<T> typeRef) {
        String key = String.format(STUB_LIST_KEY, companyId, empId, year);
        return getCacheValueByTypeRef(key, typeRef);
    }

    // ── 급여명세서 상세 캐싱 ──

    public <T> void cacheStubDetail(UUID companyId, Long empId, Long stubId, T data) {
        String key = String.format(STUB_DETAIL_KEY, companyId, empId, stubId);
        setCacheValue(key, data, STUB_DETAIL_TTL);
    }

    public <T> Optional<T> getStubDetailCache(UUID companyId, Long empId, Long stubId, Class<T> type) {
        String key = String.format(STUB_DETAIL_KEY, companyId, empId, stubId);
        return getCacheValue(key, type);
    }

    // ── 퇴직연금 정보 캐싱 ──

    public <T> void cachePensionInfo(UUID companyId, Long empId, T data) {
        String key = String.format(PENSION_KEY, companyId, empId);
        setCacheValue(key, data, PENSION_TTL);
    }

    public <T> Optional<T> getPensionInfoCache(UUID companyId, Long empId, Class<T> type) {
        String key = String.format(PENSION_KEY, companyId, empId);
        return getCacheValue(key, type);
    }

    // ── 캐시 무효화 ──

    /**
     * 특정 사원의 모든 급여 관련 캐시 삭제
     */
    public void evictAllSalaryCache(UUID companyId, Long empId) {
        evictByPattern(String.format("salary:*:%s:%d*", companyId, empId));
    }

    /**
     * 특정 사원의 급여 정보 캐시만 삭제
     */
    public void evictSalaryInfoCache(UUID companyId, Long empId) {
        String key = String.format(SALARY_INFO_KEY, companyId, empId);
        redisTemplate.delete(key);
    }

    /**
     * 특정 사원의 명세서 목록 캐시 삭제
     */
    public void evictStubListCache(UUID companyId, Long empId, String year) {
        String key = String.format(STUB_LIST_KEY, companyId, empId, year);
        redisTemplate.delete(key);
    }

    // ── 내부 헬퍼 ──

    private <T> void setCacheValue(String key, T data, long ttlMinutes) {
        try {
            redisTemplate.opsForValue().set(key, data, ttlMinutes, TimeUnit.MINUTES);
            log.debug("[MySalaryCache] 캐시 저장 완료: {}", key);
        } catch (Exception e) {
            log.warn("[MySalaryCache] 캐시 저장 실패: {} - {}", key, e.getMessage());
        }
    }

    private <T> Optional<T> getCacheValue(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            T result = objectMapper.convertValue(value, type);
            log.debug("[MySalaryCache] 캐시 히트: {}", key);
            return Optional.of(result);
        } catch (Exception e) {
            log.warn("[MySalaryCache] 캐시 조회 실패: {} - {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private <T> Optional<T> getCacheValueByTypeRef(String key, TypeReference<T> typeRef) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            T result = objectMapper.convertValue(value, typeRef);
            log.debug("[MySalaryCache] 캐시 히트: {}", key);
            return Optional.of(result);
        } catch (Exception e) {
            log.warn("[MySalaryCache] 캐시 조회 실패: {} - {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private void evictByPattern(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("[MySalaryCache] 캐시 패턴 삭제: {} ({}건)", pattern, keys.size());
            }
        } catch (Exception e) {
            log.warn("[MySalaryCache] 캐시 패턴 삭제 실패: {} - {}", pattern, e.getMessage());
        }
    }
}
