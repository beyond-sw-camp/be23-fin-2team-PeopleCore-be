package com.peoplecore.attendance.cache;

import com.peoplecore.company.service.CollaborationClient;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 회사별 근태정정 양식(formCode=ATTENDANCE_MODIFY) 의 formId 로컬 캐시.
 */
@Slf4j
@Component
public class ApprovalFormIdCache {

    /* 근태정정 양식 코드 상수 — collaboration-service seed 값과 동일해야 함 */
    public static final String FORM_CODE_ATTENDANCE_MODIFY = "ATTENDANCE_MODIFY";

    private final CollaborationClient collaborationClient;

    /* companyId → formId. 미스 시 REST 호출로 채움 */
    private final ConcurrentHashMap<UUID, Long> cache = new ConcurrentHashMap<>();

    @Autowired
    public ApprovalFormIdCache(CollaborationClient collaborationClient) {
        this.collaborationClient = collaborationClient;
    }

    /*
     * 회사의 ATTENDANCE_MODIFY 양식 formId 반환.
     * 캐시 miss 시 collaboration-service 에 REST 조회 후 저장.
     */
    public Long getAttendanceModifyFormId(UUID companyId) {
        return cache.computeIfAbsent(companyId, this::fetchFromCollab);
    }

    /* REST 호출로 formId 획득. 실패 시 CustomException 전파 — cache 에 null 저장 방지 */
    private Long fetchFromCollab(UUID companyId) {
        try {
            Long formId = collaborationClient.getFormIdByCode(companyId, FORM_CODE_ATTENDANCE_MODIFY);
            if (formId == null) {
                throw new CustomException(ErrorCode.ATTENDANCE_MODIFY_FORM_NOT_FOUND);
            }
            log.info("[ApprovalFormIdCache] miss → fetch - companyId={}, formId={}", companyId, formId);
            return formId;
        } catch (CustomException ce) {
            throw ce;
        } catch (Exception e) {
            log.error("[ApprovalFormIdCache] fetch 실패 - companyId={}, err={}", companyId, e.getMessage());
            throw new CustomException(ErrorCode.ATTENDANCE_MODIFY_FORM_NOT_FOUND);
        }
    }

    /**운영 중 양식 교체 발생 시 (예: isProtected 우회로 수정) 강제 무효화용 */
    public void invalidate(UUID companyId) {
        cache.remove(companyId);
    }
}
