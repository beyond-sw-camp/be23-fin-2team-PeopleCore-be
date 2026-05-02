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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * collaboration-service 의 캘린더 API 를 호출하는 사내 클라이언트.
 * Copilot tool 실행 시 LLM 이 추출한 input(title/startAt/endAt 등) 으로 일정을 생성한다.
 *
 * myCalendarsId 는 사용자가 모르는 내부 PK 라 LLM 에게 노출하지 않는다 — 첫 호출 시
 * GET /calendar/my 로 사용자의 캘린더 목록을 받아 default==true 인 것을 우선,
 * 없으면 첫 번째를 자동 선택. 향후 "어느 캘린더에 추가할지" 묻는 follow-up 으로 확장 가능.
 */
@Slf4j
@Component
public class CalendarClient {

    private static final String BASE = "http://collaboration-service";

    private final RestTemplate restTemplate;

    public CalendarClient(@Qualifier("internalRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 일정 생성. 성공 시 EventResDto Map(eventsId/title/...) 을, 실패 시 error 키가 들어있는 Map 을 반환.
     * tool_result 로 LLM 에 다시 들어가므로 컴팩트한 정보만 담는다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createEvent(UUID companyId, Long empId, Map<String, Object> input) {
        String title = stringOf(input, "title");
        String startAt = stringOf(input, "startAt");
        String endAt = stringOf(input, "endAt");

        if (title == null || title.isBlank()) return error("title is required");
        if (startAt == null || startAt.isBlank()) return error("startAt is required");
        if (endAt == null || endAt.isBlank()) return error("endAt is required");

        // 1) 사용자 캘린더 자동 해결
        Long myCalendarsId;
        try {
            myCalendarsId = resolveDefaultCalendarId(companyId, empId);
        } catch (Exception e) {
            log.error("resolveDefaultCalendarId failed: company={}, emp={}, err={}", companyId, empId, e.getMessage());
            return error("사용자 캘린더 조회 실패: " + e.getMessage());
        }
        if (myCalendarsId == null) {
            return error("사용자에게 등록된 캘린더가 없습니다. 캘린더 페이지에서 '내 캘린더'를 먼저 추가해주세요.");
        }

        // 2) POST 본문 — LLM input 의 안전한 키만 통과
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("startAt", startAt);
        body.put("endAt", endAt);
        body.put("myCalendarsId", myCalendarsId);
        if (input.get("description") instanceof String s && !s.isBlank()) body.put("description", s);
        if (input.get("location") instanceof String s && !s.isBlank()) body.put("location", s);
        if (input.get("isAllDay") instanceof Boolean b) body.put("isAllDay", b);
        if (input.get("isPublic") instanceof Boolean b) body.put("isPublic", b);

        try {
            HttpHeaders headers = headers(companyId, empId);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    BASE + "/calendar/events",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            Map<String, Object> created = resp.getBody();
            if (created == null) return error("일정 생성 응답이 비어있습니다.");

            // LLM/Citation 으로 돌릴 컴팩트 형태 — 민감 필드 없음
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("eventsId", created.get("eventsId"));
            out.put("title", created.get("title"));
            out.put("startAt", created.get("startAt"));
            out.put("endAt", created.get("endAt"));
            return out;
        } catch (RestClientResponseException e) {
            log.warn("createEvent http error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return error("일정 생성 실패(" + e.getStatusCode().value() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("createEvent failed", e);
            return error("일정 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 본인 + 관심 캘린더의 오늘 일정 조회 (다이제스트용).
     * GET /calendar/events?start=YYYY-MM-DDT00:00:00&end=YYYY-MM-DDT00:00:00
     * date 미지정 시 오늘. LLM 친화 컴팩트 형태(eventsId/title/startAt/endAt/location)로 변환.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMyEventsForDate(UUID companyId, Long empId, LocalDate date) {
        LocalDate d = date == null ? LocalDate.now() : date;
        String start = d + "T00:00:00";
        String end = d.plusDays(1) + "T00:00:00";
        try {
            HttpHeaders headers = headers(companyId, empId);
            String url = BASE + "/calendar/events?start=" + start + "&end=" + end;
            ResponseEntity<List> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            List<Map<String, Object>> raw = resp.getBody() == null
                    ? List.of() : (List<Map<String, Object>>) resp.getBody();

            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> e : raw) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("eventsId", e.get("eventsId"));
                one.put("title", e.get("title"));
                one.put("startAt", e.get("startAt"));
                one.put("endAt", e.get("endAt"));
                one.put("location", e.get("location"));
                one.put("isAllDay", e.get("isAllDay"));
                items.add(one);
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("date", d.toString());
            out.put("count", items.size());
            out.put("items", items);
            return out;
        } catch (RestClientResponseException e) {
            log.warn("getMyEventsForDate http error: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return error("일정 조회 실패(" + e.getStatusCode().value() + ")");
        } catch (Exception e) {
            log.error("getMyEventsForDate failed", e);
            return error("일정 조회 실패: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Long resolveDefaultCalendarId(UUID companyId, Long empId) {
        HttpHeaders headers = headers(companyId, empId);
        ResponseEntity<List> resp = restTemplate.exchange(
                BASE + "/calendar/my",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                List.class
        );
        List<Map<String, Object>> calendars = resp.getBody();
        if (calendars == null || calendars.isEmpty()) return null;

        // isDefault==true 우선, 없으면 첫 번째
        for (Map<String, Object> c : calendars) {
            if (Boolean.TRUE.equals(c.get("isDefault"))) {
                Object id = c.get("myCalendarsId");
                if (id instanceof Number n) return n.longValue();
            }
        }
        Object id = calendars.get(0).get("myCalendarsId");
        return id instanceof Number n ? n.longValue() : null;
    }

    private HttpHeaders headers(UUID companyId, Long empId) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-User-Company", companyId.toString());
        h.set("X-User-Id", String.valueOf(empId));
        h.set("Content-Type", "application/json");
        return h;
    }

    private String stringOf(Map<String, Object> input, String key) {
        Object v = input == null ? null : input.get(key);
        return v == null ? null : v.toString();
    }

    private Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", msg);
        return m;
    }
}
