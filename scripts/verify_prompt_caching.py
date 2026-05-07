#!/usr/bin/env python3
"""
프롬프트 캐싱 동작 검증 — search-service 띄우지 않고 Anthropic API 만으로 확인.

CopilotService 가 보내는 것과 동일한 system blocks + tools 를 만들고,
cache_control 부착해서 5초 간격으로 두 번 호출한다.

기대 결과:
  1차 호출: cache_creation_input_tokens > 0, cache_read_input_tokens == 0
  2차 호출: cache_creation_input_tokens == 0, cache_read_input_tokens > 0  ← 적중!

사용법:
  export ANTHROPIC_API_KEY=sk-ant-...
  python3 verify_prompt_caching.py
"""
import json
import os
import sys
import time
import urllib.request
import urllib.error

# measure_prefix_tokens 와 동일한 SYSTEM_PROMPT, TOOLS 재사용 — 단일 진실 원천(SOT).
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from measure_prefix_tokens import SYSTEM_PROMPT, TOOLS as BASE_TOOLS, MODEL  # noqa: E402

API_KEY = os.environ.get("ANTHROPIC_API_KEY")
if not API_KEY:
    print("ERROR: ANTHROPIC_API_KEY 환경변수가 비어있습니다.", file=sys.stderr)
    sys.exit(1)

# CopilotService.buildSystemBlocks() 와 동일한 구조 — 정적 블록에 cache_control
SYSTEM_BLOCKS = [
    {
        "type": "text",
        "text": SYSTEM_PROMPT,
        "cache_control": {"type": "ephemeral"},
    }
]

# 도구 4개 — 마지막 도구에만 cache_control (앞 3개 자동 포함)
# CopilotService.chat() 의 cache_control 부착 로직과 동일.
TOOLS = [dict(t) for t in BASE_TOOLS]
TOOLS[-1] = dict(TOOLS[-1])
TOOLS[-1]["cache_control"] = {"type": "ephemeral"}


def call_messages(user_text):
    body = {
        "model": MODEL,
        "max_tokens": 256,
        "system": SYSTEM_BLOCKS,
        "tools": TOOLS,
        "messages": [{"role": "user", "content": user_text}],
    }
    req = urllib.request.Request(
        "https://api.anthropic.com/v1/messages",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "x-api-key": API_KEY,
            "anthropic-version": "2023-06-01",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code}: {e.read().decode('utf-8', errors='replace')}", file=sys.stderr)
        sys.exit(2)


def print_usage(label, resp):
    u = resp.get("usage", {})
    in_tok = u.get("input_tokens", 0)
    out_tok = u.get("output_tokens", 0)
    cache_w = u.get("cache_creation_input_tokens", 0)
    cache_r = u.get("cache_read_input_tokens", 0)
    total_in = in_tok + (cache_w or 0) + (cache_r or 0)
    print(f"  {label}")
    print(f"    input_tokens                  (풀가격, 1.0x) : {in_tok:>6,}")
    print(f"    cache_creation_input_tokens   (캐시 쓰기, 1.25x): {cache_w:>6,}")
    print(f"    cache_read_input_tokens       (캐시 읽기, 0.1x) : {cache_r:>6,}")
    print(f"    합계 입력 토큰                                   : {total_in:>6,}")


def main():
    user_text = "황주완님 연락처 알려줘"

    print("=== 1차 호출 (캐시 비어있음 → cache_creation 발생 예상) ===")
    r1 = call_messages(user_text)
    print_usage("1차", r1)

    print("\n  → 5초 대기 (캐시 entry 가 readable 상태가 되기까지)")
    time.sleep(5)

    print("\n=== 2차 호출 (동일 prefix → cache_read 발생 예상) ===")
    r2 = call_messages(user_text)
    print_usage("2차", r2)

    cache_w_1 = r1["usage"].get("cache_creation_input_tokens", 0) or 0
    cache_r_2 = r2["usage"].get("cache_read_input_tokens", 0) or 0

    print("\n=== 판정 ===")
    if cache_w_1 > 0 and cache_r_2 > 0:
        savings_pct = 100 * (1 - 0.1)
        print(f"  ✅ 캐싱 정상 동작.")
        print(f"     1차 cache_write {cache_w_1:,} 토큰 → 2차 cache_read {cache_r_2:,} 토큰 적중")
        print(f"     2차 호출의 prefix 비용은 표준가 대비 약 90% 할인 적용됨")
    elif cache_w_1 == 0:
        print(f"  ❌ 1차에 cache_creation 이 0 — cache_control 이 무시됨.")
        print(f"     prefix 가 모델 임계값 미달이거나 요청 본문 형식이 잘못됨.")
    elif cache_r_2 == 0:
        print(f"  ⚠️  1차 cache_write 는 발생했으나 2차 cache_read 가 0.")
        print(f"     prefix 바이트가 미세하게 달라졌을 가능성 (silent invalidator).")
    else:
        print(f"  ⚠️  예상 외 상태. usage 직접 확인 필요.")


if __name__ == "__main__":
    main()
