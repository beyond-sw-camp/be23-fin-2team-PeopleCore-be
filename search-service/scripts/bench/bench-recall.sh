#!/usr/bin/env bash
set -euo pipefail

# Recall@10 측정: BM25(/search) vs Hybrid(/search/hybrid) 비교.
# 사용법: ./bench-recall.sh [GOLDENSET_PATH]
#   GOLDENSET_PATH  골든셋 JSON 경로 (기본 ./goldenset.json)
# 환경변수: PORT 로 search-service 포트 오버라이드 가능.

GOLDEN=${1:-"$(dirname "$0")/goldenset.json"}

if [[ ! -f "$GOLDEN" ]]; then
  echo "ERROR: goldenset not found: $GOLDEN" >&2
  exit 1
fi

if [[ -z "${PORT:-}" ]]; then
  PORT=$(curl -s http://localhost:8761/eureka/apps/SEARCH-SERVICE \
    | grep -oE '<port[^>]*>[0-9]+' | head -1 | grep -oE '[0-9]+')
fi
if [[ -z "${PORT:-}" ]]; then
  echo "ERROR: search-service port not found via Eureka. Set PORT env var." >&2
  exit 1
fi

echo "search-service : http://localhost:${PORT}"
echo "goldenset      : $GOLDEN"
echo

PORT="$PORT" GOLDEN="$GOLDEN" python3 - <<'PY'
import json
import os
import sys
import urllib.parse
import urllib.request
from collections import defaultdict

PORT = os.environ["PORT"]
GOLDEN = os.environ["GOLDEN"]
BASE = f"http://localhost:{PORT}"

with open(GOLDEN, "r", encoding="utf-8") as f:
    spec = json.load(f)

meta = spec["_meta"]
queries = spec["queries"]

COMPANY_ID = meta["companyId"]
USER_ID = str(meta["userId"])
ROLE = meta["role"]
SIZE = 10  # Recall@10

HEADERS = {
    "X-User-Company": COMPANY_ID,
    "X-User-Id": USER_ID,
    "X-User-Role": ROLE,
}

