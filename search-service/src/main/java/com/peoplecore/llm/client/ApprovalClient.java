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

    /**
     * formCode → 결재 양식 메타데이터(formId/formName/folderName/retentionYear) 해소.
     * <p>
     * Copilot 의 prefill_approval_form 흐름이 FE 에 OPEN_APPROVAL_FORM 액션을 보낼 때
     * formId 까지 미리 채워주기 위함. FE 가 추가로 /approval/form 전체 목록을 받아
     * formCode 로 매칭하는 단계를 건너뛰게 해 모달 진입을 즉시 보장한다.
     * <p>
     * 1차로 내부 API GET /approval/forms/by-code 로 formId 만 빠르게 얻고,
     * 2차로 GET /approval/forms/{formId} 로 formName/folderName/retentionYear 까지 채운다.
     * 둘 중 어디서든 실패하면 null 반환 — 호출자는 formCode-only 폴백으로 동작.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveFormByCode(UUID companyId, Long empId, String formCode) {
        if (formCode == null || formCode.isBlank()) return null;
        try {
            HttpHeaders headers = headers(companyId, empId);

            // 1) formCode → formId (collaboration-service 내부 API)
            String byCodeUrl = BASE + "/approval/forms/by-code?formCode=" + formCode;
            ResponseEntity<Long> idResp = restTemplate.exchange(
                    byCodeUrl, HttpMethod.GET, new HttpEntity<>(headers), Long.class);
            Long formId = idResp.getBody();
            if (formId == null) {
                log.info("resolveFormByCode: formCode={} not found", formCode);
                return null;
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("formId", formId);
            out.put("formCode", formCode);

            // 2) 양식 상세 — formName/folder/retention 채움. 실패해도 formId 만이라도 반환.
            try {
                String detailUrl = BASE + "/approval/forms/" + formId;
                ResponseEntity<Map> detailResp = restTemplate.exchange(
                        detailUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
                Map<String, Object> detail = detailResp.getBody();
                if (detail != null) {
                    if (detail.get("formName") != null) out.put("formName", detail.get("formName"));
                    if (detail.get("folderName") != null) out.put("folderName", detail.get("folderName"));
                    if (detail.get("formRetentionYear") != null) {
                        out.put("formRetentionYear", detail.get("formRetentionYear"));
                    }
                }
            } catch (Exception detailErr) {
                log.warn("resolveFormByCode: detail fetch failed formId={}, err={}", formId, detailErr.getMessage());
            }
            return out;
        } catch (RestClientResponseException e) {
            // 404 (양식 미등록) 는 정상 시나리오 — info 로 로깅하고 null 반환
            if (e.getStatusCode().value() == 404) {
                log.info("resolveFormByCode: formCode={} returns 404", formCode);
            } else {
                log.warn("resolveFormByCode http error: formCode={}, status={}, body={}",
                        formCode, e.getStatusCode(), e.getResponseBodyAsString());
            }
            return null;
        } catch (Exception e) {
            log.warn("resolveFormByCode failed: formCode={}, err={}", formCode, e.getMessage());
            return null;
        }
    }

    private HttpHeaders headers(UUID companyId, Long empId) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-User-Company", companyId.toString());
        if (empId != null) h.set("X-User-Id", String.valueOf(empId));
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
