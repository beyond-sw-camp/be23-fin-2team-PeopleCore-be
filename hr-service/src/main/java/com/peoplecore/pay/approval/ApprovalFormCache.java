package com.peoplecore.pay.approval;

import com.peoplecore.company.service.CollaborationClient;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/* 회사별 결의서 양식(PAYROLL_RESOLUTION / SEVERANCE_RESOLUTION)의
 * formId + formHtml 메모리 캐시.
 * - 캐시 키: (companyId, ApprovalFormType)
 * - miss 시 collaboration-service REST 호출
 * - 양식 개정 시 invalidate(companyId) 수동 호출로 무효화 */
@Slf4j
@Component
public class ApprovalFormCache {
    private final CollaborationClient collaborationClient;

    private final ConcurrentHashMap<UUID, Map<ApprovalFormType, CachedForm>> cache
            = new ConcurrentHashMap<>();

    @Autowired
    public ApprovalFormCache(CollaborationClient collaborationClient) {
        this.collaborationClient = collaborationClient;
    }

    public CachedForm get(UUID companyId, ApprovalFormType type) {
        return cache
                .computeIfAbsent(companyId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(type, t -> fetch(companyId, t));
    }

    private CachedForm fetch(UUID companyId, ApprovalFormType type) {
        try {
            // ① formCode → formId
            Long formId = collaborationClient.getFormIdByCode(companyId, type.getFormCode());
            if (formId == null) {
                throw new CustomException(ErrorCode.APPROVAL_TEMPLATE_NOT_FOUND);
            }
            // ② formId → FormDetailResponse (MinIO 최신 HTML 포함)
            FormDetailResDto detail = collaborationClient.getFormDetailEditing(companyId, formId);
            if (detail == null || detail.getFormHtml() == null) {
                throw new CustomException(ErrorCode.APPROVAL_TEMPLATE_NOT_FOUND);
            }
            log.info("[ApprovalFormCache] miss → fetch companyId={}, type={}, formId={}, version={}",
                    companyId, type, formId, detail.getFormVersion());
            return new CachedForm(formId, detail.getFormHtml(), detail.getFormVersion());
        } catch (CustomException ce) { throw ce; }
        catch (Exception e) {
            log.error("[ApprovalFormCache] fetch 실패 companyId={}, type={}, err={}",
                    companyId, type, e.getMessage());
            throw new CustomException(ErrorCode.APPROVAL_TEMPLATE_NOT_FOUND);
        }
    }



    public void invalidate(UUID companyId) {
        cache.remove(companyId);
    }

    public record CachedForm(Long formId, String formHtml, Integer formVersion) {}
}
