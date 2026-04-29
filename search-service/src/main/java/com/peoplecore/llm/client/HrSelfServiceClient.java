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
 * hr-service 의 본인 정보·급여 endpoint 를 호출하는 사내 클라이언트.
 * Copilot 의 EXAONE 경로(민감 라우팅) 도구 실행 시 사용된다.
 * <p>
 * 보안 원칙:
 * <ul>
 *   <li>empId 는 LLM input 무시 — 항상 인증 컨텍스트의 본인 empId 강제 주입</li>
 *   <li>응답에 주민번호가 들어오면 앞 6자리만 노출하도록 마스킹</li>
 *   <li>도구 결과는 EXAONE(local) 으로만 흐름 — Anthropic 으로 절대 송출되지 않음 (CopilotService 분기 보장)</li>
 * </ul>
 */
@Slf4j
@Component
public class HrSelfServiceClient {

    private static final String BASE = "http://hr-service";

    private final RestTemplate restTemplate;

    public HrSelfServiceClient(@Qualifier("internalRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 본인 기본 정보 (휴대폰·이메일·생년월일·주소·주민번호) 조회.
     * GET /employee/{empId} 호출 후 주민번호는 마스킹해서 반환.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMyPersonalInfo(UUID companyId, Long empId, String role) {
        try {
            HttpHeaders headers = headers(companyId, empId, role);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    BASE + "/employee/" + empId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );
            Map<String, Object> body = resp.getBody();
            if (body == null) return error("응답이 비어있습니다.");

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("empName", body.get("empName"));
            out.put("empPhone", body.get("empPhone"));
            out.put("empPersonalEmail", body.get("empPersonalEmail"));
            out.put("empBirthDate", body.get("empBirthDate"));
            out.put("empGender", body.get("empGender"));
            out.put("empZipCode", body.get("empZipCode"));
            out.put("empAddressBase", body.get("empAddressBase"));
            out.put("empAddressDetail", body.get("empAddressDetail"));
            out.put("empResidentNumberMasked", maskRrn((String) body.get("empResidentNumber")));
            return out;
        } catch (RestClientResponseException e) {
            log.warn("getMyPersonalInfo http error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return error("개인정보 조회 실패(" + e.getStatusCode().value() + ")");
        } catch (Exception e) {
            log.error("getMyPersonalInfo failed", e);
            return error("개인정보 조회 실패: " + e.getMessage());
        }
    }

    /**
     * 본인 기본 급여 정보(연봉·월급·고정수당) 조회. GET /pay/my/info 호출.
     * <p>
     * stubs 대신 info 를 쓰는 이유 — info 는 직원 등록 시 자동 생성되는 base 급여 데이터이고,
     * stubs 는 월별 명세서 배치가 돌아야 채워져 신규/테스트 환경에서 자주 빈 채로 옴.
     * 사용자가 "내 급여 알려줘" 라고 할 때 일반적으로 기대하는 답(연봉·월급) 도 info 쪽이 더 직접적.
     * <p>
     * 보안: 계좌·퇴직연금·부양가족 같은 또다른 민감 카테고리는 응답에서 제거하고, 급여 숫자와
     * 고정수당 항목만 노출. 추후 별도 도구로 분리하는 게 권한·감사 측면에서도 깔끔.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMySalarySummary(UUID companyId, Long empId, String role) {
        try {
            HttpHeaders headers = headers(companyId, empId, role);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    BASE + "/pay/my/info",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );
            Map<String, Object> body = resp.getBody();
            if (body == null) return error("급여 정보 응답이 비어있습니다.");

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("empName", body.get("empName"));
            out.put("deptName", body.get("deptName"));
            out.put("gradeName", body.get("gradeName"));
            out.put("titleName", body.get("titleName"));
            out.put("empHireDate", body.get("empHireDate"));

            Object salaryInfo = body.get("salaryInfo");
            if (salaryInfo instanceof Map<?, ?> si) {
                Map<String, Object> sOut = new LinkedHashMap<>();
                sOut.put("annualSalary", si.get("annualSalary"));
                sOut.put("monthlySalary", si.get("monthlySalary"));

                List<Map<String, Object>> allowancesOut = new ArrayList<>();
                Object fa = si.get("fixedAllowances");
                if (fa instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> a) {
                            Map<String, Object> one = new LinkedHashMap<>();
                            one.put("payItemName", a.get("payItemName"));
                            one.put("amount", a.get("amount"));
                            allowancesOut.add(one);
                        }
                    }
                }
                sOut.put("fixedAllowances", allowancesOut);
                out.put("salaryInfo", sOut);
            }
            return out;
        } catch (RestClientResponseException e) {
            log.warn("getMySalarySummary http error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return error("급여 정보 조회 실패(" + e.getStatusCode().value() + ")");
        } catch (Exception e) {
            log.error("getMySalarySummary failed", e);
            return error("급여 정보 조회 실패: " + e.getMessage());
        }
    }

    /**
     * 본인 휴가 잔액 요약. GET /vacation/balances/me/status?year=YYYY.
     * 다이제스트용으로 핵심 카드(annual)만 추리고 others/upcoming/past 는 제외 — overview 응답이 너무 커지지 않게.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMyVacationStatus(UUID companyId, Long empId, String role, Integer year) {
        int resolvedYear = year == null ? LocalDate.now().getYear() : year;
        try {
            HttpHeaders headers = headers(companyId, empId, role);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    BASE + "/vacation/balances/me/status?year=" + resolvedYear,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );
            Map<String, Object> body = resp.getBody();
            if (body == null) return error("휴가 잔액 응답이 비어있습니다.");

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("year", resolvedYear);
            // annual: 메인 연차 카드. 핵심 숫자만 노출.
            Object annual = body.get("annual");
            if (annual instanceof Map<?, ?> a) {
                Map<String, Object> aOut = new LinkedHashMap<>();
                aOut.put("typeName", a.get("typeName"));
                aOut.put("totalDays", a.get("totalDays"));
                aOut.put("usedDays", a.get("usedDays"));
                aOut.put("pendingDays", a.get("pendingDays"));
                aOut.put("availableDays", a.get("availableDays"));
                out.put("annual", aOut);
            }
            return out;
        } catch (RestClientResponseException e) {
            log.warn("getMyVacationStatus http error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return error("휴가 잔액 조회 실패(" + e.getStatusCode().value() + ")");
        } catch (Exception e) {
            log.error("getMyVacationStatus failed", e);
            return error("휴가 잔액 조회 실패: " + e.getMessage());
        }
    }

    /**
     * 본인 이번 주 근태 요약. GET /attendance/my/weekly-summary?date=YYYY-MM-DD.
     * weekly 카드만 추림 (today 와 workGroup 은 다이제스트엔 과함).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMyAttendanceWeeklySummary(UUID companyId, Long empId, String role, LocalDate date) {
        LocalDate d = date == null ? LocalDate.now() : date;
        try {
            HttpHeaders headers = headers(companyId, empId, role);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    BASE + "/attendance/my/weekly-summary?date=" + d,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );
            Map<String, Object> body = resp.getBody();
            if (body == null) return error("근태 요약 응답이 비어있습니다.");

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            // today 는 가볍게: checkIn / checkOut 시간만
            Object today = body.get("today");
            if (today instanceof Map<?, ?> t) {
                Map<String, Object> tOut = new LinkedHashMap<>();
                tOut.put("checkIn", t.get("checkIn"));
                tOut.put("checkOut", t.get("checkOut"));
                out.put("today", tOut);
            }
            // weekly 카드 핵심
            Object weekly = body.get("weekly");
            if (weekly instanceof Map<?, ?> w) {
                Map<String, Object> wOut = new LinkedHashMap<>();
                wOut.put("weekStart", w.get("weekStart"));
                wOut.put("weekEnd", w.get("weekEnd"));
                wOut.put("workedMinutes", w.get("workedMinutes"));
                wOut.put("attendedDays", w.get("attendedDays"));
                wOut.put("workDays", w.get("workDays"));
                wOut.put("approvedOvertimeMinutes", w.get("approvedOvertimeMinutes"));
                wOut.put("abnormalDays", w.get("abnormalDays"));
                out.put("weekly", wOut);
            }
            return out;
        } catch (RestClientResponseException e) {
            log.warn("getMyAttendanceWeeklySummary http error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return error("근태 요약 조회 실패(" + e.getStatusCode().value() + ")");
        } catch (Exception e) {
            log.error("getMyAttendanceWeeklySummary failed", e);
            return error("근태 요약 조회 실패: " + e.getMessage());
        }
    }

    /**
     * D 다이제스트 — 한 도구 호출에 본인 인사 정보·급여·잔여 연차·이번 주 근태를 한꺼번에 묶어 반환.
     * BE 측 composite 으로 처리해 EXAONE 의 manual prompting 안정성을 해치지 않음.
     * 4 endpoint 를 순차 호출 — 각 700ms 가정 × 4 = ~3초 이내. 일부 실패는 부분 결과로 반환(ok 별도).
     */
    public Map<String, Object> getMyOverview(UUID companyId, Long empId, String role) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("personal", getMyPersonalInfo(companyId, empId, role));
        out.put("salary", getMySalarySummary(companyId, empId, role));
        out.put("vacation", getMyVacationStatus(companyId, empId, role, null));
        out.put("attendance", getMyAttendanceWeeklySummary(companyId, empId, role, null));
        return out;
    }

    /**
     * 본인 인사평가 다이제스트.
     * 시즌 목록 → 최신 시즌(startDate desc) 자동 선택 → 결과·목표·자기평가 4 endpoint 묶음.
     * <p>
     * 설계 결정:
     * - LLM 이 seasonId 를 알 수 없으므로 args 인자로 받지 않고 서버가 최신 시즌으로 고정 (v1).
     * - /eval/goals 와 /eval/self-evaluations 는 현재 OPEN 시즌 기준이라 과거 시즌 결과를 볼 땐
     *   다소 어긋날 수 있으나, "내 평가" 발화는 보통 진행 중·최근 시즌을 의미하므로 트레이드오프 OK.
     * - 시즌 자체가 없으면 ok:true + empty 표시(에러 아님) — "아직 평가 참여 이력 없음" 안내용.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMyEvaluation(UUID companyId, Long empId, String role) {
        try {
            HttpHeaders headers = headers(companyId, empId, role);

            // 1) 본인 시즌 목록
            ResponseEntity<List> seasonsResp = restTemplate.exchange(
                    BASE + "/eval/grades/my/seasons",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    List.class
            );
            List<Map<String, Object>> seasons = seasonsResp.getBody() == null
                    ? List.of() : (List<Map<String, Object>>) seasonsResp.getBody();
            if (seasons.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("ok", true);
                empty.put("hasSeason", false);
                empty.put("message", "참여한 평가 시즌이 없습니다.");
                return empty;
            }

            // startDate desc (null safe) → 최신 시즌 한 건 선택
            Map<String, Object> selected = seasons.stream()
                    .max((a, b) -> compareDateString(
                            (String) a.get("startDate"), (String) b.get("startDate")))
                    .orElse(seasons.get(0));
            Object seasonIdObj = selected.get("seasonId");
            if (!(seasonIdObj instanceof Number sn)) {
                return error("시즌 ID 파싱 실패");
            }
            long seasonId = sn.longValue();

            Map<String, Object> seasonOut = new LinkedHashMap<>();
            seasonOut.put("seasonId", seasonId);
            seasonOut.put("name", selected.get("name"));
            seasonOut.put("status", selected.get("status"));
            seasonOut.put("finalizedAt", selected.get("finalizedAt"));
            seasonOut.put("startDate", selected.get("startDate"));

            // 2) 평가 결과 (선택된 시즌 기준)
            Map<String, Object> resultOut = new LinkedHashMap<>();
            try {
                ResponseEntity<Map> rResp = restTemplate.exchange(
                        BASE + "/eval/grades/my/result?seasonId=" + seasonId,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        Map.class
                );
                Map<String, Object> r = rResp.getBody();
                if (r != null) {
                    resultOut.put("status", r.get("status"));
                    resultOut.put("finalizedAt", r.get("finalizedAt"));
                    resultOut.put("autoGrade", r.get("autoGrade"));
                    resultOut.put("managerGrade", r.get("managerGrade"));
                    resultOut.put("finalGrade", r.get("finalGrade"));
                    resultOut.put("feedback", r.get("feedback"));
                    resultOut.put("goals", r.get("goals")); // 목표별 평가 상세 그대로 통과
                }
            } catch (Exception e) {
                log.warn("getMyEvaluation: result fetch failed: {}", e.getMessage());
                resultOut.put("error", "평가 결과 조회 실패");
            }

            // 3) 본인 목표 (현재 OPEN 시즌)
            List<Map<String, Object>> goalsOut = new ArrayList<>();
            try {
                ResponseEntity<List> gResp = restTemplate.exchange(
                        BASE + "/eval/goals",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        List.class
                );
                List<Map<String, Object>> goals = gResp.getBody() == null
                        ? List.of() : (List<Map<String, Object>>) gResp.getBody();
                for (Map<String, Object> g : goals) {
                    Map<String, Object> one = new LinkedHashMap<>();
                    one.put("id", g.get("id"));
                    one.put("goalType", g.get("goalType"));
                    one.put("title", g.get("title"));
                    one.put("grade", g.get("grade"));
                    one.put("targetValue", g.get("targetValue"));
                    one.put("targetUnit", g.get("targetUnit"));
                    one.put("approval", g.get("approval"));
                    one.put("ratio", g.get("ratio"));
                    goalsOut.add(one);
                }
            } catch (Exception e) {
                log.warn("getMyEvaluation: goals fetch failed: {}", e.getMessage());
            }

            // 4) 자기평가 (현재 OPEN 시즌)
            List<Map<String, Object>> selfOut = new ArrayList<>();
            try {
                ResponseEntity<List> sResp = restTemplate.exchange(
                        BASE + "/eval/self-evaluations",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        List.class
                );
                List<Map<String, Object>> selfs = sResp.getBody() == null
                        ? List.of() : (List<Map<String, Object>>) sResp.getBody();
                for (Map<String, Object> s : selfs) {
                    Map<String, Object> one = new LinkedHashMap<>();
                    one.put("goalId", s.get("goalId"));
                    one.put("title", s.get("title"));
                    one.put("achievementLevel", s.get("achievementLevel"));
                    one.put("achievementDetail", s.get("achievementDetail"));
                    one.put("approval", s.get("approval"));
                    one.put("rejectReason", s.get("rejectReason"));
                    selfOut.add(one);
                }
            } catch (Exception e) {
                log.warn("getMyEvaluation: self-evaluations fetch failed: {}", e.getMessage());
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("hasSeason", true);
            out.put("season", seasonOut);
            out.put("result", resultOut);
            out.put("goals", goalsOut);
            out.put("selfEvaluations", selfOut);
            return out;
        } catch (RestClientResponseException e) {
            log.warn("getMyEvaluation http error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return error("인사평가 조회 실패(" + e.getStatusCode().value() + ")");
        } catch (Exception e) {
            log.error("getMyEvaluation failed", e);
            return error("인사평가 조회 실패: " + e.getMessage());
        }
    }

    /** ISO LocalDate 문자열 비교. null 은 가장 작은 값 취급 — 정렬에서 뒤로 밀림. */
    private int compareDateString(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }

    /**
     * 주민번호 마스킹 — "901234-1******" 형태. null/형식 미일치 시 null 반환.
     * 본인 데이터라도 LLM 응답 텍스트에 풀 RRN 이 들어가지 않게 함(컴플라이언스 + 프롬프트 인젝션 방어).
     */
    private String maskRrn(String rrn) {
        if (rrn == null) return null;
        String compact = rrn.replaceAll("[^0-9]", "");
        if (compact.length() < 7) return null;
        return compact.substring(0, 6) + "-" + compact.charAt(6) + "******";
    }

    private HttpHeaders headers(UUID companyId, Long empId, String role) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-User-Company", companyId.toString());
        h.set("X-User-Id", String.valueOf(empId));
        if (role != null && !role.isBlank()) h.set("X-User-Role", role);
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
