package com.peoplecore.llm;

import com.peoplecore.dto.CopilotRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Copilot 발화·페이지 컨텍스트가 "민감 정보 접근" 으로 분류되는지 판정.
 * <p>
 * 민감으로 판정되면 CopilotService 는 외부 LLM(Anthropic Claude) 대신
 * 로컬 sLLM(EXAONE via Ollama) 으로 라우팅한다 — 컴플라이언스 요건.
 * <p>
 * 판정 규칙(다층 방어):
 * <ol>
 *   <li>페이지 컨텍스트 게이트 — 민감 화면(/hr/payroll, /hr/evaluation 등)에서는 무조건 sLLM</li>
 *   <li>주민번호 정규식 — 사용자가 발화에 RRN 직접 입력</li>
 *   <li>민감 키워드 매칭 — 급여/연봉/평가/주소/계좌/연락처 등</li>
 * </ol>
 * 분류기 LLM 호출은 의도적으로 사용하지 않음 — 룰만으로 99% 케이스 커버, 분류 호출 자체가 가장 비싼 단계.
 */
@Slf4j
@Component
public class SensitiveDetector {

    /**
     * 민감 키워드 사전. 한국 PIPA 관점의 통상 PII + HR 도메인 민감 카테고리.
     * 정규화된 발화(공백 제거)에서 substring 매칭하므로 어미·조사 영향 받지 않음.
     */
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            // 급여
            "급여", "연봉", "월급", "실수령", "급여명세", "급여명세서", "보너스", "성과급",
            // 인사평가
            "평가", "고과", "인사평가", "역량평가", "성과평가",
            // 주민번호·개인 식별
            "주민번호", "주민등록번호", "주민등록",
            // 주소
            "주소", "거주지", "거주",
            // 계좌
            "계좌", "계좌번호", "은행계좌", "급여계좌",
            // 연락처·이메일
            "휴대폰번호", "휴대전화", "전화번호", "개인이메일", "사적이메일",
            // 생년월일·성별
            "생년월일", "생일", "성별",
            // 기타 민감 (의료·징계·휴직사유)
            "징계", "병가사유", "휴직사유", "의료기록", "건강검진", "면접평가",
            // 본인 종합 조회 (overview) — 다이제스트는 급여·평가·연차를 묶으므로 통째로 sLLM 라우팅.
            // "내 정보", "내 현황", "내 인사정보" 등은 공백 제거 후 contains 매칭됨.
            "내정보", "내인사", "인사정보", "인사현황", "내현황"
    );

    /**
     * 주민등록번호 패턴 — "901234-1234567" / "9012341234567" 양쪽 매칭.
     * 첫 6자리는 생년월일이라 \\d{6} 으로 충분, 7번째 자리는 1~4(내국인)/5~8(외국인)/9~0(특수) 모두 허용.
     */
    private static final Pattern RRN_PATTERN = Pattern.compile("\\b\\d{6}-?\\d{7}\\b");

    /**
     * 민감 화면 prefix 목록. FE 의 실제 라우트(App.tsx, *Layout.tsx) 와 1:1 매핑.
     * startsWith 비교라서 하위 경로도 자동 포함. 신규 민감 화면 추가 시 같이 갱신.
     */
    private static final List<String> SENSITIVE_ROUTE_PREFIXES = List.of(
            "/salary",                  // 개인 급여명세 (App.tsx)
            "/payroll",                 // 급여대장·보험·퇴직금·연차수당·연금 (PayrollLayout)
            "/eval",                    // 인사평가 (목표·결과·이의신청·매니저평가) (EvalLayout)
            "/hr/salary-contract",      // 연봉계약 (HRLayout)
            "/hr/employee/",            // 직원 상세·편집 (PII 포함, HRLayout)
            "/hr/retirement",           // 퇴직 관리 (HRLayout)
            "/hr/appointment",          // 인사발령 (HRLayout)
            "/hr-admin"                 // HR 관리자 영역 (App.tsx)
    );

    public Verdict classify(String utterance, CopilotRequest.PageContext pageContext) {
        if (matchesSensitiveRoute(pageContext)) {
            return new Verdict(true, Reason.ROUTE, pageContext == null ? null : pageContext.getRoute());
        }
        if (utterance == null || utterance.isBlank()) return Verdict.SAFE;
        if (RRN_PATTERN.matcher(utterance).find()) {
            return new Verdict(true, Reason.RRN, "주민번호 패턴 감지");
        }
        String matched = matchKeyword(utterance);
        if (matched != null) {
            return new Verdict(true, Reason.KEYWORD, matched);
        }
        return Verdict.SAFE;
    }

    private boolean matchesSensitiveRoute(CopilotRequest.PageContext pc) {
        if (pc == null || pc.getRoute() == null) return false;
        String route = pc.getRoute();
        for (String prefix : SENSITIVE_ROUTE_PREFIXES) {
            if (route.startsWith(prefix)) return true;
        }
        return false;
    }

    private String matchKeyword(String utterance) {
        // 공백·어미 영향 최소화 위해 소문자화·공백제거 후 contains
        String normalized = utterance.toLowerCase().replaceAll("\\s+", "");
        for (String kw : SENSITIVE_KEYWORDS) {
            if (normalized.contains(kw)) return kw;
        }
        return null;
    }

    /** 분기 결정 + 사유. 로깅·감사용 (민감 발화 자체는 로그에 남기지 않음 — 사유만). */
    public record Verdict(boolean sensitive, Reason reason, String detail) {
        public static final Verdict SAFE = new Verdict(false, Reason.NONE, null);
    }

    public enum Reason { NONE, ROUTE, RRN, KEYWORD }
}
