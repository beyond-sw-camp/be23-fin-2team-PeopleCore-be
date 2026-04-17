package com.peoplecore.filevault.config;

import com.peoplecore.filevault.entity.FolderType;
import com.peoplecore.filevault.repository.FileFolderRepository;
import com.peoplecore.filevault.service.FileFolderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileVaultInitializer {

    private final FileFolderRepository folderRepository;
    private final FileFolderService folderService;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureCompanyRootFolders() {
        List<UUID> companyIds = folderRepository.findDistinctCompanyIds();
        for (UUID companyId : companyIds) {
            boolean exists = !folderRepository
                .findByCompanyIdAndTypeAndParentFolderIdIsNullAndDeletedAtIsNull(companyId, FolderType.COMPANY)
                .isEmpty();
            if (!exists) {
                folderService.createSystemDefaultFolder(
                    companyId, "전사 공용", FolderType.COMPANY, null, null, 0L);
                log.info("[FileVault] COMPANY 루트 폴더 자동 생성 companyId={}", companyId);
            }
        }
    }
}
