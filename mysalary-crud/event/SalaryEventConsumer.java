package com.peoplecore.pay.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.pay.cache.MySalaryCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 급여 이벤트 Kafka 소비자
 * - 급여 관련 이벤트 수신 시 Redis 캐시 무효화
 * - 사원별 캐시를 재귀적으로 처리
 */
@Slf4j
@Component
public class SalaryEventConsumer {

    @Autowired
    private MySalaryCacheService cacheService;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "salary-event", groupId = "hr-salary-cache")
    public void consume(String message) {
        try {
            SalaryEvent event = objectMapper.readValue(message, SalaryEvent.class);
            log.info("[SalaryEvent] 이벤트 수신 - type: {}, company: {}",
                    event.getEventType(), event.getCompanyId());

            processEventByType(event);
        } catch (Exception e) {
            log.error("[SalaryEvent] 이벤트 처리 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 이벤트 타입별 캐시 무효화 처리
     */
    private void processEventByType(SalaryEvent event) {
        switch (event.getEventType()) {
            case SalaryEvent.PAY_STUB_CREATED, SalaryEvent.PAYROLL_CONFIRMED ->
                    evictCachesRecursive(event.getCompanyId(), event.getEmpIds(), 0);
            case SalaryEvent.PENSION_UPDATED ->
                    evictPensionCachesRecursive(event.getCompanyId(), event.getEmpIds(), 0);
            case SalaryEvent.SALARY_INFO_UPDATED ->
                    evictSalaryInfoRecursive(event.getCompanyId(), event.getEmpIds(), 0);
            default ->
                    log.warn("[SalaryEvent] 알 수 없는 이벤트 타입: {}", event.getEventType());
        }
    }

    /**
     * 사원 목록의 전체 급여 캐시 재귀 무효화
     */
    private void evictCachesRecursive(UUID companyId, List<Long> empIds, int index) {
        if (index >= empIds.size()) {
            return;
        }
        cacheService.evictAllSalaryCache(companyId, empIds.get(index));
        evictCachesRecursive(companyId, empIds, index + 1);
    }

    /**
     * 사원 목록의 퇴직연금 캐시 재귀 무효화
     */
    private void evictPensionCachesRecursive(UUID companyId, List<Long> empIds, int index) {
        if (index >= empIds.size()) {
            return;
        }
        cacheService.evictAllSalaryCache(companyId, empIds.get(index));
        evictPensionCachesRecursive(companyId, empIds, index + 1);
    }

    /**
     * 사원 목록의 급여 정보 캐시 재귀 무효화
     */
    private void evictSalaryInfoRecursive(UUID companyId, List<Long> empIds, int index) {
        if (index >= empIds.size()) {
            return;
        }
        cacheService.evictSalaryInfoCache(companyId, empIds.get(index));
        evictSalaryInfoRecursive(companyId, empIds, index + 1);
    }
}
