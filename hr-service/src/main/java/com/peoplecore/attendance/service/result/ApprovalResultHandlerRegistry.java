package com.peoplecore.attendance.service.result;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/* 결재 결과 핸들러 룩업 - Spring 이 List<ApprovalResultHandler> 자동 주입.
 * collab 의 ApprovalFormHandlerRegistry 와 동일 패턴. */
@Component
public class ApprovalResultHandlerRegistry {

    private final List<ApprovalResultHandler> handlers;

    @Autowired
    public ApprovalResultHandlerRegistry(List<ApprovalResultHandler> handlers) {
        this.handlers = handlers;
    }

    /* supports 첫 매칭 핸들러 반환 - 매칭 없으면 empty (알 수 없는 status) */
    public Optional<ApprovalResultHandler> find(String status) {
        return handlers.stream().filter(h -> h.supports(status)).findFirst();
    }
}
