package com.peoplecore.pay.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 급여 이벤트 Kafka 발행자
 * - 급여명세서 생성, 급여 산정 확정, 퇴직연금 갱신 시 이벤트 발행
 * - salary-event 토픽으로 JSON 직렬화하여 발행
 */
@Slf4j
@Component
public class SalaryEventPublisher {

    private static final String TOPIC = "salary-event";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 급여 이벤트 발행
     */
    public void publish(SalaryEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.getCompanyId().toString(), payload);
            log.info("[SalaryEvent] 이벤트 발행 완료 - type: {}, company: {}, empIds: {}",
                    event.getEventType(), event.getCompanyId(), event.getEmpIds());
        } catch (Exception e) {
            log.error("[SalaryEvent] 이벤트 발행 실패 - type: {}, error: {}",
                    event.getEventType(), e.getMessage(), e);
        }
    }
}
