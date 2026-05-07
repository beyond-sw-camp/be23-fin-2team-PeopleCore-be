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
