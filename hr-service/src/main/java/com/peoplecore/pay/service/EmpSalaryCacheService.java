package com.peoplecore.pay.service;

import com.peoplecore.pay.dtos.EmpSalaryDetailResDto;
import com.peoplecore.pay.dtos.ExpectedDeductionSummaryResDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * 사원별 급여 관리(관리자) 캐시.
 * 상세/예상공제 캐싱. 목록은 필터 조합이 많아 캐싱 제외.
 */
@Slf4j
@Service
public class EmpSalaryCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final Duration TTL_DETAIL   = Duration.ofHours(1);
    private static final Duration TTL_EXPECTED = Duration.ofMinutes(30);

    private static final String PREFIX_DETAIL   = "empsalary:detail";
    private static final String PREFIX_EXPECTED = "empsalary:expected";

    public EmpSalaryCacheService(
            @Qualifier("hrCacheRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ── 상세 ──
    public EmpSalaryDetailResDto getDetailCache(UUID companyId, Long empId, Integer year) {
        return get(detailKey(companyId, empId, year), EmpSalaryDetailResDto.class);
    }

    public void cacheDetail(UUID companyId, Long empId, Integer year, EmpSalaryDetailResDto value) {
        set(detailKey(companyId, empId, year), value, TTL_DETAIL);
    }

    /** 한 사원의 모든 year 키 일괄 삭제. 계좌/연봉계약 변경 시 호출. */
    public void evictByEmpId(UUID companyId, Long empId) {
        deleteByPattern(PREFIX_DETAIL + ":" + companyId + ":" + empId + ":*");
    }

    private String detailKey(UUID companyId, Long empId, Integer year) {
        String yearPart = (year != null) ? String.valueOf(year) : LocalDate.now().toString();
        return String.format("%s:%s:%d:%s", PREFIX_DETAIL, companyId, empId, yearPart);
    }

    // ── 예상공제 ──
    public ExpectedDeductionSummaryResDto getExpectedCache(UUID companyId) {
        return get(expectedKey(companyId), ExpectedDeductionSummaryResDto.class);
    }

    public void cacheExpected(UUID companyId, ExpectedDeductionSummaryResDto value) {
        set(expectedKey(companyId), value, TTL_EXPECTED);
    }

    /** 회사 단위 예상공제 캐시 무효화. 연봉계약/보험요율/세액표 변경 시 호출. */
    public void evictExpected(UUID companyId) {
        deleteByPattern(PREFIX_EXPECTED + ":" + companyId + ":*");
    }

    private String expectedKey(UUID companyId) {
        // 오늘 시점 기준이라 날짜를 키에 포함 → 자정 넘어가면 새 키로 자연 분기
        return String.format("%s:%s:%s", PREFIX_EXPECTED, companyId, LocalDate.now());
    }




    @SuppressWarnings("unchecked")
    private <T> T get(String key, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) return null;
            if (clazz.isInstance(value)) return (T) value;
            log.warn("[EmpSalaryCache] 타입 불일치 - key={}, expect={}, actual={}",
                    key, clazz.getSimpleName(), value.getClass().getSimpleName());
            return null;
        } catch (Exception e) {
            log.warn("[EmpSalaryCache] get 실패 - key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    private void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("[EmpSalaryCache] set 실패 - key={}, err={}", key, e.getMessage());
        }
    }

    private void deleteByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("[EmpSalaryCache] deleteByPattern 실패 - pattern={}, err={}",
                    pattern, e.getMessage());
        }
    }
}