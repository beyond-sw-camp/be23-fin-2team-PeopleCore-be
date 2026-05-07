package com.peoplecore.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.dto.CopilotRequest;
import com.peoplecore.dto.CopilotResponse;
import com.peoplecore.dto.SearchResponse;
import com.peoplecore.dto.SearchResultItem;
import com.peoplecore.llm.client.CalendarClient;
import com.peoplecore.llm.client.HrSelfServiceClient;
import com.peoplecore.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copilot orchestrator. Anthropic Messages API를 tool_use 루프로 돌리며 search_documents 도구를
 * 호출자(=서버) 가 직접 실행해 결과를 회신한다. LLM은 도구 호출과 자연어 응답만 책임.
 *
 * 흐름: user message → Claude (tools 노출) → stop_reason
 *   - "end_turn"  → text 추출 후 종료
 *   - "tool_use"  → 도구 실행 → tool_result block 으로 회신 → 재호출
 *   - max iter   → 강제 종료, 마지막 텍스트만 반환 (폭주 방지)
 */
@Slf4j
@Service
public class CopilotService {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            당신은 PeopleCore 사내 검색·실행 코파일럿입니다. 사용자의 질문/요청에 답하기 위해
            제공된 도구를 적극 활용하세요. 오늘 날짜는 %s 입니다.

