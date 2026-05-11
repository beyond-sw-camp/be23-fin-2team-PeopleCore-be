# AI Copilot — 민감도 라우팅 + Tool-Use 기반 사내 어시스턴트

> PeopleCore의 AI Copilot은 **민감도 분류 → LLM 이중 라우팅 → Tool-Use 루프 → 인용·액션 응답** 으로 구성된 사내 업무 어시스턴트입니다. 일반 질의는 **Anthropic Claude(Haiku 4.5)**, 급여·평가·개인정보 등 민감 발화는 **사내 sLLM(EXAONE on Ollama)** 으로 자동 분기되어 외부 API로 민감정보가 흘러나가지 않습니다.

[← README로 돌아가기](../README.md)

---

## 1. 개요

| 항목 | 값 |
|------|---|
| 진입점 | `POST /search-service/copilot/chat` |
| 외부 LLM | Anthropic **claude-haiku-4-5-20251001** (일반 질의) |
| 사내 sLLM | **EXAONE 3.5 7.8B** on Ollama (민감 질의) |
| 라우팅 기준 | 발화 키워드 · 주민번호 정규식 · 페이지 컨텍스트(URL) |
| 도구(Tool) | `search_documents` · `today_digest` · `prefill_approval_form` · `create_calendar_event` |
| 검색 백엔드 | [Elasticsearch Hybrid Search (BM25 + kNN, RRF)](elasticsearch.md) |
| 프롬프트 캐싱 | Anthropic ephemeral 캐시 적용 — warm 호출 input 토큰 **약 79% 절감** |
| 컴플라이언스 | 민감 데이터 외부 LLM 차단, 도메인 권한 필터를 검색 단계에서 강제 |

---

## 2. 왜 이중 라우팅인가

| 도전 과제 | 해결 |
|----------|------|
| AI 활용은 늘리고 싶지만 **급여·평가·주민번호** 등은 외부 API에 보낼 수 없음 | 민감 발화는 **로컬 sLLM**에서만 처리 (Anthropic 호출 차단) |
| 모든 발화를 sLLM으로 돌리면 응답 품질·속도 저하 | 일반 질의는 **Claude Haiku 4.5** 로 빠른 고품질 응답 |
| 단순 키워드 차단만으로는 누락 위험 | **3중 분류**(URL 라우트 · 주민번호 정규식 · 키워드) 결합 |
| 반복되는 시스템 프롬프트로 비용 누적 | **Prompt Caching**으로 정적 블록·도구 정의를 캐시 |

---

## 3. 전체 흐름

```
사용자 발화 + pageContext
        │
        ▼
┌─────────────────────────────┐
│ SensitiveDetector.classify  │ ① URL 라우트 확인 (/salary, /payroll, /eval ...)
│                             │ ② 주민번호 정규식
│                             │ ③ 민감 키워드 매칭
└─────────────────────────────┘
        │
   ┌────┴────────────┐
   ▼ SAFE            ▼ SENSITIVE
Anthropic Claude    EXAONE (Ollama, EKS)
   │                  │
   ▼                  ▼
Tool-Use 루프      자연어 도구 카탈로그 + JSON 파싱
(search_documents,  (Ollama는 native tool calling 미지원
 today_digest, ...)  → 시스템 프롬프트로 도구 노출)
   │                  │
   └─────────┬────────┘
             ▼
   answer + citations + actions + toolCalls
```

---

## 4. 민감도 분류 (`SensitiveDetector`)

세 가지 신호를 OR 로 묶어 한 번이라도 걸리면 sLLM 경로로 보냅니다.

### 4.1 URL 라우트 기반 (가장 강한 신호)

`pageContext.route` 가 다음 prefix 로 시작하면 발화 내용 무관하게 **무조건 sLLM**:

| 라우트 | 의미 |
|--------|------|
| `/salary` | 개인 급여명세 |
| `/payroll` | 급여대장·보험·퇴직금·연차수당·연금 |
| `/eval` | 인사평가 (목표·결과·이의신청) |

**왜**: 사용자가 해당 페이지에서 "이거 정리해줘" 처럼 모호하게 말해도 화면 컨텍스트가 민감하면 sLLM으로 보내야 함.

### 4.2 주민번호 정규식

```regex
\b\d{6}-?\d{7}\b
```

발화 또는 첨부 컨텍스트에 RRN 형태가 있으면 sLLM.

### 4.3 민감 키워드

