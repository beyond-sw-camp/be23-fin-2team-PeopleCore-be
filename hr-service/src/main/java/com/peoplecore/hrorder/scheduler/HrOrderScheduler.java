package com.peoplecore.hrorder.scheduler;

import com.peoplecore.hrorder.service.HrOrderService;
import com.peoplecore.resign.service.ResignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

/* 인사발령/퇴직 자동 적용 스케줄러 - 매일 자정 */
/* 분산 락으로 멀티 인스턴스 중복 실행 방지 (락 키: hr-order-apply:{yyyy-MM-dd}) */
/*
 * TODO 고도화:
 *   1) Quartz JDBC 클러스터링으로 마이그레이션 — vacation/attendance 스케줄러 패턴(SchedulerConfig+Job) 동일하게.
 *      DB row lock 기반 fire 1회 보장 → 분산락 자체 불필요해짐.
 *   2) (Quartz 이전 전 임시 보강) Redis SETNX 락 안전화:
 *      - UUID 토큰 매칭 + Lua script 조건부 delete 로 'TTL 만료 후 finally 가 남의 락 지우는' 케이스 차단.
 *      - 작업 시간이 LOCK_TTL(10분) 초과 가능한지 검토 후 TTL 재산정.
 *   3) 작업 메서드 idempotency 검증 — applyAllScheduledOrders / processScheduledResigns 가
 *      중복 호출 시에도 데이터 오염 없는지 상태 가드 확인.
 */
@Component
@Slf4j
public class HrOrderScheduler {

    private static final Duration LOCK_TTL = Duration.ofMinutes(10);
    private static final String LOCK_KEY_PREFIX = "hr-order-apply";

    private final StringRedisTemplate redisTemplate;
    private final HrOrderService hrOrderService;
    private final ResignService resignService;

    public HrOrderScheduler(StringRedisTemplate redisTemplate,
                            HrOrderService hrOrderService,
                            ResignService resignService) {
        this.redisTemplate = redisTemplate;
        this.hrOrderService = hrOrderService;
        this.resignService = resignService;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void applyConfirmedOrders() {
        LocalDate today = LocalDate.now();
        String lockKey = LOCK_KEY_PREFIX + ":" + today;

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[HrOrder] 다른 인스턴스 진행 중 - skip. date={}", today);
            return;
        }
        log.info("[HrOrder] 시작 - date={}", today);
        try {
            hrOrderService.applyAllScheduledOrders();
            resignService.processScheduledResigns();
            log.info("[HrOrder] 완료 - date={}", today);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }
}