            도구 사용 원칙:
            1) 인물·부서·결재·일정 조회는 반드시 search_documents 를 먼저 호출합니다.
               추측이나 사전지식으로 대답하지 않습니다.
            2) 사용자가 일정/회의/약속을 "잡아줘", "등록해줘", "추가해줘" 라고 명시적으로 요청하면
               create_calendar_event 를 호출합니다. "알려줘", "확인해줘" 같은 조회는 도구로
               등록하지 말고 search_documents 로 검색합니다.
            3) 사용자가 결재 기안을 "신청해줘", "올려줘", "기안해줘" 라고 명시적으로 요청하면
               prefill_approval_form 을 호출합니다. 양식 코드는 발화 키워드로 매핑:
               - 휴가/연차/반차/병가 → VACATION_REQUEST
               - 초과근무/잔업/야근 → OVERTIME_REQUEST
               결재선에 보낼 사람 이름이 발화에 있으면 approverNames 배열에 그대로 넣습니다.
               이름 미명시 시 절대 임의로 채우지 마세요 — 사용자가 모달에서 직접 선택합니다.
               이 도구는 모달을 열기만 합니다 — 실제 상신은 사용자가 모달에서 직접 누릅니다.
            4) "내일", "다음 주 월요일", "오후 3시" 같은 상대 표현은 오늘 날짜 기준으로
               ISO 8601 LocalDateTime(YYYY-MM-DDTHH:mm:ss) 으로 변환해 startAt/endAt 에 넣습니다.
               시간 미지정 시 09:00~10:00 을 기본으로 가정하되, 답변에서 사용자에게 알려줍니다.
            5) 같은 검색 질문에 도구를 2회를 초과해 호출하지 않습니다. 결과가 비어 있으면
               keyword 를 한 번만 바꾸어 재시도하고, 그래도 없으면 솔직히 "검색 결과가 없다" 고 답합니다.
            6) 답변은 한국어, 3~6문장 이내로 간결하게 작성합니다. 일정/결재 등록 성공 시
               핵심 정보(제목·시간·결재선)를 명시해 사용자에게 확인시킵니다.
               결재 양식이 열렸을 때는 "잔여 일정/날짜는 모달에서 선택해주세요" 라고 안내합니다.
            7) 사용자 권한·회사 컨텍스트는 서버가 자동 적용합니다. 도구 input 에 회사/사번/캘린더 ID 를
               명시하지 마세요.
            8) 사용자가 "오늘 할 일", "오늘 다이제스트", "출근하면 뭐부터", "내 오늘 일정과 결재",
               "오늘 뭐 봐야" 같은 **종합 요약** 을 요청하면 get_my_today_digest 를 호출합니다.
               이 도구는 결재 대기 + 오늘 일정을 한 번에 묶어 반환하므로 search_documents 를 따로
               부르지 마세요. 결과의 pendingApprovals 는 결재 대기 top 5(긴급 우선),
               todaySchedules 는 오늘 일정 목록입니다 — 두 영역을 모두 짧게 요약해 답변합니다.
               단일 영역(결재만 / 일정만) 조회 발화는 search_documents 로 처리.
            """;

    /**
     * 시스템 프롬프트를 캐싱 친화적인 블록 리스트로 구성.
     * - 정적 블록(템플릿 + 오늘 날짜): cache_control 부착 → 동일 일자 안에서 재사용
     * - pageContext 블록(요청별 가변): cache_control 미부착 — breakpoint 뒤로 빠져 캐시 무효화 영향 없음
     *
     * 오늘 날짜는 매 요청 새로 채우지만 같은 날 안에서는 동일 → 정적 블록에 안전하게 포함.
     * 자정 1분만 캐시 invalidate 되고 그 이후 새 prefix 로 재캐시.
     */
    private static List<Map<String, Object>> buildSystemBlocks(CopilotRequest.PageContext pageContext) {
        String base = String.format(SYSTEM_PROMPT_TEMPLATE,
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", java.util.Locale.KOREAN)));

        List<Map<String, Object>> blocks = new ArrayList<>();
        Map<String, Object> staticBlock = new LinkedHashMap<>();
        staticBlock.put("type", "text");
        staticBlock.put("text", base);
        staticBlock.put("cache_control", Map.of("type", "ephemeral"));
        blocks.add(staticBlock);

        String contextLine = renderPageContextLine(pageContext);
        if (contextLine != null) {
            // pageContext 는 요청별 가변 → cache_control 안 붙임. 캐시된 정적 블록 뒤에 추가만.
            blocks.add(Map.of("type", "text", "text", contextLine));
        }
        return blocks;
    }

    /**
     * 페이지 컨텍스트 → system prompt 끝에 붙는 한 줄 안내.
     * 사용자가 "이 결재", "이 사람" 같은 지시 표현을 쓸 때 LLM 이 현재 화면 기준으로 해석하도록 유도.
     * route 만으로 충분 — 화면명 매핑은 LLM 이 경로 prefix 로 추론(/approval, /hr/payroll 등).
     */
    private static String renderPageContextLine(CopilotRequest.PageContext pageContext) {
        if (pageContext == null) return null;
        String route = pageContext.getRoute();
        if (route == null || route.isBlank()) return null;
        // 의도적으로 톤 약하게 — 발화에 "이/그/저" 같은 지시 표현이 있을 때만 활용하라는 힌트.
        // 일반 발화에 대해 명확화를 강요하지 않도록 함(과보호 회귀 방지).
        return "[참고] 사용자가 보고 있는 현재 화면 경로: " + route + ". " +
                "발화에 \"이 결재/이 사람/이 일정\" 같은 지시 표현이 있을 때만 이 경로를 단서로 활용하세요. " +
                "그 외 일반 발화에는 영향을 주지 마세요.";
    }

    /** LLM 응답에 노출 가능한 metadata 키. 민감 필드(salary 등)는 화이트리스트에서 제외해 사전 차단. */
    private static final Set<String> METADATA_WHITELIST = Set.of(
            "empName", "deptName", "deptCode", "gradeName", "titleName",
            "docNum", "location", "status", "approverName", "startAt", "endAt"
    );

    private final AnthropicClient anthropicClient;
    private final OllamaClient ollamaClient;
    private final SensitiveDetector sensitiveDetector;
    private final SearchService searchService;
    private final CalendarClient calendarClient;
    private final com.peoplecore.llm.client.ApprovalClient approvalClient;
    private final HrSelfServiceClient hrSelfServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int maxIterations;

    public CopilotService(
            AnthropicClient anthropicClient,
            OllamaClient ollamaClient,
            SensitiveDetector sensitiveDetector,
            SearchService searchService,
            CalendarClient calendarClient,
            com.peoplecore.llm.client.ApprovalClient approvalClient,
            HrSelfServiceClient hrSelfServiceClient,
            @Value("${anthropic.max-tool-iterations:4}") int maxIterations
    ) {
        this.anthropicClient = anthropicClient;
        this.ollamaClient = ollamaClient;
        this.sensitiveDetector = sensitiveDetector;
        this.searchService = searchService;
        this.calendarClient = calendarClient;
        this.approvalClient = approvalClient;
        this.hrSelfServiceClient = hrSelfServiceClient;
        this.maxIterations = maxIterations;
    }

    public CopilotResponse chat(CopilotRequest req, String companyId, Long empId, String role) {
        // 민감 라우팅 1차 — 발화·페이지 컨텍스트가 민감하면 외부(Anthropic) 대신 로컬 sLLM 경유.
        // 이유: 컴플라이언스 — 급여/평가/주민번호/주소 등이 외부 API 로그에 흘러가지 않게.
        SensitiveDetector.Verdict verdict = sensitiveDetector.classify(req.getMessage(), req.getPageContext());
        // 진단 로그 — pageContext 가 BE 까지 도착하는지, classify 결과가 무엇인지 매 호출마다 가시화.
        // 발화 본문은 로깅하지 않음 (민감 정보가 그대로 로그에 남는 위험 방지). length 만 노출.
        log.info("[Copilot] chat in: msgLen={}, route={}, verdict={}, reason={}",
                req.getMessage() == null ? 0 : req.getMessage().length(),
                req.getPageContext() == null ? "<null>" : req.getPageContext().getRoute(),
                verdict.sensitive() ? "SENSITIVE" : "SAFE",
                verdict.reason());
        if (verdict.sensitive()) {
            log.info("[Copilot] sensitive route → EXAONE (reason={}, detail={})",
                    verdict.reason(), verdict.detail());
            return chatWithExaone(req, companyId, empId, role);
        }

        if (!anthropicClient.isConfigured()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not configured");
        }

        // messages: 과거 history(텍스트만) + 이번 user 메시지
        List<Map<String, Object>> messages = new ArrayList<>();
        if (req.getHistory() != null) {
            for (CopilotRequest.HistoryTurn turn : req.getHistory()) {
                messages.add(Map.of("role", turn.getRole(), "content", turn.getContent()));
            }
        }
        messages.add(Map.of("role", "user", "content", req.getMessage()));

        // 도구 4개 — 마지막 도구에 cache_control 부착해 4개 모두 캐시.
        // Render 순서가 tools → system → messages 이므로 도구의 마지막 항목 breakpoint 가
        // 도구 전체를 한 덩어리로 캐싱한다 (앞 3개도 자동으로 같은 entry 에 포함).
        List<Map<String, Object>> tools = new ArrayList<>(List.of(
                buildSearchTool(),
                buildCreateCalendarEventTool(),
                buildPrefillApprovalFormTool(),
                buildTodayDigestTool()
        ));
        Map<String, Object> lastTool = new LinkedHashMap<>(tools.get(tools.size() - 1));
        lastTool.put("cache_control", Map.of("type", "ephemeral"));
        tools.set(tools.size() - 1, lastTool);

        List<Map<String, Object>> systemBlocks = buildSystemBlocks(req.getPageContext());

        List<CopilotResponse.Citation> citations = new ArrayList<>();
        List<CopilotResponse.ToolCall> toolCalls = new ArrayList<>();
        List<CopilotResponse.Action> actions = new ArrayList<>();
        int totalIn = 0;
        int totalOut = 0;
        int totalCacheRead = 0;
        int totalCacheWrite = 0;
        String stopReason = null;
        String finalAnswer = "";

        for (int iter = 0; iter < maxIterations; iter++) {
            AnthropicClient.MessagesResponse resp = anthropicClient.messages(systemBlocks, messages, tools);
            stopReason = resp.stop_reason;
            if (resp.usage != null) {
                if (resp.usage.input_tokens != null) totalIn += resp.usage.input_tokens;
                if (resp.usage.output_tokens != null) totalOut += resp.usage.output_tokens;
                if (resp.usage.cache_read_input_tokens != null) totalCacheRead += resp.usage.cache_read_input_tokens;
                if (resp.usage.cache_creation_input_tokens != null) totalCacheWrite += resp.usage.cache_creation_input_tokens;
            }

            // assistant 응답 전체(content blocks) 를 그대로 messages 에 다시 넣어야 다음 호출에서
            // tool_use_id 매칭이 성립한다.
            messages.add(Map.of("role", "assistant", "content", toContentArray(resp.content)));

            if (!"tool_use".equals(stopReason)) {
                finalAnswer = extractText(resp.content);
                break;
            }

            // tool_use blocks 를 모두 실행해 하나의 user 메시지 안에 tool_result 들을 모아서 회신
            List<Map<String, Object>> toolResults = new ArrayList<>();
            if (resp.content != null) {
                for (AnthropicClient.ContentBlock block : resp.content) {
                    if (!"tool_use".equals(block.type)) continue;

                    Map<String, Object> resultContent = executeTool(
                            block.name, block.input, companyId, empId, role, citations, toolCalls, actions);

                    toolResults.add(Map.of(
                            "type", "tool_result",
                            "tool_use_id", block.id,
                            "content", resultContent.get("content")
                    ));
                }
            }
            messages.add(Map.of("role", "user", "content", toolResults));
        }

        // max-iter 도달 시 마지막 응답에 텍스트가 비어있을 수 있다 — 안내 문구로 대체
        if (finalAnswer == null || finalAnswer.isBlank()) {
            finalAnswer = "도구 호출 한도(" + maxIterations + "회) 에 도달했습니다. 질문을 더 구체적으로 다시 해주세요.";
        }

        // 캐시 적중률 — Phase 2 메트릭 대시보드에서 hit_ratio 계산할 때 쓰는 4개 카운터.
        // 적중 이상치 진단용으로 매 chat 마다 한 줄 로깅.
        log.info("[Copilot] usage: in={}, out={}, cacheRead={}, cacheWrite={} (model={})",
                totalIn, totalOut, totalCacheRead, totalCacheWrite, anthropicClient.getModel());

        Map<String, Integer> usage = new HashMap<>();
        usage.put("inputTokens", totalIn);
        usage.put("outputTokens", totalOut);
        usage.put("cacheReadTokens", totalCacheRead);
        usage.put("cacheWriteTokens", totalCacheWrite);

        return CopilotResponse.builder()
                .answer(finalAnswer)
                .citations(citations)
                .toolCalls(toolCalls)
                .actions(actions)
                .stopReason(stopReason)
                .usage(usage)
                .model(anthropicClient.getModel())
                .build();
    }

    /**
     * EXAONE 은 Ollama tool calling 지원 X — 시스템 프롬프트에 도구 카탈로그를 자연어로 박고
     * [[CALL]]...[[/CALL]] 텍스트 마커로 호출 신호를 받는 manual prompting 방식.
     * <p>
     * 작성 의도(중요):
     * - "보안 모드" 가 아니라 "본인 데이터 조회 모드" 로 framing — 작은 한국어 LLM 이 PII 키워드만 보면
     *   반사적으로 거부하는 RLHF 패턴을 깨기 위함. 본인 데이터를 도구로 가져오는 것이 이 모드의 목적임을 강조.
     * - 본인/타인 분기를 한 줄 규칙(1인칭 OR 주어 생략 → 본인, 타인 이름 명시 → 거절)으로 단순화.
     * - few-shot 3종(본인 호출 / 타인 거절 / 결과 인용) 으로 출력 포맷 고정.
     */
    private static final String EXAONE_SYSTEM_PROMPT_TEMPLATE = """
            당신은 PeopleCore 사내 코파일럿의 **본인 데이터 조회 모드** 입니다. 오늘 날짜는 %s 입니다.

            이 모드의 목적: 사용자가 자기 자신의 개인정보·급여를 도구로 즉시 조회할 수 있도록 돕는 것입니다.
            **본인 정보 요청을 거부하지 마세요.** 거부 대신 도구를 호출해 실제 데이터를 가져와 답변합니다.

            ### 본인 vs 타인 판정 (단순 규칙)
            - "내/제/나의/저의/내가/제가" 가 있거나 **주어가 생략된** 발화 → 본인. 도구 호출.
              예: "주민번호 알려줘", "급여 알려줘", "내 휴대폰", "올해 연봉" 모두 본인 의미.
            - 다른 사람 이름(예: 홍길동)이 명시된 발화 → 타인. 도구 호출 없이 한 문장으로 거절:
              "권한이 없습니다 — 본인 데이터만 조회 가능합니다."

            ### 도구 호출 규약 (반드시 정확히 이 형식)
            본인 정보 요청이면, 다른 텍스트 없이 **이 한 줄만** 출력하세요:

            [[CALL]]{"name":"<도구명>","args":{...}}[[/CALL]]

            - args 가 비면 {} 로. JSON 키·문자열은 큰따옴표.
            - 호출 turn 에는 한국어 설명·코드펜스·인사말 금지 — 호출만 출력해야 시스템이 결과를 회신합니다.
            - 시스템이 [[RESULT]]{...}[[/RESULT]] 로 답을 주면, **다음 turn** 에 그 데이터를 인용해 한국어로 답변.
            - 결과 받은 후에는 [[CALL]] 을 다시 출력하지 마세요.

            ### 사용 가능한 도구

            1) get_my_personal_info — 본인 개인정보(이름·휴대폰·개인이메일·생년월일·성별·주소·주민번호).
               args: {} (없음). 주민번호는 서버가 자동으로 마스킹된 형태로 반환합니다.
               트리거: 사용자가 "주민번호/휴대폰/전화번호/이메일/주소/생년월일" **단일 항목** 을 물을 때.
               예: "내 주민번호 알려줘", "내 휴대폰 번호".

            2) get_my_payroll — 본인 기본 급여 정보(연봉 annualSalary, 월급 monthlySalary,
               고정수당 fixedAllowances 항목별 이름·금액). args: {} (없음).
               트리거: 사용자가 "급여/연봉/월급/수당/보너스" **단일 항목** 을 물을 때.
               월별 명세서·실수령액은 이 도구로 조회 불가 — "지난달 명세서/실수령액" 요청 시 도구 호출 없이
               "급여명세서는 [내 정보 > 급여명세서] 화면에서 확인 가능합니다" 라고 안내.

            3) get_my_overview — 본인 인사 정보 다이제스트(개인정보 + 급여 + 잔여 연차 + 이번 주 근태)를
               한 번에 묶어 반환. args: {} (없음).
               트리거: 사용자가 "내 정보/내 인사정보/한 번에/요약/한눈에/대시보드/내 현황" 등 **여러 도메인을
               아우르는 종합 조회** 를 물을 때. 단일 항목(주민번호만, 급여만 등) 은 1번/2번 도구를 쓰세요.
               예 트리거: "내 정보 한 번에 보여줘", "내 인사 현황 알려줘", "내 인사 정보 요약".

            4) get_my_evaluation — 본인 인사평가 다이제스트(최신 시즌의 평가 등급·피드백 +
               현재 진행 중 시즌의 본인 목표 + 자기평가). args: {} (없음).
               서버가 자동으로 최신 시즌을 선택하므로 시즌 ID·연도를 args 에 넣지 마세요.
               트리거: 사용자가 "평가/고과/인사평가/역량평가/성과평가/내 등급/평가 피드백/내 목표/자기평가"
               등 평가 관련 조회를 물을 때.
               예 트리거: "내 인사평가 알려줘", "올해 내 평가 결과", "내 목표 어떻게 돼?", "내 자기평가".
               결과의 result.finalGrade 가 비어있으면 "아직 최종 등급이 확정되지 않았다" 고 안내.
               result.feedback 은 매니저 피드백이며 GRADING 단계 전에는 비어있을 수 있음 — 비어있으면
               "피드백이 아직 공개되지 않았다" 라고 답변.

            5) get_my_vacation_status — 본인 연차 잔액·사용 이력 조회.
               args: {} (올해 자동) 또는 {"year": 2025} (특정 연도).
               응답 구조 (데이터 있음):
                 { ok:true, year, hasBalance:true, annual:{ typeName, totalDays, usedDays, pendingDays, availableDays } }
               응답 구조 (데이터 없음):
                 { ok:true, year, hasBalance:false, message:"YYYY년 연차 잔액 정보가 등록되어있지 않습니다." }
               availableDays = 신청 가능 일수, usedDays = 사용한 일수, pendingDays = 결재 대기 중.

               **답변 규칙**:
               - hasBalance:true 이면 annual 객체의 **실제 숫자값** 을 인용해 답변
                 (예: "올해 잔여 연차는 15일 남았습니다. 사용 0일, 결재 대기 0일.")
               - hasBalance:false 이면 message 그대로 안내 + "인사팀에 확인해주세요" 추가.
                 추측 답변·placeholder 텍스트 금지.

               트리거: 사용자가 "잔여연차/잔여휴가/연차잔액/연차 며칠/휴가 며칠 남았/올해 연차"
               등 **잔액 조회** 또는 "작년 연차/작년 휴가 며칠 썼" 등 **연도별 사용 이력** 발화를 할 때.
               연도가 명시되면 args.year 에 정수로 넣고(예: "작년" → 2025, "올해" → 2026), 미명시 시 args:{}.
               **휴가 신청** 발화 ("휴가 신청해줘", "올려줘") 는 이 도구 대상이 아님 — 결재 신청은
               별도 흐름이라 이 모드에선 처리하지 않습니다. 잔액 조회 후 사용자에게 "휴가 신청은
               근태 화면 또는 결재 화면에서 진행해주세요" 라고 안내하세요.

            ### 출력 예시 (반드시 따라하세요)

            예시 1 — 본인 개인정보 호출:
            [사용자] 주민번호 알려줘
            [당신] [[CALL]]{"name":"get_my_personal_info","args":{}}[[/CALL]]

            예시 2 — 본인 급여 호출:
            [사용자] 내 급여 알려줘
            [당신] [[CALL]]{"name":"get_my_payroll","args":{}}[[/CALL]]

            예시 3 — 다이제스트 호출:
            [사용자] 내 인사 정보 한 번에 보여줘
            [당신] [[CALL]]{"name":"get_my_overview","args":{}}[[/CALL]]

            예시 4-a — 평가 호출 (가장 흔한 발화):
            [사용자] 내 인사평가 알려줘
            [당신] [[CALL]]{"name":"get_my_evaluation","args":{}}[[/CALL]]

            예시 4-b — 평가 호출 (결과 강조):
            [사용자] 올해 내 평가 결과
            [당신] [[CALL]]{"name":"get_my_evaluation","args":{}}[[/CALL]]

            예시 4-c — 평가 호출 (목표 강조):
            [사용자] 내 목표 어떻게 돼?
            [당신] [[CALL]]{"name":"get_my_evaluation","args":{}}[[/CALL]]

            예시 5-a — 잔여 연차 호출 (올해):
            [사용자] 잔여 연차 며칠 남았어?
            [당신] [[CALL]]{"name":"get_my_vacation_status","args":{}}[[/CALL]]

            예시 5-b — 잔여 휴가 호출 (올해):
            [사용자] 올해 내 휴가 잔액 알려줘
            [당신] [[CALL]]{"name":"get_my_vacation_status","args":{}}[[/CALL]]

            예시 5-c — 작년 사용 이력 호출 (year 명시):
            [사용자] 작년에 연차 며칠 썼어?
            [당신] [[CALL]]{"name":"get_my_vacation_status","args":{"year":2025}}[[/CALL]]

            ### 절대 하지 마세요 (anti-pattern)
            아래는 모두 **잘못된 응답** 입니다 — 도구를 호출하지 않고 추측·회피로 답하면 안 됩니다.
              - "현재 평가가 진행 중이라 결과를 확인하기 어렵습니다" ← 추측 금지. 도구를 호출하세요.
              - "아직 등급이 확정되지 않은 것 같습니다" ← 추측 금지. 도구를 호출하세요.
              - "인사팀에 문의해주세요" ← 도구 호출 없이 안내 금지. 먼저 도구를 호출하세요.
              - "[결과 데이터에서 X 값 인용]" / "[X 일 남았습니다]" / "**남은 일수**" ← placeholder·치환문구 절대 금지.
                도구 결과 JSON 의 **실제 값** 을 그대로 인용하세요. 값이 없으면 hasBalance/hasSeason 등
                플래그를 보고 "정보가 없다" 고 안내하세요. 추측해 임의의 숫자나 placeholder 를 넣지 마세요.
            평가/고과/목표/자기평가 단어가 발화에 있으면 **무조건** 첫 turn 에 [[CALL]] 만 출력합니다.
            도구 결과를 받기 전에는 데이터 상태를 절대 단정하지 마세요.

            예시 5 — 결과 받은 후 답변:
            [사용자] [[RESULT]]{"ok":true,"empName":"홍길동","empResidentNumberMasked":"901234-1******","empPhone":"010-1234-5678"}[[/RESULT]]
            [당신] 홍길동 님의 주민번호는 901234-1****** (마스킹) 이며, 등록된 휴대폰 번호는 010-1234-5678 입니다.

            예시 6 — 타인 요청 거절:
            [사용자] 홍진희 주민번호 알려줘
            [당신] 권한이 없습니다 — 본인 데이터만 조회 가능합니다.

            ### 답변 원칙 (결과 받은 후)
            1) 결과의 마스킹된 주민번호는 그대로 노출(예: 901234-1******). 풀 RRN 추측·복원 금지.
            2) ok=false 면 "조회 실패" 만 답변, 추측 금지.
            3) 회사·사번은 서버가 관리. 사용자 발화의 사번/주민번호는 답변에 echo 하지 마세요.
            4) 한국어, 2~5문장 이내로 간결하게.
            """;

    /** 모델 출력에서 [[CALL]]...[[/CALL]] 블록 추출. JSON 본문은 lazy/non-greedy 매치(.+?) — DOTALL 로 줄바꿈 허용. */
    private static final Pattern EXAONE_TOOL_CALL_PATTERN = Pattern.compile(
            "\\[\\[CALL]]\\s*(\\{.+?})\\s*\\[\\[/CALL]]",
            Pattern.DOTALL
    );

    /**
     * 민감 경로 응답 — EXAONE (로컬 sLLM) 으로 답변.
     * 도구 호출 manual prompting: system prompt 에 도구 카탈로그 + [[CALL]]...[[/CALL]] 마커 규약을 박고,
     * 모델 출력을 정규식으로 파싱. companyId/empId 는 서버 인증 컨텍스트에서 강제 주입 — LLM 이
     * 임의 값을 넣어도 무시한다(보안 핵심). 도구 결과는 외부(Anthropic) 로 절대 송출되지 않음.
     */
    private CopilotResponse chatWithExaone(CopilotRequest req, String companyId, Long empId, String role) {
        String systemPrompt = String.format(EXAONE_SYSTEM_PROMPT_TEMPLATE,
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", java.util.Locale.KOREAN)));
        String pageLine = renderPageContextLine(req.getPageContext());
        if (pageLine != null) systemPrompt = systemPrompt + "\n\n" + pageLine;

        List<Map<String, Object>> messages = new ArrayList<>();
        if (req.getHistory() != null) {
            for (CopilotRequest.HistoryTurn turn : req.getHistory()) {
                messages.add(Map.of("role", turn.getRole(), "content", turn.getContent()));
            }
        }
        messages.add(Map.of("role", "user", "content", req.getMessage()));

        UUID companyUuid;
        try {
            companyUuid = UUID.fromString(companyId);
        } catch (Exception e) {
            log.warn("[Copilot] EXAONE: invalid companyId={}", companyId);
            companyUuid = null;
        }

        List<CopilotResponse.ToolCall> toolCalls = new ArrayList<>();
        String answer = "";
        int inTokens = 0;
        int outTokens = 0;

        // manual tool-loop — Ollama tools 필드 미사용. 모델 텍스트에서 [[CALL]] 마커 파싱.
        try {
            for (int iter = 0; iter < maxIterations; iter++) {
                OllamaClient.ChatResponse resp = ollamaClient.chat(systemPrompt, messages, null);
                if (resp == null || resp.message == null) {
                    answer = "응답을 생성하지 못했습니다.";
                    break;
                }
                if (resp.prompt_eval_count != null) inTokens += resp.prompt_eval_count.intValue();
                if (resp.eval_count != null) outTokens += resp.eval_count.intValue();

                String content = resp.message.content == null ? "" : resp.message.content;
                ParsedToolCall parsed = parseExaoneToolCall(content);
                if (parsed == null) {
                    // [[CALL]] 없음 → 최종 답변. 안전을 위해 혹시 남아있을지 모르는 마커는 제거.
                    answer = stripToolMarkers(content);
                    break;
                }

                if (companyUuid == null) {
                    answer = "인증 컨텍스트가 없어 본인 정보를 조회할 수 없습니다.";
                    break;
                }

                // assistant 가 발화한 호출 텍스트를 messages 에 그대로 넣고, 결과는 user 메시지로 회신.
                messages.add(Map.of("role", "assistant", "content", content));

                String resultJson = executeExaoneTool(parsed.name, parsed.args, companyUuid, empId, role);
                toolCalls.add(CopilotResponse.ToolCall.builder()
                        .name(parsed.name)
                        .input(parsed.args)
                        .resultCount(1)
                        .build());

                String resultMessage = "[[RESULT]]" + resultJson + "[[/RESULT]]\n\n" +
                        "위 결과를 참고해 사용자 질문에 한국어로 답변하세요. 더 이상 [[CALL]] 을 출력하지 마세요.";
                messages.add(Map.of("role", "user", "content", resultMessage));
            }
        } catch (Exception e) {
            log.error("[Copilot] EXAONE call failed: {}", e.getMessage());
            answer = "로컬 AI 서비스 호출에 실패했습니다. 잠시 후 다시 시도해 주세요.";
        }

        if (answer == null || answer.isBlank()) {
            answer = "도구 호출 한도에 도달했습니다. 질문을 더 구체적으로 다시 해주세요.";
        }

        Map<String, Integer> usage = new HashMap<>();
        usage.put("inputTokens", inTokens);
        usage.put("outputTokens", outTokens);

        return CopilotResponse.builder()
                .answer(answer)
                .citations(new ArrayList<>())
                .toolCalls(toolCalls)
                .actions(new ArrayList<>())
                .stopReason("end_turn")
                .usage(usage)
                .model(ollamaClient.getModel())
                .build();
    }

    /** 모델 출력에서 첫 [[CALL]]...[[/CALL]] 블록을 파싱. 마커 없거나 JSON 깨졌으면 null. */
    private ParsedToolCall parseExaoneToolCall(String content) {
        if (content == null || content.isEmpty()) return null;
        Matcher m = EXAONE_TOOL_CALL_PATTERN.matcher(content);
        if (!m.find()) return null;
        String json = m.group(1);
        try {
            Map<?, ?> raw = objectMapper.readValue(json, Map.class);
            Object name = raw.get("name");
            if (!(name instanceof String s) || s.isBlank()) return null;
            Object args = raw.get("args");
            @SuppressWarnings("unchecked")
            Map<String, Object> argMap = (args instanceof Map<?, ?>)
                    ? (Map<String, Object>) args : Map.of();
            return new ParsedToolCall(s, argMap);
        } catch (Exception e) {
            log.warn("[Copilot] EXAONE tool call JSON parse failed: {}", e.getMessage());
            return null;
        }
    }

    /** 최종 답변 텍스트에서 혹시 남아있는 [[CALL]]/[[RESULT]] 마커 제거. */
    private String stripToolMarkers(String content) {
        if (content == null) return "";
        return content
                .replaceAll("\\[\\[CALL]].*?\\[\\[/CALL]]", "")
                .replaceAll("\\[\\[RESULT]].*?\\[\\[/RESULT]]", "")
                .trim();
    }

    private record ParsedToolCall(String name, Map<String, Object> args) {}

    /**
     * EXAONE 도구 디스패치. companyId/empId 는 인증 컨텍스트에서 받은 값만 사용 —
     * LLM 이 인자에 넣은 어떤 empId 도 무시(서버 측 강제 주입).
     */
    private String executeExaoneTool(String name, Map<String, Object> args, UUID companyId, Long empId, String role) {
        if (companyId == null || empId == null) {
            return "{\"ok\":false,\"error\":\"인증 컨텍스트 없음\"}";
        }
        try {
            Map<String, Object> result;
            if ("get_my_personal_info".equals(name)) {
                result = hrSelfServiceClient.getMyPersonalInfo(companyId, empId, role);
            } else if ("get_my_payroll".equals(name)) {
                result = hrSelfServiceClient.getMySalarySummary(companyId, empId, role);
            } else if ("get_my_overview".equals(name)) {
                result = hrSelfServiceClient.getMyOverview(companyId, empId, role);
            } else if ("get_my_evaluation".equals(name)) {
                result = hrSelfServiceClient.getMyEvaluation(companyId, empId, role);
            } else if ("get_my_vacation_status".equals(name)) {
                // year 옵션 — LLM 이 args.year 에 정수를 넣을 수도 있고 비울 수도 있음.
                // 합리적 범위(현재년도 ±5) 외의 값은 null 처리해 BE 가 올해로 폴백시킴.
                Integer year = parseYearArg(args, LocalDate.now().getYear());
                result = hrSelfServiceClient.getMyVacationStatus(companyId, empId, role, year);
            } else {
                return "{\"ok\":false,\"error\":\"unknown tool: " + escape(name) + "\"}";
            }
            // 중첩 List/Map 포함 — Jackson 으로 직렬화 (mapToJson 은 flat 전용).
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("[Copilot] EXAONE tool failed: name={}, err={}", name, e.getMessage());
            return "{\"ok\":false,\"error\":\"" + escape(e.getMessage()) + "\"}";
        }
    }

    /**
     * args.year 안전 추출. LLM 이 정수·문자열 어느 쪽으로 보내도 흡수하고,
     * 현재년도 ±5 범위 밖이거나 파싱 실패 시 null 반환 → HrSelfServiceClient 가 올해로 폴백.
     * 환각으로 9999 같은 비정상 값을 넣어도 BE 가 안전하게 처리.
     */
    private Integer parseYearArg(Map<String, Object> args, int currentYear) {
        if (args == null) return null;
        Object raw = args.get("year");
        if (raw == null) return null;
        int year;
        if (raw instanceof Number n) {
            year = n.intValue();
        } else if (raw instanceof String s && !s.isBlank()) {
            try { year = Integer.parseInt(s.trim()); }
            catch (NumberFormatException e) { return null; }
        } else {
            return null;
        }
        if (year < currentYear - 5 || year > currentYear + 1) return null;  // 합리적 범위만
        return year;
    }

    private List<Map<String, Object>> toContentArray(List<AnthropicClient.ContentBlock> blocks) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (blocks == null) return out;
        for (AnthropicClient.ContentBlock b : blocks) {
            if ("text".equals(b.type)) {
                out.add(Map.of("type", "text", "text", b.text == null ? "" : b.text));
            } else if ("tool_use".equals(b.type)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "tool_use");
                m.put("id", b.id);
                m.put("name", b.name);
                m.put("input", b.input == null ? Map.of() : b.input);
                out.add(m);
            }
        }
        return out;
    }

    /** stop_reason=end_turn 시 모든 text 블록을 이어붙여 최종 답변 생성. */
    private String extractText(List<AnthropicClient.ContentBlock> blocks) {
        if (blocks == null) return "";
        StringBuilder sb = new StringBuilder();
        for (AnthropicClient.ContentBlock b : blocks) {
            if ("text".equals(b.type) && b.text != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(b.text);
            }
        }
        return sb.toString();
    }

    /** 도구 디스패치. 도구가 추가되면 여기서 name 으로 분기. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeTool(String name, Map<String, Object> input,
                                            String companyId, Long empId, String role,
                                            List<CopilotResponse.Citation> citations,
                                            List<CopilotResponse.ToolCall> toolCalls,
                                            List<CopilotResponse.Action> actions) {
        if ("create_calendar_event".equals(name)) {
            return executeCreateCalendarEvent(input, companyId, empId, citations, toolCalls);
        }
        if ("prefill_approval_form".equals(name)) {
            return executePrefillApprovalForm(input, companyId, empId, role, toolCalls, actions);
        }
        if ("get_my_today_digest".equals(name)) {
            return executeTodayDigest(input, companyId, empId, toolCalls);
        }
//        LLM의 도구 환각 방지(서버에 있는 도구가 아니면 error JSON 반환)
        if (!"search_documents".equals(name)) {
            return Map.of("content", "[{\"error\":\"unknown tool: " + escape(name) + "\"}]");
        }

        String keyword = input != null ? (String) input.get("keyword") : null;
        String type = input != null ? (String) input.get("type") : null;
        Object sizeObj = input != null ? input.get("size") : null;
        int size = 5;
        if (sizeObj instanceof Number n) size = Math.min(Math.max(n.intValue(), 1), 10);

        if (keyword == null || keyword.isBlank()) {
            toolCalls.add(CopilotResponse.ToolCall.builder()
                    .name(name).input(input == null ? Map.of() : input).resultCount(0).build());
            return Map.of("content", "[{\"error\":\"keyword is required\"}]");
        }

        try {
            SearchResponse sr = searchService.searchHybrid(keyword, type, companyId, empId, role, size);
            String rendered = renderSearchResults(sr, citations);
            toolCalls.add(CopilotResponse.ToolCall.builder()
                    .name(name)
                    .input(input == null ? Map.of() : input)
                    .resultCount(sr.getItems() == null ? 0 : sr.getItems().size())
                    .build());
            return Map.of("content", rendered);
        } catch (Exception e) {
            log.error("tool execution failed: name={}, keyword={}, err={}", name, keyword, e.getMessage());
            toolCalls.add(CopilotResponse.ToolCall.builder()
                    .name(name).input(input == null ? Map.of() : input).resultCount(0).build());
            return Map.of("content", "[{\"error\":\"" + escape(e.getMessage()) + "\"}]");
        }
    }

    /**
     * SearchResponse → LLM 친화적인 컴팩트 JSON 문자열.
     * 동시에 citations 리스트도 채워서 UI 가 클릭 가능한 링크를 그릴 수 있게 한다.
     */
    private String renderSearchResults(SearchResponse sr, List<CopilotResponse.Citation> citations) {
        StringBuilder sb = new StringBuilder("[");
        List<SearchResultItem> items = sr.getItems();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                SearchResultItem it = items.get(i);
                if (i > 0) sb.append(",");
                sb.append("{");
                sb.append("\"id\":\"").append(escape(it.getId())).append("\"");
                sb.append(",\"type\":\"").append(escape(it.getType())).append("\"");
                sb.append(",\"title\":\"").append(escape(it.getTitle())).append("\"");

                Map<String, Object> meta = it.getMetadata();
                if (meta != null && !meta.isEmpty()) {
                    sb.append(",\"metadata\":{");
                    boolean first = true;
                    for (Map.Entry<String, Object> e : meta.entrySet()) {
                        if (!METADATA_WHITELIST.contains(e.getKey())) continue;
                        if (e.getValue() == null) continue;
                        if (!first) sb.append(",");
                        sb.append("\"").append(escape(e.getKey())).append("\":\"")
                                .append(escape(String.valueOf(e.getValue()))).append("\"");
                        first = false;
                    }
                    sb.append("}");
                }
                sb.append("}");

                citations.add(CopilotResponse.Citation.builder()
                        .id(it.getId())
                        .type(it.getType())
                        .title(it.getTitle())
                        .link(buildLink(it))
                        .build());
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /** 도메인별 deep-link 규칙. FE 라우팅과 1:1 매칭 — 새 type 추가 시 여기 분기 추가. */
    private String buildLink(SearchResultItem it) {
        String type = it.getType();
        String sourceId = it.getSourceId();
        if (type == null || sourceId == null) return null;
        return switch (type) {
            case "EMPLOYEE" -> "/hr/employees/" + sourceId;
            case "DEPARTMENT" -> "/hr/departments/" + sourceId;
            case "APPROVAL" -> "/approval/" + sourceId;
            case "CALENDAR" -> "/calendar/" + sourceId;
            default -> null;
        };
    }

    /**
     * create_calendar_event 실행. CalendarClient 가 myCalendarsId 자동 해결 후 POST.
     * 성공 시 citation(타입 CALENDAR) 한 건을 추가해 UI 가 클릭 진입할 수 있게 한다.
     */
    private Map<String, Object> executeCreateCalendarEvent(Map<String, Object> input,
                                                           String companyId, Long empId,
                                                           List<CopilotResponse.Citation> citations,
                                                           List<CopilotResponse.ToolCall> toolCalls) {
        UUID companyUuid;
        try {
            companyUuid = UUID.fromString(companyId);
        } catch (Exception e) {
            toolCalls.add(CopilotResponse.ToolCall.builder()
                    .name("create_calendar_event").input(input == null ? Map.of() : input).resultCount(0).build());
            return Map.of("content", "{\"ok\":false,\"error\":\"invalid companyId\"}");
        }

        Map<String, Object> result = calendarClient.createEvent(companyUuid, empId, input == null ? Map.of() : input);
        boolean ok = Boolean.TRUE.equals(result.get("ok"));
        toolCalls.add(CopilotResponse.ToolCall.builder()
                .name("create_calendar_event")
                .input(input == null ? Map.of() : input)
                .resultCount(ok ? 1 : 0)
                .build());

        if (ok && result.get("eventsId") != null) {
            String eventsId = String.valueOf(result.get("eventsId"));
            String title = String.valueOf(result.getOrDefault("title", ""));
            citations.add(CopilotResponse.Citation.builder()
                    .id("CALENDAR_" + eventsId)
                    .type("CALENDAR")
                    .title(title)
                    .link("/calendar/" + eventsId)
                    .build());
        }

        // tool_result 는 LLM 이 자연어 답변에 그대로 인용할 수 있도록 컴팩트 JSON 으로 반환
        return Map.of("content", mapToJson(result));
    }

    /** 지원 양식 코드. 늘어나면 여기와 buildPrefillApprovalFormTool 의 enum 양쪽 동기화. */
    private static final Set<String> SUPPORTED_FORM_CODES = Set.of("VACATION_REQUEST", "OVERTIME_REQUEST");

    /**
     * prefill_approval_form 실행. 서버에 아무것도 저장하지 않는 "클라이언트 사이드 액션" —
     * actions 리스트에 OPEN_APPROVAL_FORM directive 를 쌓고 LLM 에는 "성공" 만 반환.
     * 사용자가 결재 받을 사람 이름을 명시하면 SearchService 로 EMPLOYEE 한 명을 찾아 결재선에 채운다.
     * 잔여휴가·근태 같은 HR 검증 필요 데이터는 사용자가 모달에서 직접 입력 (LLM 환각 방지).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executePrefillApprovalForm(Map<String, Object> input,
                                                           String companyId, Long empId, String role,
                                                           List<CopilotResponse.ToolCall> toolCalls,
                                                           List<CopilotResponse.Action> actions) {
        String formCode = input == null ? null : (String) input.get("formCode");
        if (formCode == null || !SUPPORTED_FORM_CODES.contains(formCode)) {
            toolCalls.add(CopilotResponse.ToolCall.builder()
                    .name("prefill_approval_form")
                    .input(input == null ? Map.of() : input)
                    .resultCount(0).build());
            return Map.of("content", "{\"ok\":false,\"error\":\"unsupported formCode. allowed: VACATION_REQUEST, OVERTIME_REQUEST\"}");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("formCode", formCode);
        payload.put("formName", formNameOf(formCode));

        // 양식 HTML 의 input name 과 1:1 매칭되어야 ApprovalDocumentPage 의 querySelector(`[name=...]`) 가
        // 값을 주입한다. formCode 마다 사유 필드명이 다르므로(휴가=vacReqReason, 초과근무=otReason) 분기.
        String reasonKey = "VACATION_REQUEST".equals(formCode) ? "vacReqReason" : "otReason";
        Map<String, Object> prefill = new LinkedHashMap<>();
        if (input.get("docTitle") instanceof String s && !s.isBlank()) prefill.put("docTitle", s);
        if (input.get("reason") instanceof String s && !s.isBlank()) prefill.put(reasonKey, s);
        payload.put("prefill", prefill);

        // 결재선 자동 해결 — 검색 인덱스에서 EMPLOYEE 한 명만 골라 OrgMember 형태로 변환
        List<Map<String, Object>> resolvedApprovers = new ArrayList<>();
        List<String> unresolvedNames = new ArrayList<>();
        Object rawApprovers = input.get("approverNames");
        if (rawApprovers instanceof List<?> names) {
            for (Object n : names) {
                if (!(n instanceof String name) || name.isBlank()) continue;
                Map<String, Object> resolved = resolveApprover(name, companyId, empId, role);
                if (resolved != null) resolvedApprovers.add(resolved);
                else unresolvedNames.add(name);
            }
        }
        if (!resolvedApprovers.isEmpty()) payload.put("initialApprovers", resolvedApprovers);

        actions.add(CopilotResponse.Action.builder()
                .type("OPEN_APPROVAL_FORM")
                .payload(payload)
                .build());

        toolCalls.add(CopilotResponse.ToolCall.builder()
                .name("prefill_approval_form")
                .input(input == null ? Map.of() : input)
                .resultCount(1).build());

        // LLM 이 답변에 무엇이 채워졌고 못 찾은 이름이 무엇인지 자연어로 안내할 수 있도록 회신
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"formCode\":\"").append(formCode).append("\"");
        sb.append(",\"prefilledFields\":[");
        boolean first = true;
        for (String k : prefill.keySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escape(k)).append("\"");
            first = false;
        }
        sb.append("],\"resolvedApprovers\":").append(resolvedApprovers.size());
        if (!unresolvedNames.isEmpty()) {
            sb.append(",\"unresolvedNames\":[");
            first = true;
            for (String n : unresolvedNames) {
                if (!first) sb.append(",");
                sb.append("\"").append(escape(n)).append("\"");
                first = false;
            }
            sb.append("]");
        }
        sb.append("}");
        return Map.of("content", sb.toString());
    }

    /**
     * 이름 한 건을 EMPLOYEE 검색으로 1건 해결해 OrgMember 형태(empId/empName/empDeptId/empDeptName/empGrade/empTitle) 로 변환.
     * 검색 결과가 없거나 type 이 EMPLOYEE 가 아니면 null 반환 → unresolvedNames 로 분류.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveApprover(String name, String companyId, Long empId, String role) {
        try {
            SearchResponse sr = searchService.searchHybrid(name, "EMPLOYEE", companyId, empId, role, 1);
            if (sr.getItems() == null || sr.getItems().isEmpty()) return null;
            SearchResultItem hit = sr.getItems().get(0);
            if (!"EMPLOYEE".equals(hit.getType())) return null;

            Map<String, Object> meta = hit.getMetadata() == null ? Map.of() : hit.getMetadata();
            Map<String, Object> approver = new LinkedHashMap<>();
            try {
                approver.put("empId", Long.parseLong(hit.getSourceId()));
            } catch (NumberFormatException nfe) {
                return null;
            }
            approver.put("empName", meta.getOrDefault("empName", hit.getTitle()));
            if (meta.get("deptId") instanceof Number dn) approver.put("empDeptId", dn.longValue());
            approver.put("empDeptName", meta.getOrDefault("deptName", ""));
            approver.put("empGrade", meta.getOrDefault("gradeName", ""));
            approver.put("empTitle", meta.getOrDefault("titleName", ""));
            return approver;
        } catch (Exception e) {
            log.warn("resolveApprover failed: name={}, err={}", name, e.getMessage());
            return null;
        }
    }

    private String formNameOf(String formCode) {
        return switch (formCode) {
            case "VACATION_REQUEST" -> "휴가신청";
            case "OVERTIME_REQUEST" -> "초과근무신청";
            default -> formCode;
        };
    }

    /**
     * 오늘 다이제스트 — 결재 대기(top 5, 긴급 우선) + 오늘 일정 묶음.
     * BE composite 패턴: LLM 입장에선 1 도구 호출, BE 안에서 2 endpoint 묶음 처리.
     * pendingApprovals 가 있으면 docId 들로 citation 도 누적해 사용자가 즉시 진입 가능.
     */
    private Map<String, Object> executeTodayDigest(Map<String, Object> input,
                                                    String companyId, Long empId,
                                                    List<CopilotResponse.ToolCall> toolCalls) {
        UUID companyUuid;
        try {
            companyUuid = UUID.fromString(companyId);
        } catch (Exception e) {
            toolCalls.add(CopilotResponse.ToolCall.builder()
                    .name("get_my_today_digest").input(input == null ? Map.of() : input).resultCount(0).build());
            return Map.of("content", "{\"ok\":false,\"error\":\"invalid companyId\"}");
        }

        // 결재 대기 top 5 (긴급 우선)
        Map<String, Object> approvals = approvalClient.getMyPendingApprovals(companyUuid, empId, 5);
        // 오늘 일정 (date 미지정 시 LocalDate.now())
        Map<String, Object> events = calendarClient.getMyEventsForDate(companyUuid, empId, null);

        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("ok", true);
        digest.put("date", LocalDate.now().toString());
        digest.put("pendingApprovals", approvals);
        digest.put("todaySchedules", events);

        toolCalls.add(CopilotResponse.ToolCall.builder()
                .name("get_my_today_digest")
                .input(input == null ? Map.of() : input)
                .resultCount(1)
                .build());

        try {
            return Map.of("content", objectMapper.writeValueAsString(digest));
        } catch (Exception e) {
            log.error("today digest serialize failed", e);
            return Map.of("content", "{\"ok\":false,\"error\":\"serialize failed\"}");
        }
    }

    private String mapToJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else sb.append("\"").append(escape(v.toString())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /** Anthropic tool spec — JSON Schema 로 input 스키마 명시. LLM 이 이 스키마에 맞춰 input 을 생성한다. */
    private Map<String, Object> buildSearchTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", Map.of(
                "type", "string",
                "description", "검색어. 사용자 질문에서 핵심 명사·키워드를 추출해 넣는다. 한국어 그대로."
        ));
        properties.put("type", Map.of(
                "type", "string",
                "enum", List.of("EMPLOYEE", "DEPARTMENT", "APPROVAL", "CALENDAR"),
                "description", "결과를 특정 도메인으로 좁히고 싶을 때만 지정. 모르겠으면 생략."
        ));
        properties.put("size", Map.of(
                "type", "integer",
                "minimum", 1,
                "maximum", 10,
                "description", "반환할 결과 개수. 기본 5."
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("keyword"));

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "search_documents");
        tool.put("description", "PeopleCore 사내 통합검색(BM25+kNN 하이브리드). 직원/부서/결재/일정을 한 번에 조회. " +
                "권한·회사 필터는 서버가 자동 적용하므로 절대 input 에 넣지 말 것.");
        tool.put("input_schema", schema);
        return tool;
    }

    /**
     * create_calendar_event tool spec. 사용자가 명시적으로 "일정 잡아줘" 라고 했을 때만 호출.
     * myCalendarsId 는 LLM 이 모르므로 schema 에서 제외 — 서버가 자동 해결한다.
     */
    private Map<String, Object> buildCreateCalendarEventTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", Map.of(
                "type", "string",
                "description", "일정 제목. 사용자 발화에서 핵심을 추출."
        ));
        properties.put("startAt", Map.of(
                "type", "string",
                "description", "시작 시각, ISO 8601 LocalDateTime 형식 (예: 2026-04-28T14:00:00). 시간 미지정 시 09:00 기본."
        ));
        properties.put("endAt", Map.of(
                "type", "string",
                "description", "종료 시각, ISO 8601 LocalDateTime 형식. 미지정 시 startAt + 1시간."
        ));
        properties.put("description", Map.of(
                "type", "string",
                "description", "메모/상세. 선택."
        ));
        properties.put("location", Map.of(
                "type", "string",
                "description", "장소. 선택."
        ));
        properties.put("isAllDay", Map.of(
                "type", "boolean",
                "description", "종일 일정 여부. 사용자가 '하루종일' 같은 표현을 쓰면 true."
        ));
        properties.put("isPublic", Map.of(
                "type", "boolean",
                "description", "공개 여부. 미지정 시 false(개인 일정)."
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("title", "startAt", "endAt"));

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "create_calendar_event");
        tool.put("description", "사용자 캘린더에 새 일정을 등록한다. 사용자가 '일정 잡아줘/추가해줘/등록해줘' 등 " +
                "명시적으로 등록을 요청한 경우에만 호출. 단순 조회('내 일정 알려줘')에는 사용하지 말 것. " +
                "회사·사번·캘린더 ID 는 서버가 자동 적용하므로 input 에 넣지 말 것.");
        tool.put("input_schema", schema);
        return tool;
    }

    /**
     * prefill_approval_form tool spec. 서버 저장 없이 FE 가 결재 모달을 자동으로 띄우는 클라이언트 사이드 액션.
     * 결재선은 LLM 이 이름만 넘기면 서버가 검색으로 empId/dept/grade 까지 해결해 채운다.
     * 잔여휴가·근태 같은 검증 필요 데이터는 사용자가 모달에서 직접 입력 — LLM 환각 방지.
     */
    private Map<String, Object> buildPrefillApprovalFormTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("formCode", Map.of(
                "type", "string",
                "enum", List.of("VACATION_REQUEST", "OVERTIME_REQUEST"),
                "description", "양식 코드. VACATION_REQUEST=휴가신청, OVERTIME_REQUEST=초과근무신청. " +
                        "사용자 발화에 '휴가/연차/반차/병가' → VACATION_REQUEST, '초과근무/잔업/야근' → OVERTIME_REQUEST."
        ));
        properties.put("docTitle", Map.of(
                "type", "string",
                "description", "결재 문서 제목. 사용자가 명시한 게 없으면 생략(모달이 기본값 사용)."
        ));
        properties.put("reason", Map.of(
                "type", "string",
                "description", "신청 사유. 사용자 발화에서 추출 (예: '개인 사정', '연말 결산 마감 대응'). 미명시 시 생략."
        ));
        properties.put("approverNames", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "결재선에 자동으로 채울 결재자 이름 목록. 사용자가 '김영희 부장이랑 박철수 과장한테 올려줘' " +
                        "처럼 명시한 경우만 넣는다. 서버가 EMPLOYEE 검색으로 한 명을 찾아 결재선에 채움. " +
                        "사용자가 명시하지 않으면 절대 넣지 말 것 — 모달에서 사용자가 직접 선택."
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("formCode"));

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "prefill_approval_form");
        tool.put("description", "결재 양식 작성 모달을 자동으로 열고 사유/제목/결재선을 미리 채운다. " +
                "사용자가 '휴가 신청해줘', '초과근무 올려줘' 처럼 명시적으로 결재 기안을 요청한 경우에만 호출. " +
                "단순 조회('결재 양식 알려줘')에는 사용 금지. 날짜·잔여휴가·근태 같은 검증 필요 필드는 " +
                "사용자가 모달에서 직접 입력하므로 이 도구로 채우지 말 것.");
        tool.put("input_schema", schema);
        return tool;
    }

    /**
     * get_my_today_digest tool spec. 결재 대기 + 오늘 일정 BE composite.
     * input 인자 없음 (args:{}) — 본인 인증 컨텍스트만으로 동작. 추가 인자 받지 않음 단순화.
     */
    private Map<String, Object> buildTodayDigestTool() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "get_my_today_digest");
        tool.put("description", "오늘 사용자가 처리해야 할 핵심 업무를 한 번에 요약해 반환한다 — " +
                "(1) 본인이 결재해야 할 PENDING 결재 문서 top 5(긴급 우선), (2) 오늘 일정(회의·약속 등). " +
                "사용자가 '오늘 할 일', '오늘 다이제스트', '출근하면 뭐부터', '내 오늘 일정', " +
                "'결재 대기 + 오늘 회의', '오늘 뭐 봐야' 같은 종합 요약을 요청할 때만 호출. " +
                "단일 영역(결재만, 일정만) 조회는 search_documents 또는 다른 도구를 쓸 것. " +
                "회사·사번은 서버가 자동 적용하므로 input 에 넣지 말 것.");
        tool.put("input_schema", schema);
        return tool;
    }

    /** JSON 문자열 직접 조립 시 사용하는 최소 escape — backslash, quote, 제어문자만 처리. */
    private String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
