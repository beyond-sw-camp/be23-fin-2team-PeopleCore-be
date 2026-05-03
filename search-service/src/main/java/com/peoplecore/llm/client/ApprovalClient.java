package com.peoplecore.llm.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * collaboration-service 의 결재 API 를 호출하는 사내 클라이언트.
 * Copilot tool 실행 시 본인 결재 대기 목록을 다이제스트용으로 가져온다.
 */
@Slf4j
@Component
public class ApprovalClient {

    private static final String BASE = "http://collaboration-service";

    private final RestTemplate restTemplate;

    public ApprovalClient(@Qualifier("internalRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 본인이 결재자로 지정된 PENDING 결재 문서 top N 조회.
     * GET /approval/documents/waiting?size=N&page=0&sort=isEmergency,desc
     * 긴급 우선 정렬해 가장 시급한 건들이 위로 오도록 한다.
     * <p>
     * LLM 친화적 컴팩트 응답 — 핵심 필드(docId/docTitle/drafterName/createdAt/isEmergency)만 노출.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMyPendingApprovals(UUID companyId, Long empId, int size) {
        try {
            HttpHeaders headers = headers(companyId, empId);
            String url = BASE + "/approval/documents/waiting?size=" + size + "&page=0&sort=isEmergency,desc";
            ResponseEntity<Map> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) return error("결재 대기 응답이 비어있습니다.");

            // Page 응답: { content: [...], totalElements, ... }
            Object contentRaw = body.get("content");
            List<Map<String, Object>> items = new ArrayList<>();
            if (contentRaw instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> doc)) continue;
                    Map<String, Object> one = new LinkedHashMap<>();
                    one.put("docId", doc.get("docId"));
                    one.put("docNum", doc.get("docNum"));
                    one.put("docTitle", doc.get("docTitle"));
                    one.put("drafterName", doc.get("drafterName"));
                    one.put("drafterDept", doc.get("drafterDept"));
                    one.put("createdAt", doc.get("createdAt"));
                    one.put("isEmergency", doc.get("isEmergency"));
                    items.add(one);
                }
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("totalCount", body.get("totalElements"));
            out.put("items", items);
            return out;
        } catch (RestClientResponseException e) {
            log.warn("getMyPendingApprovals http error: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return error("결재 대기 조회 실패(" + e.getStatusCode().value() + ")");
        } catch (Exception e) {
            log.error("getMyPendingApprovals failed", e);
            return error("결재 대기 조회 실패: " + e.getMessage());
        }
    }

    private HttpHeaders headers(UUID companyId, Long empId) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-User-Company", companyId.toString());
        h.set("X-User-Id", String.valueOf(empId));
        h.set("Content-Type", "application/json");
        return h;
    }

    private Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", msg);
        return m;
    }
}
