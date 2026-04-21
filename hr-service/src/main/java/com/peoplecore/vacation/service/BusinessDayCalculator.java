package com.peoplecore.vacation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.attendance.repository.HolidayLookupRepository;
import com.peoplecore.entity.Holidays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/* 영업일 계산기 - 주말(토/일) + 공휴일 제외 일수 산출 */
/* 캐시: (companyId, yearMonth) → 공휴일 LocalDate Set (JSON). TTL 6시간 + write-through evict */
/* 공휴일 변경 시 evictMonth/evictCompany/evictAll 로 즉시 무효화 가능 (정합성 보장) */
/* 스케줄러(만근 판정, 월차 적립, 연차 발생) + 출퇴근 판정(CommuteService) 에서 재사용 */
@Component
@Slf4j
public class BusinessDayCalculator {

    /* 캐시 TTL - 공휴일 마스터 변경 주기가 느려 6시간으로 충분 */
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    /* 캐시 키 prefix - biz-holidays:{companyId}:{yyyy-MM} */
    private static final String CACHE_KEY_PREFIX = "biz-holidays";

    private final HolidayLookupRepository holidayLookupRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public BusinessDayCalculator(HolidayLookupRepository holidayLookupRepository,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper) {
        this.holidayLookupRepository = holidayLookupRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /* 영업일 수 산출 - [start, end] 포함. 주말 + 공휴일 제외 */
    /* start > end 이면 0 반환 (방어) */
    public int countBusinessDays(UUID companyId, LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end)) return 0;
        Set<LocalDate> holidays = loadHolidaysInRange(companyId, start, end);
        int count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (isBusinessDay(d, holidays)) count++;
        }
        return count;
    }

    /* 단일 날짜 영업일 여부 - 단건 조회 시에도 캐시 경유 (월 단위 캐시라 일 단위 호출 반복에도 이득) */
    public boolean isBusinessDay(UUID companyId, LocalDate date) {
        if (date == null) return false;
        Set<LocalDate> holidays = loadHolidaysInRange(companyId, date, date);
        return isBusinessDay(date, holidays);
    }

    /* 월 단위 공휴일 Set 외부 노출 - 출퇴근 판정(CommuteService) 등에서 캐시 공유용 */
    /* 반복 공휴일은 호출 연도에 맞춰 날짜가 매핑된 상태로 반환 */
    public Set<LocalDate> getHolidaysInMonth(UUID companyId, YearMonth ym) {
        if (companyId == null || ym == null) return Set.of();
        return loadHolidaysForMonth(companyId, ym);
    }

    /* 특정 회사의 특정 월 캐시 무효화 - 일회성 공휴일(isRepeating=false) 추가/수정/삭제 시 */
    public void evictMonth(UUID companyId, YearMonth ym) {
        if (companyId == null || ym == null) return;
        redisTemplate.delete(cacheKey(companyId, ym));
    }

    /* 특정 회사 전체 캐시 무효화 - 반복 공휴일(isRepeating=true) 변경 시 모든 연/월 영향 */
    public void evictCompany(UUID companyId) {
        if (companyId == null) return;
        Set<String> keys = scanKeys(CACHE_KEY_PREFIX + ":" + companyId + ":*");
        if (!keys.isEmpty()) redisTemplate.delete(keys);
    }

    /* 전역 캐시 무효화 - NATIONAL 공휴일(전 회사 공유) 변경 시 */
    public void evictAll() {
        Set<String> keys = scanKeys(CACHE_KEY_PREFIX + ":*");
        if (!keys.isEmpty()) redisTemplate.delete(keys);
    }

    /* 주말 + 공휴일 제외 - 내부 헬퍼 (holidays Set 이미 로드된 상태) */
    private boolean isBusinessDay(LocalDate date, Set<LocalDate> holidays) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false;
        return !holidays.contains(date);
    }

    /* 기간이 여러 달에 걸쳐있을 수 있어 월별 캐시 합집합 로드 */
    private Set<LocalDate> loadHolidaysInRange(UUID companyId, LocalDate start, LocalDate end) {
        Set<LocalDate> merged = new HashSet<>();
        YearMonth cursor = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        while (!cursor.isAfter(last)) {
            merged.addAll(loadHolidaysForMonth(companyId, cursor));
            cursor = cursor.plusMonths(1);
        }
        return merged;
    }

    /* 회사 + 년월 공휴일 Set - Redis 캐시 확인 → miss 면 DB 조회 + 캐싱 */
    /* 캐시 역직렬화/저장 실패 시 로그만 남기고 DB 경유 (신뢰성 > 캐시 성능) */
    private Set<LocalDate> loadHolidaysForMonth(UUID companyId, YearMonth ym) {
        String key = cacheKey(companyId, ym);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                List<String> dates = objectMapper.readValue(cached, new TypeReference<List<String>>() {});
                return dates.stream().map(LocalDate::parse).collect(Collectors.toSet());
            } catch (Exception e) {
                log.warn("[BusinessDayCalculator] 캐시 역직렬화 실패 - key={}, err={}", key, e.getMessage());
            }
        }
        Set<LocalDate> fromDb = queryHolidaysForMonth(companyId, ym);
        try {
            List<String> asStrings = fromDb.stream().map(LocalDate::toString).sorted().toList();
            String json = objectMapper.writeValueAsString(asStrings);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL);
        } catch (Exception e) {
            log.warn("[BusinessDayCalculator] 캐시 저장 실패 - key={}, err={}", key, e.getMessage());
        }
        return fromDb;
    }

    /* 해당 월의 일자별로 findMatching 호출 - isRepeating/비반복 모두 포함 */
    /* ~30회 호출이지만 캐시 miss 때만 발생 (스케줄러 하루 1~2회 실행 → 무시 가능) */
    private Set<LocalDate> queryHolidaysForMonth(UUID companyId, YearMonth ym) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        List<Holidays> matched = holidayLookupRepository.findMatchingForMonth(companyId, ym.getMonthValue(), start, end);

        /* isRepeating = true 인 경우 date 가 기준 연도가 아닐 수 있어 해당 월의 실제 날짜로 변환 */
        Set<LocalDate> result = new HashSet<>();
        for (Holidays h : matched) {
            if (h.getIsRepeating()) {
                /*매년 반복 - 저장된 date의 월/일을 현재 연도 월에 매핑*/
                int day = h.getDate().getDayOfMonth();
                if (day <= end.getDayOfMonth()) {
                    result.add(LocalDate.of(ym.getYear(), ym.getMonth(), day));
                }
            } else {
                result.add(h.getDate());
            }
        }
        return result;
    }

    /* 캐시 키 - biz-holidays:{companyId}:{yyyy-MM} */
    private String cacheKey(UUID companyId, YearMonth ym) {
        return String.format("%s:%s:%s", CACHE_KEY_PREFIX, companyId, ym);
    }

    /* Redis SCAN 기반 키 조회 - KEYS 는 O(N) 블로킹이라 프로덕션 금지, SCAN 은 커서 기반 비블로킹 */
    /* 실패 시 빈 Set 반환 → 호출부 delete 가 no-op (캐시 미삭제는 TTL 만료로 자연 복구) */
    private Set<String> scanKeys(String pattern) {
        Set<String> result = new HashSet<>();
        try {
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        result.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("[BusinessDayCalculator] 캐시 SCAN 실패 - pattern={}, err={}", pattern, e.getMessage());
        }
        return result;
    }
}