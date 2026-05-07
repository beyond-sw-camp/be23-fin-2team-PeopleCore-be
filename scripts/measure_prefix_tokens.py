#!/usr/bin/env python3
"""
Anthropic prompt caching 적용 전 prefix 토큰 측정 스크립트.

실제 CopilotService.java가 Anthropic /v1/messages 로 보내는 것과 동일한
system prompt + tools 를 재현해 /v1/messages/count_tokens 에 던져서
prefix 토큰 수를 측정한다.

사용법:
  export ANTHROPIC_API_KEY=sk-ant-...
  python3 measure_prefix_tokens.py

판정 기준:
  - Haiku 4.5 / Opus 4.6+ : prefix >= 4096 토큰이어야 캐싱 적용됨
  - Sonnet 4.6           : prefix >= 2048 토큰이어야 캐싱 적용됨
"""
import json
import os
import sys
import urllib.request
import urllib.error
from datetime import date

API_KEY = os.environ.get("ANTHROPIC_API_KEY")
if not API_KEY:
    print("ERROR: ANTHROPIC_API_KEY 환경변수가 비어있습니다.", file=sys.stderr)
    sys.exit(1)

# 모델 — application-local.yml 의 anthropic.model 과 동일
MODEL = "claude-haiku-4-5-20251001"

# 오늘 날짜 — CopilotService 가 LocalDate.now() 로 채우는 것과 동일 포맷
WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"]
today = date.today()
today_str = f"{today.year}년 {today.month}월 {today.day}일 ({WEEKDAYS[today.weekday()]})"

# CopilotService.SYSTEM_PROMPT_TEMPLATE 와 동일 (검색용 비민감 경로)
SYSTEM_PROMPT = f"""당신은 PeopleCore 사내 검색·실행 코파일럿입니다. 사용자의 질문/요청에 답하기 위해
제공된 도구를 적극 활용하세요. 오늘 날짜는 {today_str} 입니다.

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
"""

# CopilotService.buildSearchTool() 과 동일
SEARCH_TOOL = {
    "name": "search_documents",
    "description": "PeopleCore 사내 통합검색(BM25+kNN 하이브리드). 직원/부서/결재/일정을 한 번에 조회. "
                   "권한·회사 필터는 서버가 자동 적용하므로 절대 input 에 넣지 말 것.",
    "input_schema": {
        "type": "object",
        "properties": {
            "keyword": {
                "type": "string",
                "description": "검색어. 사용자 질문에서 핵심 명사·키워드를 추출해 넣는다. 한국어 그대로."
            },
            "type": {
                "type": "string",
                "enum": ["EMPLOYEE", "DEPARTMENT", "APPROVAL", "CALENDAR"],
                "description": "결과를 특정 도메인으로 좁히고 싶을 때만 지정. 모르겠으면 생략."
            },
            "size": {
                "type": "integer",
                "minimum": 1,
                "maximum": 10,
                "description": "반환할 결과 개수. 기본 5."
            }
        },
        "required": ["keyword"]
    }
}

# CopilotService.buildCreateCalendarEventTool() 과 동일
CALENDAR_TOOL = {
    "name": "create_calendar_event",
    "description": "사용자 캘린더에 새 일정을 등록한다. 사용자가 '일정 잡아줘/추가해줘/등록해줘' 등 "
                   "명시적으로 등록을 요청한 경우에만 호출. 단순 조회('내 일정 알려줘')에는 사용하지 말 것. "
                   "회사·사번·캘린더 ID 는 서버가 자동 적용하므로 input 에 넣지 말 것.",
    "input_schema": {
        "type": "object",
        "properties": {
            "title": {"type": "string", "description": "일정 제목. 사용자 발화에서 핵심을 추출."},
            "startAt": {"type": "string", "description": "시작 시각, ISO 8601 LocalDateTime 형식 (예: 2026-04-28T14:00:00). 시간 미지정 시 09:00 기본."},
            "endAt": {"type": "string", "description": "종료 시각, ISO 8601 LocalDateTime 형식. 미지정 시 startAt + 1시간."},
            "description": {"type": "string", "description": "메모/상세. 선택."},
            "location": {"type": "string", "description": "장소. 선택."},
            "isAllDay": {"type": "boolean", "description": "종일 일정 여부. 사용자가 '하루종일' 같은 표현을 쓰면 true."},
            "isPublic": {"type": "boolean", "description": "공개 여부. 미지정 시 false(개인 일정)."}
        },
        "required": ["title", "startAt", "endAt"]
    }
}