def call(path, params):
    qs = urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
    url = f"{BASE}{path}?{qs}"
    req = urllib.request.Request(url, headers=HEADERS, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        return data
    except Exception as e:
        return {"_error": str(e), "items": []}

def top_ids(resp, k=SIZE):
    items = resp.get("items", []) or []
    return [it.get("id") for it in items[:k]]

def recall_at_k(retrieved, expected):
    if not expected:
        return 1.0
    hit = len(set(retrieved) & set(expected))
    return hit / len(expected)

# 결과 누적
overall = {"BM25": [], "HYBRID": []}
by_cat = defaultdict(lambda: {"BM25": [], "HYBRID": []})
rows = []

for q in queries:
    qid = q["id"]
    keyword = q["query"]
    qtype = q.get("type")
    expected = q.get("expectedIds", [])
    cat = q.get("category", "?")

    params = {"keyword": keyword, "size": SIZE}
    if qtype:
        params["type"] = qtype

    bm25_resp = call("/search", params)
    hybrid_resp = call("/search/hybrid", params)

    bm25_ids = top_ids(bm25_resp)
    hyb_ids = top_ids(hybrid_resp)

    bm25_r = recall_at_k(bm25_ids, expected)
    hyb_r = recall_at_k(hyb_ids, expected)

    overall["BM25"].append(bm25_r)
    overall["HYBRID"].append(hyb_r)
    by_cat[cat]["BM25"].append(bm25_r)
    by_cat[cat]["HYBRID"].append(hyb_r)

    bm25_err = bm25_resp.get("_error")
    hyb_err = hybrid_resp.get("_error")

    rows.append({
        "id": qid,
        "category": cat,
        "type": qtype or "ALL",
        "query": keyword,
        "expected_n": len(expected),
        "bm25_recall": bm25_r,
        "hybrid_recall": hyb_r,
        "delta": hyb_r - bm25_r,
        "bm25_top": bm25_ids,
        "hybrid_top": hyb_ids,
        "expected": expected,
        "errors": [e for e in (bm25_err, hyb_err) if e],
    })

# 출력: 테이블
def fmt_pct(x):
    return f"{x*100:5.1f}%"

print("=" * 96)
print(f"{'ID':<5}{'CAT':<6}{'TYPE':<12}{'QUERY':<22}{'EXP':>4}  {'BM25':>7}  {'HYB':>7}  {'Δ':>7}")
print("-" * 96)
for r in rows:
    delta = r["delta"]
    sign = "+" if delta > 0 else ("-" if delta < 0 else " ")
    qstr = r["query"]
    if len(qstr) > 20:
        qstr = qstr[:19] + "…"
    print(f"{r['id']:<5}{r['category']:<6}{r['type']:<12}{qstr:<22}{r['expected_n']:>4}  "
          f"{fmt_pct(r['bm25_recall']):>7}  {fmt_pct(r['hybrid_recall']):>7}  "
          f"{sign}{fmt_pct(abs(delta)):>6}")
    if r["errors"]:
        print(f"     ERR: {r['errors']}")
print("=" * 96)

# 카테고리별 요약
def avg(xs):
    return sum(xs) / len(xs) if xs else 0.0

print()
print("=== Category Summary ===")
print(f"{'CAT':<8}{'N':>5}  {'BM25':>10}  {'HYBRID':>10}  {'Δ':>8}")
print("-" * 50)
for cat in ["BM25", "KNN", "BOTH"]:
    if cat not in by_cat:
        continue
    n = len(by_cat[cat]["BM25"])
    a = avg(by_cat[cat]["BM25"])
    b = avg(by_cat[cat]["HYBRID"])
    d = b - a
    sign = "+" if d > 0 else ("-" if d < 0 else " ")
    print(f"{cat:<8}{n:>5}  {fmt_pct(a):>10}  {fmt_pct(b):>10}  {sign}{fmt_pct(abs(d)):>7}")

# 전체 요약
n = len(overall["BM25"])
a = avg(overall["BM25"])
b = avg(overall["HYBRID"])
d = b - a
sign = "+" if d > 0 else ("-" if d < 0 else " ")
print("-" * 50)
print(f"{'OVERALL':<8}{n:>5}  {fmt_pct(a):>10}  {fmt_pct(b):>10}  {sign}{fmt_pct(abs(d)):>7}")
print()

# 합격 라인: hybrid가 BM25보다 같거나 높아야 함 + KNN 카테고리에서 명확한 개선
knn_a = avg(by_cat["KNN"]["BM25"]) if "KNN" in by_cat else 0
knn_b = avg(by_cat["KNN"]["HYBRID"]) if "KNN" in by_cat else 0
overall_ok = b >= a - 0.01  # 1%p 허용 오차
knn_ok = knn_b >= knn_a  # KNN 카테고리는 동등 이상
verdict = "PASS" if (overall_ok and knn_ok) else "REVIEW"
print(f"VERDICT : {verdict}")
print(f"  - overall hybrid({b*100:.1f}%) vs bm25({a*100:.1f}%): "
      f"{'OK' if overall_ok else 'REGRESSION'}")
print(f"  - KNN cat hybrid({knn_b*100:.1f}%) vs bm25({knn_a*100:.1f}%): "
      f"{'OK' if knn_ok else 'REGRESSION'}")

# 상세 JSON 저장
out_path = os.path.join(os.path.dirname(GOLDEN), "recall-result.json")
with open(out_path, "w", encoding="utf-8") as f:
    json.dump({
        "meta": {
            "port": PORT,
            "size": SIZE,
            "n": n,
        },
        "overall": {
            "bm25_avg": a,
            "hybrid_avg": b,
            "delta": d,
        },
        "by_category": {
            cat: {
                "n": len(v["BM25"]),
                "bm25_avg": avg(v["BM25"]),
                "hybrid_avg": avg(v["HYBRID"]),
                "delta": avg(v["HYBRID"]) - avg(v["BM25"]),
            } for cat, v in by_cat.items()
        },
        "rows": rows,
    }, f, ensure_ascii=False, indent=2)
print(f"\ndetail  : {out_path}")
PY