| 카테고리 | 키워드 예시 |
|---------|------------|
| 급여 | 급여, 연봉, 월급, 실수령, 급여명세서, 보너스, 성과급 |
| 인사평가 | 평가, 고과, 인사평가, 역량평가, 성과평가 |
| 개인 식별·금융 | 계좌, 계좌번호, 은행계좌, 급여계좌 |
| 이력·건강 | 징계, 병가사유, 휴직사유, 의료기록, 건강검진 |
| 본인 종합 조회 | overview 다이제스트(급여·평가·연차 묶음) |

`Verdict` 결과는 `(sensitive, reason, detail)` 로 반환되어 **로그·후속 운영 분석**에서 어떤 신호로 분기됐는지 추적 가능.

---

## 5. SAFE 경로 — Anthropic Claude + Tool-Use 루프

### 5.1 동작 시퀀스

```
user message
   ↓
Claude (tools 노출)
   ├─ stop_reason = "end_turn"  → 최종 답변 반환
   └─ stop_reason = "tool_use"  → 도구 실행 → tool_result 회신 → 재호출
                                  (최대 maxIterations=4 회)
```

도구 호출 결과는 같은 `user` 메시지 안에 `tool_result` 블록 묶음으로 회신해 다음 호출에서 Claude가 인용·요약합니다.

### 5.2 도구 카탈로그

| 도구 | 역할 |
|------|------|
| `search_documents` | 사내 문서·인물·부서·결재·일정 통합검색 (Hybrid BM25+kNN) |
| `today_digest` | 결재 대기 + 오늘 일정 묶음 조회 |
| `prefill_approval_form` | 결재 양식 자동 채움 후보 제시 |
| `create_calendar_event` | 일정 등록 액션 카드 생성 |

