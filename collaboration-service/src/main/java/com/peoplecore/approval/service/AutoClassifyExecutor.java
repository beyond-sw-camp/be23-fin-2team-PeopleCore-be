package com.peoplecore.approval.service;

import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.AutoClassifyRule;
import com.peoplecore.approval.entity.SourceBoxType;
import com.peoplecore.approval.repository.AutoClassifyRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 자동분류 규칙 실행기
 * - 문서 상신 시 기안자(SENT) 규칙 매칭
 * - 결재 수신 시 결재자(INBOX) 규칙 매칭
 * - 우선순위(sortOrder) 순으로 첫 매칭 규칙 적용
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoClassifyExecutor {

    private final AutoClassifyRuleRepository ruleRepository;

    /**
     * 문서에 자동분류 규칙을 적용하여 개인 폴더에 배정
     * @param sourceBox SENT(기안자) 또는 INBOX(결재자)
     * @param empId 규칙 소유자 (기안자 또는 결재자)
     * @param document 대상 문서
     */
    public void classify(UUID companyId, Long empId, SourceBoxType sourceBox, ApprovalDocument document) {
        List<AutoClassifyRule> rules = ruleRepository
                .findByCompanyIdAndEmpIdAndIsActiveTrueOrderBySortOrder(companyId, empId);

        for (AutoClassifyRule rule : rules) {
            if (rule.getSourceBox() != sourceBox) continue;
            if (matches(rule, document)) {
                document.assignPersonalFolder(rule.getTargetFolderId());
                log.info("자동분류 적용: docId={}, rule={}, folderId={}",
                        document.getDocId(), rule.getRuleName(), rule.getTargetFolderId());
                return;
            }
        }
    }

    /**
     * 규칙 조건 매칭 (모든 조건이 AND, null 조건은 무시)
     */
    private boolean matches(AutoClassifyRule rule, ApprovalDocument document) {
        if (rule.getTitleContains() != null && !rule.getTitleContains().isBlank()) {
            if (document.getDocTitle() == null || !document.getDocTitle().contains(rule.getTitleContains())) {
                return false;
            }
        }
        if (rule.getFormName() != null && !rule.getFormName().isBlank()) {
            if (document.getFormId() == null || !rule.getFormName().equals(document.getFormId().getFormName())) {
                return false;
            }
        }
        if (rule.getDrafterDept() != null && !rule.getDrafterDept().isBlank()) {
            if (document.getEmpDeptName() == null || !document.getEmpDeptName().equals(rule.getDrafterDept())) {
                return false;
            }
        }
        if (rule.getDrafterName() != null && !rule.getDrafterName().isBlank()) {
            if (document.getEmpName() == null || !document.getEmpName().equals(rule.getDrafterName())) {
                return false;
            }
        }
        return true;
    }
}