# CopilotService.buildPrefillApprovalFormTool() 과 동일
APPROVAL_TOOL = {
    "name": "prefill_approval_form",
    "description": "결재 양식 작성 모달을 자동으로 열고 사유/제목/결재선을 미리 채운다. "
                   "사용자가 '휴가 신청해줘', '초과근무 올려줘' 처럼 명시적으로 결재 기안을 요청한 경우에만 호출. "
                   "단순 조회('결재 양식 알려줘')에는 사용 금지. 날짜·잔여휴가·근태 같은 검증 필요 필드는 "
                   "사용자가 모달에서 직접 입력하므로 이 도구로 채우지 말 것.",
    "input_schema": {
        "type": "object",
        "properties": {
            "formCode": {
                "type": "string",
                "enum": ["VACATION_REQUEST", "OVERTIME_REQUEST"],
                "description": "양식 코드. VACATION_REQUEST=휴가신청, OVERTIME_REQUEST=초과근무신청. "
                               "사용자 발화에 '휴가/연차/반차/병가' → VACATION_REQUEST, '초과근무/잔업/야근' → OVERTIME_REQUEST."
            },
            "docTitle": {"type": "string", "description": "결재 문서 제목. 사용자가 명시한 게 없으면 생략(모달이 기본값 사용)."},
            "reason": {"type": "string", "description": "신청 사유. 사용자 발화에서 추출 (예: '개인 사정', '연말 결산 마감 대응'). 미명시 시 생략."},
            "approverNames": {
                "type": "array",
                "items": {"type": "string"},
                "description": "결재선에 자동으로 채울 결재자 이름 목록. 사용자가 '김영희 부장이랑 박철수 과장한테 올려줘' "
                               "처럼 명시한 경우만 넣는다. 서버가 EMPLOYEE 검색으로 한 명을 찾아 결재선에 채움. "
                               "사용자가 명시하지 않으면 절대 넣지 말 것 — 모달에서 사용자가 직접 선택."
            }
        },
        "required": ["formCode"]
    }
}

# CopilotService.buildTodayDigestTool() 과 동일
DIGEST_TOOL = {
    "name": "get_my_today_digest",
    "description": "오늘 사용자가 처리해야 할 핵심 업무를 한 번에 요약해 반환한다 — "
                   "(1) 본인이 결재해야 할 PENDING 결재 문서 top 5(긴급 우선), (2) 오늘 일정(회의·약속 등). "
                   "사용자가 '오늘 할 일', '오늘 다이제스트', '출근하면 뭐부터', '내 오늘 일정', "
                   "'결재 대기 + 오늘 회의', '오늘 뭐 봐야' 같은 종합 요약을 요청할 때만 호출. "
                   "단일 영역(결재만, 일정만) 조회는 search_documents 또는 다른 도구를 쓸 것. "
                   "회사·사번은 서버가 자동 적용하므로 input 에 넣지 말 것.",
    "input_schema": {
        "type": "object",
        "properties": {}
    }
}

TOOLS = [SEARCH_TOOL, CALENDAR_TOOL, APPROVAL_TOOL, DIGEST_TOOL]

# 샘플 사용자 발화 — prefix 측정에는 영향 적음 (작아서)
SAMPLE_USER_MESSAGES = [
    "황주완님 연락처 알려줘",                       # 짧은 검색
    "내일 오후 3시에 팀 회의 잡아줘",                # 일정 등록
    "휴가 신청해줘. 김영희 부장한테 올려줘",         # 결재 신청
    "오늘 할 일 알려줘",                            # 다이제스트
]

