#!/usr/bin/env python3
"""
프롬프트 캐싱 적용 효과 벤치마크 — 발표용 산출물 생성.

동일 prefix(시스템 프롬프트 + 도구 4개) 로 N회 호출하면서:
  - 매 호출의 cache_creation/read/input 토큰 기록
  - 캐싱 ON/OFF 가정 시 등가 비용 누적 계산
  - 최종 절감률 + 가정 트래픽 환산 월 절감액 산출

사용법:
  export ANTHROPIC_API_KEY=sk-ant-...
  python3 benchmark_caching.py [--calls 30] [--out benchmark_result.json]

비용 모델 (Haiku 4.5 표준가):
  - 일반 입력 (input_tokens):              $1.00 / 1M  (1.0×)
  - 캐시 쓰기 (cache_creation_input_tokens): $1.25 / 1M  (1.25×)
  - 캐시 읽기 (cache_read_input_tokens):    $0.10 / 1M  (0.1×)
  - 출력 (output_tokens):                  $5.00 / 1M
"""
import argparse
import json
import os
import sys
import time
import urllib.request
import urllib.error
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from measure_prefix_tokens import SYSTEM_PROMPT, TOOLS as BASE_TOOLS, MODEL  # noqa: E402

API_KEY = os.environ.get("ANTHROPIC_API_KEY")
if not API_KEY:
    print("ERROR: ANTHROPIC_API_KEY 환경변수가 비어있습니다.", file=sys.stderr)
    sys.exit(1)

# 단가 (Haiku 4.5, USD per 1M tokens)
PRICE_INPUT = 1.00
PRICE_CACHE_WRITE = 1.25
PRICE_CACHE_READ = 0.10
PRICE_OUTPUT = 5.00

# CopilotService 와 동일한 cache_control 배치
SYSTEM_BLOCKS = [{"type": "text", "text": SYSTEM_PROMPT, "cache_control": {"type": "ephemeral"}}]
TOOLS = [dict(t) for t in BASE_TOOLS]
TOOLS[-1] = dict(TOOLS[-1])
TOOLS[-1]["cache_control"] = {"type": "ephemeral"}

# 비민감(SAFE 라우팅) 발화 풀 — 실제 코파일럿 사용자 패턴 시뮬레이션
USER_MESSAGES = [
    "황주완님 연락처 알려줘",
    "김민준님 어느 부서야?",
    "이서연 차장 찾아줘",
    "박지훈님 직급이 뭐야",
    "정수아 사원 정보 알려줘",
    "최도윤님 어디 소속이지",
    "한지우님 어느 팀이야",
    "윤서현 과장 찾아줘",
    "장하늘 대리 어느 부서",
    "임유준님 알려줘",
    "오늘 회의 일정 뭐야",
    "내일 일정 보여줘",
    "다음 주 월요일 일정",
    "이번 주 회의 뭐 있어",
    "다음 달 첫 주 일정",
    "결재 대기 중인 거 있어",
    "내가 봐야 할 결재 보여줘",
    "오늘 결재 처리할 거",
    "긴급 결재 있어",
    "어제 올라온 결재",
    "개발팀 정보 알려줘",
    "인사팀 누구 있어",
    "영업본부 사람들",
    "마케팅팀 인원",
    "인프라팀 알려줘",
    "재무팀 정보",
    "임원실 누구 있어",
    "오늘 할 일 뭐야",
    "오늘 다이제스트",
    "내 오늘 일정과 결재 보여줘",
]


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
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read())


def cost_usd(in_tok, cache_w, cache_r, out_tok):
    return (
        in_tok * PRICE_INPUT
        + cache_w * PRICE_CACHE_WRITE
        + cache_r * PRICE_CACHE_READ
        + out_tok * PRICE_OUTPUT
    ) / 1_000_000


