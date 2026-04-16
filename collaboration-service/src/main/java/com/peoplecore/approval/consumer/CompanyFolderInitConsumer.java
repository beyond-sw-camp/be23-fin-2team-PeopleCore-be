package com.peoplecore.approval.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.service.ApprovalFormService;
import com.peoplecore.event.CompanyCreateEvent;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class CompanyFolderInitConsumer {

    private final ApprovalFormService formService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CompanyFolderInitConsumer(ApprovalFormService formService, ObjectMapper objectMapper) {
        this.formService = formService;
        this.objectMapper = objectMapper;
    }

    /* attempts 총시도 횟수, backoff: 재시도 간격(1초), multiplier => 2 시도 때마다 * 2초*/
    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2))
    @KafkaListener(topics = "company-folder-init", groupId = "collaboration-folder-init")
    public void folderInitConsume(String message) {
        try {
            CompanyCreateEvent event = objectMapper.readValue(message, CompanyCreateEvent.class);
            formService.initFormFolder(event.getCompanyId());
        } catch (Exception e) {
            log.error("양식 폴더 초기화 이벤트 처리 실패: {}", e.getMessage());
            throw new BusinessException("오류 발생");
        }
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("양식 폴더 초기화 최종 실패 - DLT 처리, message: {}", message);
    }
}
