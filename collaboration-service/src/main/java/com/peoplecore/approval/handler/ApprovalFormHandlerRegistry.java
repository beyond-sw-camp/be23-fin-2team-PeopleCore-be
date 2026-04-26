package com.peoplecore.approval.handler;

import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/* 폼 핸들러 룩업 — Spring 이 List<ApprovalFormHandler> 자동 주입 */
@Component
public class ApprovalFormHandlerRegistry {

    private final List<ApprovalFormHandler> handlers;

    @Autowired
    public ApprovalFormHandlerRegistry(List<ApprovalFormHandler> handlers) {
        this.handlers = handlers;
    }

    /* supports 첫 매칭 핸들러 반환 — 매칭 없으면 empty */
    public Optional<ApprovalFormHandler> find(ApprovalDocument document) {
        return handlers.stream().filter(h -> h.supports(document)).findFirst();
    }

    /* 문서 저장 전 단계용 — ApprovalForm 만으로 핸들러 룩업 (멱등키 검사 등) */
    public Optional<ApprovalFormHandler> findByForm(ApprovalForm form) {
        ApprovalDocument probe = ApprovalDocument.builder().formId(form).build();
        return find(probe);
    }
}
