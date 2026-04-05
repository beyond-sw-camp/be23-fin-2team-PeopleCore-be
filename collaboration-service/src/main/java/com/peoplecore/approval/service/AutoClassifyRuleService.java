package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.AutoClassifyRuleCreateRequest;
import com.peoplecore.approval.dto.AutoClassifyRuleReorderRequest;
import com.peoplecore.approval.dto.AutoClassifyRuleResponse;
import com.peoplecore.approval.dto.AutoClassifyRuleUpdateRequest;
import com.peoplecore.approval.entity.AutoClassifyRule;
import com.peoplecore.approval.entity.DeptApprovalFolder;
import com.peoplecore.approval.repository.AutoClassifyRuleRepository;
import com.peoplecore.approval.repository.DeptApprovalFolderRepository;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@Transactional(readOnly = true)
public class AutoClassifyRuleService {

    private final AutoClassifyRuleRepository ruleRepository;
    private final DeptApprovalFolderRepository folderRepository;

    @Autowired
    public AutoClassifyRuleService(AutoClassifyRuleRepository ruleRepository,
                                   DeptApprovalFolderRepository folderRepository) {
        this.ruleRepository = ruleRepository;
        this.folderRepository = folderRepository;
    }

    /**  규칙 목록 조회 */
    public List<AutoClassifyRuleResponse> getList(UUID companyId, Long deptId) {
        return ruleRepository.findByCompanyIdAndDeptIdOrderBySortOrder(companyId, deptId).stream()
                .map(rule -> {
                    String folderName = folderRepository.findById(rule.getTargetFolderId())
                            .map(DeptApprovalFolder::getFolderName)
                            .orElse(null);
                    return AutoClassifyRuleResponse.from(rule, folderName);
                })
                .toList();
    }

    /**  규칙 생성 */
    @Transactional
    public AutoClassifyRuleResponse create(UUID companyId, Long deptId, AutoClassifyRuleCreateRequest request) {
        DeptApprovalFolder targetFolder = folderRepository.findByDeptAppFolderIdAndCompanyId(request.getTargetFolderId(), companyId)
                .orElseThrow(() -> new BusinessException("대상 문서함을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        Integer maxSortOrder = ruleRepository.findMaxSortOrder(companyId, deptId);

        AutoClassifyRuleCreateRequest.Conditions cond = request.getConditions();

        AutoClassifyRule rule = ruleRepository.save(AutoClassifyRule.builder()
                .companyId(companyId)
                .deptId(deptId)
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

    /**규칙 수정 */
    @Transactional
    public AutoClassifyRuleResponse update(UUID companyId, Long ruleId, AutoClassifyRuleUpdateRequest request) {
        AutoClassifyRule rule = findRule(companyId, ruleId);

        DeptApprovalFolder targetFolder = folderRepository.findByDeptAppFolderIdAndCompanyId(request.getTargetFolderId(), companyId)
                .orElseThrow(() -> new BusinessException("대상 문서함을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        AutoClassifyRuleCreateRequest.Conditions cond = request.getConditions();

        rule.update(
                request.getRuleName(),
                cond != null ? cond.getTitleContains() : null,
                cond != null ? cond.getFormName() : null,
                cond != null ? cond.getDrafterDept() : null,
                cond != null ? cond.getDrafterName() : null,
                request.getTargetFolderId(),
                request.getIsActive() != null ? request.getIsActive() : rule.getIsActive()
        );

        return AutoClassifyRuleResponse.from(rule, targetFolder.getFolderName());
    }

    /*규칙 삭제 */
    @Transactional
    public void delete(UUID companyId, Long ruleId) {
        AutoClassifyRule rule = findRule(companyId, ruleId);
        ruleRepository.delete(rule);
    }

    /*활성/비활성 토글 */
    @Transactional
    public void toggle(UUID companyId, Long ruleId) {
        AutoClassifyRule rule = findRule(companyId, ruleId);
        rule.toggleActive();
    }

    /* 규칙 순서 변경 */
    @Transactional
    public void reorder(UUID companyId, AutoClassifyRuleReorderRequest request) {
        for (AutoClassifyRuleReorderRequest.ReorderItem item : request.getOrderList()) {
            AutoClassifyRule rule = findRule(companyId, item.getId());
            rule.updateSortOrder(item.getSortOrder());
        }
    }

    /**공통: 규칙 조회 (회사 격리) */
    private AutoClassifyRule findRule(UUID companyId, Long ruleId) {
        return ruleRepository.findByRuleIdAndCompanyId(ruleId, companyId)
                .orElseThrow(() -> new BusinessException("자동분류 규칙을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }
}