def count_tokens(messages, tools, system):
    body = {
        "model": MODEL,
        "messages": messages,
        "tools": tools,
        "system": system,
    }
    req = urllib.request.Request(
        "https://api.anthropic.com/v1/messages/count_tokens",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "x-api-key": API_KEY,
            "anthropic-version": "2023-06-01",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code}: {e.read().decode('utf-8', errors='replace')}", file=sys.stderr)
        sys.exit(2)


def main():
    print(f"모델: {MODEL}")
    print(f"오늘 날짜: {today_str}")
    print(f"system prompt 길이: {len(SYSTEM_PROMPT):,} 자")
    print(f"도구 수: {len(TOOLS)}개")
    print()

    # 1) 도구만 측정 (system, messages 없이는 API가 거절하니 최소 메시지 + 빈 system 으로 분리 측정)
    only_tools = count_tokens(
        messages=[{"role": "user", "content": "."}],
        tools=TOOLS,
        system="",
    )
    only_system = count_tokens(
        messages=[{"role": "user", "content": "."}],
        tools=[],
        system=SYSTEM_PROMPT,
    )
    only_baseline = count_tokens(
        messages=[{"role": "user", "content": "."}],
        tools=[],
        system="",
    )

    tools_only = only_tools["input_tokens"] - only_baseline["input_tokens"]
    system_only = only_system["input_tokens"] - only_baseline["input_tokens"]

    print("=== 컴포넌트별 토큰 (베이스라인 차감) ===")
    print(f"  도구 4개 정의           : {tools_only:>5,} 토큰")
    print(f"  시스템 프롬프트         : {system_only:>5,} 토큰")
    print(f"  베이스라인(빈 user '.') : {only_baseline['input_tokens']:>5,} 토큰")
    print()

    # 2) 실제 발화별 전체 prefix (= 캐시 대상이 될 부분)
    print("=== 발화별 전체 입력 토큰 (도구 + 시스템 + history + user) ===")
    for utterance in SAMPLE_USER_MESSAGES:
        result = count_tokens(
            messages=[{"role": "user", "content": utterance}],
            tools=TOOLS,
            system=SYSTEM_PROMPT,
        )
        total = result["input_tokens"]
        print(f"  '{utterance[:40]}'... → {total:,} 토큰")

    # 3) 캐시 대상 prefix = 도구 + 시스템 (user 메시지는 매번 바뀌므로 제외)
    cacheable_prefix = tools_only + system_only
    print()
    print("=== 캐시 적용 대상 prefix (tools + system) ===")
    print(f"  합계: {cacheable_prefix:,} 토큰")
    print()

    print("=== 모델별 임계값 판정 ===")
    thresholds = [
        ("Haiku 4.5 (현재 설정)", 4096, "claude-haiku-4-5-20251001"),
        ("Sonnet 4.6",           2048, "claude-sonnet-4-6"),
        ("Opus 4.6 / 4.7",       4096, "claude-opus-4-6"),
    ]
    for name, threshold, _ in thresholds:
        ok = "✅ 캐싱 적용됨" if cacheable_prefix >= threshold else "❌ 임계값 미달 — 캐싱 silently 동작 안 함"
        margin = cacheable_prefix - threshold
        sign = "+" if margin >= 0 else ""
        print(f"  {name:30s} 임계값 {threshold:,} : {ok}  (여유 {sign}{margin:,})")

    print()
    if cacheable_prefix < 4096:
        print("⚠️  현재 prefix 가 Haiku 4.5 임계값(4096)에 미달합니다.")
        gap = 4096 - cacheable_prefix
        print(f"   필요한 추가량: {gap:,} 토큰")
        print("   옵션:")
        print("     1) 시스템 프롬프트에 few-shot 예시 추가 → 자연스럽게 임계값 도달 + 품질↑")
        print("     2) Sonnet 4.6 (claude-sonnet-4-6) 으로 변경 → 임계값 2,048 로 낮음 (단가 3배)")
        print("     3) 캐싱 적용 보류 — 다른 최적화 우선")
    else:
        print("✅ prefix 가 Haiku 4.5 임계값을 만족합니다. 캐싱 코드 작업 진행 가능합니다.")


if __name__ == "__main__":
    main()
