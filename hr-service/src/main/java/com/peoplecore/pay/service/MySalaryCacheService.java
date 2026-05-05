package com.peoplecore.pay.service;

import com.peoplecore.pay.dtos.MySeveranceEstimateResDto;
import com.peoplecore.pay.dtos.PayStubDetailResDto;
import com.peoplecore.pay.dtos.PayStubListResDto;
import com.peoplecore.pay.dtos.PensionInfoResDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 내 급여 조회 캐시.
 * 내 급여·명세서·퇴직연금·추계액 Redis 캐시.
 */
@Slf4j
@Service
public class MySalaryCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    // ── TTL (캐시 자동 만료 시간) ──
    private static final Duration TTL_SALARY_INFO      = Duration.ofHours(1);
    private static final Duration TTL_STUB_LIST        = Duration.ofHours(24);
    private static final Duration TTL_STUB_DETAIL      = Duration.ofDays(7);
    private static final Duration TTL_PENSION          = Duration.ofHours(1);
    private static final Duration TTL_SEVERANCE_EST    = Duration.ofHours(1);

    // ── Key prefix ──
    private static final String PREFIX_SALARY_INFO    = "mysalary:info";
    private static final String PREFIX_STUB_LIST      = "mysalary:stubs";
    private static final String PREFIX_STUB_DETAIL    = "mysalary:stub-detail";
    private static final String PREFIX_PENSION        = "mysalary:pension";
    private static final String PREFIX_SEVERANCE_EST  = "mysalary:severance-estimate";

    public MySalaryCacheService(
            @Qualifier("hrCacheRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    // ── 내 급여 정보 ──
    public <T> T getSalaryInfoCache(UUID companyId, Long empId, Class<T> clazz) {
        return get(salaryInfoKey(companyId, empId), clazz);
    }

    public void cacheSalaryInfo(UUID companyId, Long empId, Object value) {
        set(salaryInfoKey(companyId, empId), value, TTL_SALARY_INFO);
    }

    public void evictSalaryInfoCache(UUID companyId, Long empId) {
        delete(salaryInfoKey(companyId, empId));
    }

    private String salaryInfoKey(UUID companyId, Long empId) {
        return String.format("%s:%s:%d", PREFIX_SALARY_INFO, companyId, empId);
    }

    // ───────── 명세서 목록 ─────────

    @SuppressWarnings("unchecked")
    public List<PayStubListResDto> getStubListCache(UUID companyId, Long empId, String year) {
        return (List<PayStubListResDto>) get(stubListKey(companyId, empId, year), List.class);
    }

    public void cacheStubList(UUID companyId, Long empId, String year,
                              List<PayStubListResDto> value) {
        set(stubListKey(companyId, empId, year), value, TTL_STUB_LIST);
    }

    public void evictStubListCache(UUID companyId, Long empId) {
        // year 가 여럿일 수 있으니 패턴으로 삭제
        deleteByPattern(PREFIX_STUB_LIST + ":" + companyId + ":" + empId + ":*");
    }

    private String stubListKey(UUID companyId, Long empId, String year) {
        return String.format("%s:%s:%d:%s", PREFIX_STUB_LIST, companyId, empId, year);
    }

    // ───────── 명세서 상세 ─────────

    public PayStubDetailResDto getStubDetailCache(UUID companyId, Long empId, Long stubId) {
        return get(stubDetailKey(companyId, empId, stubId), PayStubDetailResDto.class);
    }

    public void cacheStubDetail(UUID companyId, Long empId, Long stubId,
                                PayStubDetailResDto value) {
        set(stubDetailKey(companyId, empId, stubId), value, TTL_STUB_DETAIL);
    }

    /** 특정 급여 명세서 상세 1건만 무효화 (stubId 가 명확할 때) */
    public void evictStubDetailCache(UUID companyId, Long empId, Long stubId) {
        delete(stubDetailKey(companyId, empId, stubId));
    }

    /** 사원의 모든 급여 명세서 상세 캐시 일괄 무효화 (월/연도 범위 모름·다건일 때) */
    public void evictAllStubDetailCache(UUID companyId, Long empId) {
        deleteByPattern(PREFIX_STUB_DETAIL + ":" + companyId + ":" + empId + ":*");
    }

    private String stubDetailKey(UUID companyId, Long empId, Long stubId) {
        return String.format("%s:%s:%d:%d", PREFIX_STUB_DETAIL, companyId, empId, stubId);
    }

    // ───────── 퇴직연금 ─────────

    public PensionInfoResDto getPensionCache(UUID companyId, Long empId) {
        return get(pensionKey(companyId, empId), PensionInfoResDto.class);
    }

    public void cachePensionInfo(UUID companyId, Long empId, PensionInfoResDto value) {
        set(pensionKey(companyId, empId), value, TTL_PENSION);
    }

    public void evictPensionCache(UUID companyId, Long empId) {
        delete(pensionKey(companyId, empId));
    }

    private String pensionKey(UUID companyId, Long empId) {
        return String.format("%s:%s:%d", PREFIX_PENSION, companyId, empId);
    }

    // ───────── 퇴직금 예상(추계액) ─────────

    public <T> T getSeveranceEstimateCache(
            UUID companyId, Long empId, LocalDate baseDate, Class<T> clazz) {
        return get(severanceEstKey(companyId, empId, baseDate), clazz);
    }

    public void cacheSeveranceEstimate(
            UUID companyId, Long empId, LocalDate baseDate, MySeveranceEstimateResDto value) {
        set(severanceEstKey(companyId, empId, baseDate), value, TTL_SEVERANCE_EST);
    }

    public void evictSeveranceEstimateCache(UUID companyId, Long empId) {
        deleteByPattern(PREFIX_SEVERANCE_EST + ":" + companyId + ":" + empId + ":*");
    }

    private String severanceEstKey(UUID companyId, Long empId, LocalDate baseDate) {
        return String.format("%s:%s:%d:%s", PREFIX_SEVERANCE_EST, companyId, empId, baseDate);
    }



    @SuppressWarnings("unchecked")
    private <T> T get(String key, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) return null;
            if (clazz.isInstance(value)) return (T) value;
            log.warn("[MySalaryCache] 타입 불일치 - key={}, expect={}, actual={}",
                    key, clazz.getSimpleName(), value.getClass().getSimpleName());
            return null;
        } catch (Exception e) {
            // Redis 장애가 서비스 중단으로 이어지지 않도록 swallow
            log.warn("[MySalaryCache] get 실패 - key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    private void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("[MySalaryCache] set 실패 - key={}, err={}", key, e.getMessage());
        }
    }

    private void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[MySalaryCache] delete 실패 - key={}, err={}", key, e.getMessage());
        }
    }

    private void deleteByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("[MySalaryCache] deleteByPattern 실패 - pattern={}, err={}",
                    pattern, e.getMessage());
        }
    }
}