> Hybrid 검색 내부 동작은 [Elasticsearch 문서](elasticsearch.md#6-검색-모드) 참고.

### 5.3 Prompt Caching 설계

Anthropic Messages API의 `cache_control: ephemeral` 을 활용해 **변하지 않는 부분만 캐시 breakpoint** 로 지정합니다.

| 영역 | 캐시 여부 | 이유 |
|------|----------|------|
| 정적 시스템 블록 (템플릿 + 오늘 날짜) | ✅ 캐시 | 같은 일자 안에서 재사용 |
| `pageContext` 블록 | ❌ 미캐시 | 요청별 가변 — breakpoint 뒤에 두어 캐시 무효화 영향 없음 |
| 도구 정의 (마지막 도구에 부착) | ✅ 캐시 | render 순서가 `tools → system → messages` 이므로 마지막 도구 breakpoint 가 도구 4개 모두를 캐시에 포함 |

**측정 효과**: warm 호출 시 `cache_read_input_tokens` 비중이 커져 **input 토큰 약 79% 절감** (PoC 측정, 2026-05-06 적용).

상세 비교(런타임 시퀀스·단가·결과 요약): [README의 "프롬프트 캐싱 토큰 성능 비교"](../README.md#프롬프트-캐싱-토큰-성능-비교)

### 5.4 Usage 가시성

매 호출마다 다음 메트릭을 로그로 남깁니다:

```
[Copilot] usage: in={...}, out={...}, cacheRead={...}, cacheWrite={...} (model=claude-haiku-4-5-20251001)
```

→ 향후 토큰·비용 대시보드 구축 시 그대로 수집 소스로 사용 예정.

---

## 6. SENSITIVE 경로 — EXAONE on Ollama

### 6.1 배포 형상

| 항목 | 값 |
|------|---|
| 모델 | `exaone3.5:7.8b` |
| 배포 | EKS `default` 네임스페이스 (2026-05-06 배포) |
| 호출 엔드포인트 | `http://ollama:11434` (K8s DNS) / `http://localhost:11434` (로컬) |
| 컨텍스트 길이 | `num_ctx=8192` |

### 6.2 도구 호출 — 자연어 카탈로그 우회

EXAONE은 **Ollama tool calling을 native 지원하지 않으므로**, 시스템 프롬프트에 도구 카탈로그를 자연어로 주입하고 모델이 **JSON 형태로 도구 호출을 출력**하면 서비스가 파싱·실행합니다.

```
system: 당신은 사내 어시스턴트입니다. 다음 도구를 사용할 수 있습니다.
        - search_documents(keyword, type)
        ...
        도구를 호출할 때는 JSON 형태로 출력하세요.
user: <발화>
assistant: {"tool":"search_documents","input":{"keyword":"홍길동","type":"EMPLOYEE"}}
   ↓ (서비스가 파싱 → 실행 → tool_result 를 다시 user 로 회신)
```

JSON 파싱 실패 시에는 일반 답변으로 처리하고 경고 로그 (`EXAONE tool call JSON parse failed`) 를 남겨 후속 분석.

### 6.3 SAFE 경로와 동등한 응답 스키마

EXAONE 경로도 `answer + citations + actions + toolCalls` 동일 구조로 반환해 **프론트엔드는 라우팅 결과를 의식할 필요가 없습니다**.

---

## 7. 멀티테넌트·권한 보장

도구 실행 시 호출자(JWT에서 추출)의 `companyId`, `empId`, `role` 을 **검색 쿼리에 강제 주입**합니다. Copilot이 다른 회사·다른 사람의 데이터를 검색하도록 유도되는 프롬프트 인젝션을 차단.

| 단계 | 강제 적용 |
|------|----------|
| Copilot 진입 | Gateway에서 JWT 검증 → header로 `companyId`, `empId`, `role` 전달 |
| 도구 실행 | `executeTool(...)` 시그니처에 `companyId/empId/role` 필수 |
| ES 쿼리 | `companyId` term filter + 권한 OR 필터 (관리자 외) |

상세 권한 규칙: [Elasticsearch 문서 §7](elasticsearch.md#7-멀티테넌트--권한-필터링)

---

## 8. 응답 스키마

```json
{
  "answer": "...",
  "citations": [
    { "type": "EMPLOYEE", "sourceId": "12", "title": "홍길동", ... }
  ],
  "actions": [
    { "type": "OPEN_APPROVAL_FORM", "payload": { ... } }
  ],
  "toolCalls": [
    { "name": "search_documents", "input": {...}, "ok": true, "tookMs": 42 }
  ]
}
```

| 필드 | 용도 |
|------|------|
| `answer` | 자연어 답변 본문 |
| `citations` | 검색 결과 근거 카드 (클릭 시 상세 페이지로 이동) |
| `actions` | 결재 양식 채우기·일정 등록 등 **사용자 확인 후 실행되는 액션** |
| `toolCalls` | 디버깅·관측용 도구 호출 이력 |

---

## 9. 설정 키 (`application.yml`)

| 키 | 기본값 | 의미 |
|-----|-------|------|
| `anthropic.api-key` | (필수) | Anthropic API 키 |
| `anthropic.base-url` | `https://api.anthropic.com/v1` | API 베이스 |
| `anthropic.model` | `claude-haiku-4-5-20251001` | SAFE 경로 모델 |
| `anthropic.max-tokens` | `1024` | 응답 max tokens |
| `anthropic.max-tool-iterations` | `4` | Tool-Use 루프 최대 반복 |
| `ollama.base-url` | `http://localhost:11434` | EXAONE 엔드포인트 (운영: `http://ollama:11434`) |
| `ollama.model` | `exaone3.5:7.8b` | sLLM 모델 |
| `ollama.read-timeout-seconds` | `120` | 응답 타임아웃 |
| `ollama.num-ctx` | `8192` | EXAONE context window |

---

## 10. 한계 및 향후 개선

| 영역 | 현재 | 개선 방향 |
|------|------|----------|
| 민감도 분류 | 키워드·정규식·라우트 룰 기반 | **분류 모델(Classifier)** 도입으로 오탐/미탐 감소 |
| 운영 가시성 | usage 로그만 남김 | **토큰·캐시 적중률·비용 부서별 대시보드** 구축 |
| EXAONE 도구 호출 | 자연어 카탈로그 + JSON 파싱 | function calling 지원 모델로 교체 또는 GBNF 파서 도입 |
| 도구 다양성 | 4종 (검색·다이제스트·결재·일정) | 휴가 신청·근태 조회·문서 작성 등으로 확장 |
| 답변 품질 측정 | 수동 검수 | golden set 기반 자동 평가 루프 |

---

## 11. 관련 코드·파일

| 항목 | 위치 |
|------|------|
| Copilot 컨트롤러 | `search-service/src/main/java/com/peoplecore/controller/CopilotController.java` |
| Orchestrator | `search-service/src/main/java/com/peoplecore/llm/CopilotService.java` |
| 민감도 분류 | `search-service/src/main/java/com/peoplecore/llm/SensitiveDetector.java` |
| Anthropic 클라이언트 | `search-service/src/main/java/com/peoplecore/llm/AnthropicClient.java` |
| Ollama 클라이언트 | `search-service/src/main/java/com/peoplecore/llm/OllamaClient.java` |
| 검색 백엔드 (도구 구현) | `search-service/src/main/java/com/peoplecore/service/SearchService.java` |

---

[← README로 돌아가기](../README.md) · [Elasticsearch 문서 →](elasticsearch.md)
