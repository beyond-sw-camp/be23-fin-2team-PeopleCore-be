package com.peoplecore.approval.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.service.ApprovalFormService;
import com.peoplecore.event.CompanyCreateEvent;
import com.peoplecore.exception.BusinessException;
import com.peoplecore.filevault.entity.FolderType;
import com.peoplecore.filevault.repository.FileFolderRepository;
import com.peoplecore.filevault.service.FileFolderService;
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
    private final FileFolderService folderService;
    private final FileFolderRepository folderRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public CompanyFolderInitConsumer(ApprovalFormService formService,
                                     FileFolderService folderService,
                                     FileFolderRepository folderRepository,
                                     ObjectMapper objectMapper) {
        this.formService = formService;
        this.folderService = folderService;
        this.folderRepository = folderRepository;
        this.objectMapper = objectMapper;
    }

    /* attempts 총시도 횟수, backoff: 재시도 간격(1초), multiplier => 2 시도 때마다 * 2초*/
    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2))
    @KafkaListener(topics = "company-folder-init", groupId = "collaboration-folder-init")
    public void folderInitConsume(String message) {
        try {
            CompanyCreateEvent event = objectMapper.readValue(message, CompanyCreateEvent.class);
            formService.initFormFolder(event.getCompanyId());

            // 멱등성 — 이미 COMPANY 루트가 있으면 스킵 (이벤트 재전달/중복 수신 대비)
            boolean alreadyExists = !folderRepository
                .findByCompanyIdAndTypeAndParentFolderIdIsNullAndDeletedAtIsNull(
                    event.getCompanyId(), FolderType.COMPANY)
                .isEmpty();
            if (alreadyExists) {
                log.info("[FileVault] 전사 기본 파일함 이미 존재 — 생성 스킵 companyId={}", event.getCompanyId());
                return;
            }

            folderService.createSystemDefaultFolder(
                event.getCompanyId(), "전사 파일함", FolderType.COMPANY, null, null, 0L);
            log.info("[FileVault] 전사 기본 파일함 생성 완료 companyId={}", event.getCompanyId());
        } catch (Exception e) {
            log.error("폴더 초기화 이벤트 처리 실패: {}", e.getMessage());
            throw new BusinessException("오류 발생");
        }
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("양식 폴더 초기화 최종 실패 - DLT 처리, message: {}", message);
    }
}
