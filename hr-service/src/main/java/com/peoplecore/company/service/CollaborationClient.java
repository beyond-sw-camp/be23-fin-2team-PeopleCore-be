package com.peoplecore.company.service;

import com.peoplecore.company.config.CollaborationApiConfig;
import com.peoplecore.pay.approval.FormDetailResDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.UUID;

/* hr -> collabo 내부 통신 클라이언트 */
@Slf4j
@Component
public class CollaborationClient {

    private final RestClient restClient;

    @Autowired
    public CollaborationClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("http://collaboration-service").build();
    }


    /* formCode로 해당 회사의 active 양식 Id 조회
     * hr-service가 근태 정정 모달 프리필 시 formId 가져옴 */
    public Long getFormIdByCode(UUID companyId, String formCode) {
        return restClient.get()
                .uri(uri -> uri.path("/approval/forms/by-code")
                        .queryParam("formCode", formCode)
                        .build())
                .header("X-User-Company", companyId.toString())
                .retrieve()
                .body(Long.class);
    }

    /* formId 로 양식 상세 조회 (편집용).
     * collab 내부에서 MinIO 최신본 HTML 을 채워 반환.
     * 전자결재 UI 의 "새 문서 작성" 과 동일 경로.*/
    public FormDetailResDto getFormDetailEditing(UUID companyId, Long formId) {
        return restClient.get()
                .uri("/approval/forms/{formId}/edit", formId)
                .header("X-User-Company", companyId.toString())
                .retrieve()
                .body(FormDetailResDto.class);
    }
}
