package com.peoplecore.client.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.discovery.converters.Auto;
import com.peoplecore.client.component.HrCacheService;
import com.peoplecore.event.DeptUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/*hr-ser에서 보낸 카프카 메시지 수신후 레디스 캐시 삭제 */
@Component
@Slf4j
public class HrEventConsumer {
    private final HrCacheService hrCacheService;
    private final ObjectMapper objectMapper;

    @Autowired
    public HrEventConsumer(HrCacheService hrCacheService, ObjectMapper objectMapper) {
        this.hrCacheService = hrCacheService;
        this.objectMapper = objectMapper;
    }

    /*groupId를 서비스 명으로 지정 . 누가 받아도 상관 없기 때문에 그룹아이디 고정 */
    @KafkaListener(topics = "hr-dept-updated", groupId = "collaboration-service")
    public void handleDeptUpdated(String message) {
        try {
            DeptUpdatedEvent event = objectMapper.readValue(message, DeptUpdatedEvent.class);
            hrCacheService.evictDept(event.getDeptId());
            log.info("부서 캐시 무효화 완료 deptId = {}", event.getDeptId());
        } catch (Exception e) {
            log.error("부서 변경 이벤트 처리 싪패 error = {}", e.getMessage());
        }
    }
}
