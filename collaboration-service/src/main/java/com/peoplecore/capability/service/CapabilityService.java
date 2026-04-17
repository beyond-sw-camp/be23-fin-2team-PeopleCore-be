package com.peoplecore.capability.service;

import com.peoplecore.capability.entity.Capability;
import com.peoplecore.capability.entity.TitleCapability;
import com.peoplecore.capability.repository.CapabilityRepository;
import com.peoplecore.capability.repository.TitleCapabilityRepository;
import com.peoplecore.client.component.HrCacheService;
import com.peoplecore.client.dto.TitleInfoResponse;
import com.peoplecore.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 파일함 권한 조회/부여/회수 서비스.
 *
 * <p>권한 판정은 {@code titleId} 단위로 이루어진다. 호출측(파일함 컨트롤러 등)은
 * 인증 컨텍스트에서 얻은 사용자 titleId 를 넘겨 {@link #hasCapability} 로 검사한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CapabilityService {

    private static final String UNASSIGNED_TITLE_CODE = "000";

    private final CapabilityRepository capabilityRepository;
    private final TitleCapabilityRepository titleCapabilityRepository;
    private final HrCacheService hrCacheService;

    public List<Capability> listAll() {
        return capabilityRepository.findAll();
    }

    public List<Capability> listByCategory(String category) {
        return capabilityRepository.findByCategory(category);
    }

    public List<TitleCapability> listByTitle(Long titleId) {
        return titleCapabilityRepository.findByTitleId(titleId);
    }

    public boolean hasCapability(Long titleId, String capabilityCode) {
        if (titleId == null || capabilityCode == null) return false;
        return titleCapabilityRepository.existsByTitleIdAndCapabilityCode(titleId, capabilityCode);
    }

    @Transactional
    public TitleCapability grant(UUID companyId, Long titleId, String capabilityCode) {
        if (!capabilityRepository.existsById(capabilityCode)) {
            throw new IllegalArgumentException("Unknown capability: " + capabilityCode);
        }
        ensureNotUnassignedTitle(titleId);
        if (titleCapabilityRepository.existsByTitleIdAndCapabilityCode(titleId, capabilityCode)) {
            return titleCapabilityRepository.findByTitleId(titleId).stream()
                .filter(tc -> tc.getCapabilityCode().equals(capabilityCode))
                .findFirst().orElseThrow();
        }
        return titleCapabilityRepository.save(
            TitleCapability.builder()
                .companyId(companyId)
                .titleId(titleId)
                .capabilityCode(capabilityCode)
                .build()
        );
    }

    /** 시스템 기본 '미배정'(titleCode='000') 직책에는 권한 부여 불가. */
    private void ensureNotUnassignedTitle(Long titleId) {
        TitleInfoResponse title = hrCacheService.getTitle(titleId);
        if (title != null && UNASSIGNED_TITLE_CODE.equals(title.getTitleCode())) {
            throw new BusinessException("'미배정' 직책에는 권한을 부여할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public void revoke(Long titleId, String capabilityCode) {
        titleCapabilityRepository.deleteByTitleIdAndCapabilityCode(titleId, capabilityCode);
    }
}
