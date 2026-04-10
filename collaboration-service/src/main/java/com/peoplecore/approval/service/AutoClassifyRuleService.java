package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.AutoClassifyRuleCreateRequest;
import com.peoplecore.approval.dto.AutoClassifyRuleReorderRequest;
import com.peoplecore.approval.dto.AutoClassifyRuleResponse;
import com.peoplecore.approval.dto.AutoClassifyRuleUpdateRequest;
import com.peoplecore.approval.entity.AutoClassifyRule;
import com.peoplecore.approval.entity.PersonalApprovalFolder;
import com.peoplecore.approval.entity.SourceBoxType;
import com.peoplecore.approval.repository.AutoClassifyRuleRepository;
import com.peoplecore.approval.repository.PersonalApprovalFolderRepository;
import com.peoplecore.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 개인 문서함 자동분류 규칙 서비스
 * - 사원 개인 단위로 규칙 CRUD 및 순서 관리
 * - 발신(SENT)/수신(INBOX) 문서함 기준으로 조건 매칭
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutoClassifyRuleService {

    private final AutoClassifyRuleRepository ruleRepository;
    private final PersonalApprovalFolderRepository personalFolderRepository;

    /**
     * 규칙 목록 조회
     * - 해당 사원의 모든 자동분류 규칙을 우선순위(sortOrder) 순으로 반환
     */
    public List<AutoClassifyRuleResponse> getList(UUID companyId, Long empId) {
        return ruleRepository.findByCompanyIdAndEmpIdOrderBySortOrder(companyId, empId).stream()
                .map(rule -> {
                    /* 대상 개인 문서함 이름 조회 */
                    String folderName = personalFolderRepository.findById(rule.getTargetFolderId())
                            .map(PersonalApprovalFolder::getFolderName)
                            .orElse(null);
                    return AutoClassifyRuleResponse.from(rule, folderName);
                })
                .toList();
    }

    /**
     * 규칙 생성
     * - sourceBox(SENT/INBOX) 파싱, 대상 개인 문서함 존재 여부 검증
     * - sortOrder는 기존 최대값 + 1로 자동 부여
     */
    @Transactional
    public AutoClassifyRuleResponse create(UUID companyId, Long empId, AutoClassifyRuleCreateRequest request) {
        /* 소스 문서함 타입 파싱 */
        SourceBoxType sourceBoxType = SourceBoxType.valueOf(request.getSourceBox());

        /* 대상 개인 문서함 검증 (본인 소유 확인) */
        PersonalApprovalFolder targetFolder = personalFolderRepository
                .findByPersonalFolderIdAndCompanyIdAndEmpId(request.getTargetFolderId(), companyId, empId)
                .orElseThrow(() -> new BusinessException("대상 개인 문서함을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        /* 우선순위: 기존 최대값 + 1 */
        Integer maxSortOrder = ruleRepository.findMaxSortOrder(companyId, empId);

        AutoClassifyRuleCreateRequest.Conditions cond = request.getConditions();

        AutoClassifyRule rule = ruleRepository.save(AutoClassifyRule.builder()
                .companyId(companyId)
                .empId(empId)
                .sourceBox(sourceBoxType)
                .ruleName(request.getRuleName())
                .titleContains(cond != null ? cond.getTitleContains() : null)
                .formName(cond != null ? cond.getFormName() : null)
                .drafterDept(cond != null ? cond.getDrafterDept() : null)
                .drafterName(cond != null ? cond.getDrafterName() : null)
                .targetFolderId(request.getTargetFolderId())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .sortOrder(maxSortOrder + 1)
                .build());

        return AutoClassifyRuleResponse.from(rule, targetFolder.getFolderName());
    }

    /**
     * 규칙 수정
     * - 대상 개인 문서함 변경 시 본인 소유 검증
     * - sourceBox 변경 가능
     */
    @Transactional
    public AutoClassifyRuleResponse update(UUID companyId, Long empId, Long ruleId, AutoClassifyRuleUpdateRequest request) {
        AutoClassifyRule rule = findRule(companyId, empId, ruleId);

        /* 소스 문서함 타입 파싱 */
        SourceBoxType sourceBoxType = SourceBoxType.valueOf(request.getSourceBox());

        /* 대상 개인 문서함 검증 (본인 소유 확인) */
        PersonalApprovalFolder targetFolder = personalFolderRepository
                .findByPersonalFolderIdAndCompanyIdAndEmpId(request.getTargetFolderId(), companyId, empId)
                .orElseThrow(() -> new BusinessException("대상 개인 문서함을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        AutoClassifyRuleCreateRequest.Conditions cond = request.getConditions();

        rule.update(
                request.getRuleName(),
                sourceBoxType,
                cond != null ? cond.getTitleContains() : null,
                cond != null ? cond.getFormName() : null,
                cond != null ? cond.getDrafterDept() : null,
                cond != null ? cond.getDrafterName() : null,
                request.getTargetFolderId(),
                request.getIsActive() != null ? request.getIsActive() : rule.getIsActive()
        );

        return AutoClassifyRuleResponse.from(rule, targetFolder.getFolderName());
    }

    /**
     * 규칙 삭제
     * - 본인 규칙만 삭제 가능 (empId 격리)
     */
    @Transactional
    public void delete(UUID companyId, Long empId, Long ruleId) {
        AutoClassifyRule rule = findRule(companyId, empId, ruleId);
        ruleRepository.delete(rule);
    }

    /**
     * 활성/비활성 토글
     * - 본인 규칙만 토글 가능 (empId 격리)
     */
    @Transactional
    public void toggle(UUID companyId, Long empId, Long ruleId) {
        AutoClassifyRule rule = findRule(companyId, empId, ruleId);
        rule.toggleActive();
    }

    /**
     * 규칙 순서 변경
     * - 프론트에서 전달한 순서 목록으로 일괄 업데이트
     */
    @Transactional
    public void reorder(UUID companyId, Long empId, AutoClassifyRuleReorderRequest request) {
        for (AutoClassifyRuleReorderRequest.ReorderItem item : request.getOrderList()) {
            AutoClassifyRule rule = findRule(companyId, empId, item.getId());
            rule.updateSortOrder(item.getSortOrder());
        }
    }

    /**
     * 공통: 규칙 조회 (회사 + 사원 격리)
     * - 본인 규칙만 접근 가능하도록 empId로 격리
     */
    private AutoClassifyRule findRule(UUID companyId, Long empId, Long ruleId) {
        return ruleRepository.findByRuleIdAndCompanyIdAndEmpId(ruleId, companyId, empId)
                .orElseThrow(() -> new BusinessException("자동분류 규칙을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }
}