def cost_no_cache(in_tok, cache_w, cache_r, out_tok):
    """캐싱 OFF 가정 — cache_w 와 cache_r 도 모두 풀가격(input)으로 환산."""
    total_input = in_tok + cache_w + cache_r
    return (total_input * PRICE_INPUT + out_tok * PRICE_OUTPUT) / 1_000_000


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--calls", type=int, default=30, help="총 호출 횟수 (기본 30)")
    ap.add_argument("--out", default=None, help="결과 JSON 저장 경로 (옵션)")
    args = ap.parse_args()

    n_calls = args.calls
    print(f"=== 벤치마크 시작 ===")
    print(f"모델           : {MODEL}")
    print(f"호출 횟수      : {n_calls}")
    print(f"단가 (USD/1M)  : input ${PRICE_INPUT}, cache_w ${PRICE_CACHE_WRITE}, cache_r ${PRICE_CACHE_READ}, output ${PRICE_OUTPUT}")
    print()
    print(f"{'#':>3} {'발화':<28} {'in':>6} {'cw':>6} {'cr':>6} {'out':>5} {'with$':>10} {'no$':>10} {'절감%':>6}")
    print("-" * 100)

    rows = []
    sum_in = sum_cw = sum_cr = sum_out = 0
    sum_cost_with = sum_cost_no = 0.0
    started_at = time.time()

    for i in range(n_calls):
        msg = USER_MESSAGES[i % len(USER_MESSAGES)]
        try:
            resp = call_messages(msg)
        except urllib.error.HTTPError as e:
            print(f"\n[#{i+1}] HTTP {e.code}: {e.read().decode('utf-8', errors='replace')}", file=sys.stderr)
            break

        u = resp.get("usage", {})
        in_tok = u.get("input_tokens", 0) or 0
        cache_w = u.get("cache_creation_input_tokens", 0) or 0
        cache_r = u.get("cache_read_input_tokens", 0) or 0
        out_tok = u.get("output_tokens", 0) or 0

        c_with = cost_usd(in_tok, cache_w, cache_r, out_tok)
        c_no = cost_no_cache(in_tok, cache_w, cache_r, out_tok)
        savings_pct = 100 * (1 - c_with / c_no) if c_no > 0 else 0

        sum_in += in_tok
        sum_cw += cache_w
        sum_cr += cache_r
        sum_out += out_tok
        sum_cost_with += c_with
        sum_cost_no += c_no

        rows.append({
            "n": i + 1, "msg": msg,
            "input": in_tok, "cache_write": cache_w, "cache_read": cache_r, "output": out_tok,
            "cost_with_cache_usd": c_with, "cost_no_cache_usd": c_no,
            "savings_pct": savings_pct,
        })

        msg_short = msg if len(msg) <= 26 else msg[:25] + "…"
        print(f"{i+1:>3} {msg_short:<28} {in_tok:>6,} {cache_w:>6,} {cache_r:>6,} {out_tok:>5,} {c_with:>10.6f} {c_no:>10.6f} {savings_pct:>5.1f}%")

    elapsed = time.time() - started_at
    n_done = len(rows)
    if n_done == 0:
        print("호출 실패 — 로그 확인 필요.")
        return

    total_savings_pct = 100 * (1 - sum_cost_with / sum_cost_no) if sum_cost_no > 0 else 0
    avg_cost_with = sum_cost_with / n_done
    avg_cost_no = sum_cost_no / n_done

    print("-" * 100)
    print(f"{'합계':>3} {'':<28} {sum_in:>6,} {sum_cw:>6,} {sum_cr:>6,} {sum_out:>5,} {sum_cost_with:>10.6f} {sum_cost_no:>10.6f} {total_savings_pct:>5.1f}%")

    # cold (cache_write 발생) vs warm (cache_read 만) 호출 분리 평균
    cold_rows = [r for r in rows if r["cache_write"] > 0]
    warm_rows = [r for r in rows if r["cache_write"] == 0 and r["cache_read"] > 0]

    print()
    print("=== 요약 ===")
    print(f"실행 시간              : {elapsed:.1f}초")
    print(f"호출 평균 응답 시간    : {elapsed / n_done:.2f}초")
    print(f"총 절감률              : {total_savings_pct:.1f}%")
    print(f"호출당 평균 비용 (캐싱 ON)  : ${avg_cost_with:.6f}")
    print(f"호출당 평균 비용 (캐싱 OFF) : ${avg_cost_no:.6f}")
    print(f"호출당 평균 절감액         : ${avg_cost_no - avg_cost_with:.6f}")

    if cold_rows:
        avg_cold_savings = sum(r["savings_pct"] for r in cold_rows) / len(cold_rows)
        print(f"Cold 호출 ({len(cold_rows)}건)        : 평균 절감 {avg_cold_savings:.1f}%")
    if warm_rows:
        avg_warm_savings = sum(r["savings_pct"] for r in warm_rows) / len(warm_rows)
        print(f"Warm 호출 ({len(warm_rows)}건)        : 평균 절감 {avg_warm_savings:.1f}%")

    print()
    print("=== 가정 트래픽 환산 (호출 단위, tool_use 루프 미포함) ===")
    for traffic_per_day in [100, 1000, 10000]:
        # 모든 호출 warm 가정 (운영 정착 후)
        if warm_rows:
            avg_warm_with = sum(r["cost_with_cache_usd"] for r in warm_rows) / len(warm_rows)
            avg_warm_no = sum(r["cost_no_cache_usd"] for r in warm_rows) / len(warm_rows)
            monthly_with = avg_warm_with * traffic_per_day * 30
            monthly_no = avg_warm_no * traffic_per_day * 30
            saved = monthly_no - monthly_with
            print(f"  {traffic_per_day:>6,} chat/일 → 월 ${monthly_no:>8.2f} (OFF) vs ${monthly_with:>8.2f} (ON), 절감 ${saved:>7.2f}")

    print()
    print("⚠️  주의:")
    print("   - 실제 /copilot/chat 은 chat 당 평균 2~3 iter tool_use 루프 → 위 절감액 비례 증가")
    print("   - chat 당 절감액 = 위 호출 단위 × 평균 iter 수")
    print("   - 민감 발화는 EXAONE 로 라우팅 — Anthropic 호출 비율은 별도 측정 필요")

    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump({
                "model": MODEL,
                "ran_at": datetime.now().isoformat(),
                "calls": n_done,
                "elapsed_sec": elapsed,
                "totals": {
                    "input_tokens": sum_in,
                    "cache_write_tokens": sum_cw,
                    "cache_read_tokens": sum_cr,
                    "output_tokens": sum_out,
                    "cost_with_cache_usd": sum_cost_with,
                    "cost_no_cache_usd": sum_cost_no,
                    "savings_pct": total_savings_pct,
                },
                "rows": rows,
            }, f, ensure_ascii=False, indent=2)
        print(f"결과 저장: {args.out}")


if __name__ == "__main__":
    main()
