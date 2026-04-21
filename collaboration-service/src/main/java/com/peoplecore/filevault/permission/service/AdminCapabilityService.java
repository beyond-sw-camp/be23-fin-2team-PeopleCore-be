package com.peoplecore.filevault.permission.service;

import com.peoplecore.exception.BusinessException;
import com.peoplecore.filevault.permission.dto.AdminCapabilityConfigResponse;
import com.peoplecore.filevault.permission.dto.AdminCapabilityUpdateRequest;
import com.peoplecore.filevault.permission.dto.MyAdminCapabilityResponse;
import com.peoplecore.filevault.permission.entity.FileBoxAdminConfig;
import com.peoplecore.filevault.permission.entity.FileBoxAdminGrant;
import com.peoplecore.filevault.permission.entity.FileBoxAdminMode;
import com.peoplecore.filevault.permission.repository.FileBoxAdminConfigRepository;
import com.peoplecore.filevault.permission.repository.FileBoxAdminGrantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 파일함 Admin 권한 (Tier-1) 서비스.
 *
 * <p>회사별 단 1행의 {@link FileBoxAdminConfig} (mode = GRADE | TITLE) 와
 * 그 모드에 부여된 대상 id 집합 ({@link FileBoxAdminGrant}) 을 관리한다.
 * 모드 전환은 항상 grants 전부 삭제 후 재삽입이며, 0-admin 상태는 막는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AdminCapabilityService {

    private static final FileBoxAdminMode DEFAULT_MODE = FileBoxAdminMode.GRADE;

    private final FileBoxAdminConfigRepository configRepository;
    private final FileBoxAdminGrantRepository grantRepository;

    public AdminCapabilityConfigResponse getConfig(UUID companyId) {
        FileBoxAdminMode mode = configRepository.findByCompanyId(companyId)
            .map(FileBoxAdminConfig::getMode)
            .orElse(DEFAULT_MODE);
        List<Long> targetIds = grantRepository.findByCompanyIdAndMode(companyId, mode).stream()
            .map(FileBoxAdminGrant::getTargetId)
            .toList();
        return AdminCapabilityConfigResponse.builder()
            .mode(mode)
            .grantedTargetIds(targetIds)
            .build();
    }

    @Transactional
    public AdminCapabilityConfigResponse updateConfig(UUID companyId, AdminCapabilityUpdateRequest request) {
        if (request.getGrantedTargetIds() == null || request.getGrantedTargetIds().isEmpty()) {
            throw new BusinessException("최소 1개 이상의 대상이 지정되어야 합니다.", HttpStatus.BAD_REQUEST);
        }
        Set<Long> dedup = new HashSet<>(request.getGrantedTargetIds());

        FileBoxAdminConfig config = configRepository.findByCompanyId(companyId)
            .orElseGet(() -> FileBoxAdminConfig.builder()
                .companyId(companyId)
                .mode(request.getMode())
                .build());

        boolean modeChanged = config.getMode() != request.getMode();
        if (modeChanged) {
            config.changeMode(request.getMode());
        }
        configRepository.save(config);

        // 원자적 교체 — 회사 전체 grant 삭제 후 재삽입
        grantRepository.deleteByCompanyId(companyId);
        grantRepository.flush();

        List<FileBoxAdminGrant> newGrants = dedup.stream()
            .map(targetId -> FileBoxAdminGrant.builder()
                .companyId(companyId)
                .mode(request.getMode())
                .targetId(targetId)
                .build())
            .toList();
        grantRepository.saveAll(newGrants);

        log.info("파일함 Admin 권한 갱신 companyId={}, mode={}, targetCount={}",
            companyId, request.getMode(), dedup.size());

        return AdminCapabilityConfigResponse.builder()
            .mode(request.getMode())
            .grantedTargetIds(dedup.stream().toList())
            .build();
    }

    public MyAdminCapabilityResponse me(UUID companyId, Long gradeId, Long titleId) {
        return MyAdminCapabilityResponse.builder()
            .isAdmin(isAdmin(companyId, gradeId, titleId))
            .build();
    }

    /**
     * 현재 회사 모드에 따라 사용자의 grade/title id 가 grants 에 포함돼 있으면 true.
     */
    public boolean isAdmin(UUID companyId, Long gradeId, Long titleId) {
        FileBoxAdminMode mode = configRepository.findByCompanyId(companyId)
            .map(FileBoxAdminConfig::getMode)
            .orElse(DEFAULT_MODE);
        Long subjectId = mode == FileBoxAdminMode.GRADE ? gradeId : titleId;
        if (subjectId == null) return false;
        return grantRepository.existsByCompanyIdAndModeAndTargetIdIn(
            companyId, mode, List.of(subjectId));
    }
}
