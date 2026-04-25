#!/usr/bin/env bash
set -euo pipefail

# /search 엔드포인트 BM25 키워드 검색 p50/p95/p99 측정.
# 사용법: ./bench-search-latency.sh [N] [KEYWORD]
#   N        요청 수 (기본 100)
#   KEYWORD  검색어 (기본 "테스트")
# 환경변수: PORT, COMPANY_ID, EMP_ID, ROLE 로 오버라이드 가능.

N=${1:-100}
KEYWORD=${2:-테스트}
WARMUP=${WARMUP:-10}

COMPANY_ID=${COMPANY_ID:-a0000001-0000-0000-0000-000000000001}
EMP_ID=${EMP_ID:-1}
ROLE=${ROLE:-EMPLOYEE}

if [[ -z "${PORT:-}" ]]; then
  PORT=$(curl -s http://localhost:8761/eureka/apps/SEARCH-SERVICE \
    | grep -oE '<port[^>]*>[0-9]+' | head -1 | grep -oE '[0-9]+')
fi
if [[ -z "${PORT:-}" ]]; then
  echo "ERROR: search-service port not found via Eureka. Set PORT env var." >&2
  exit 1
fi

URL="http://localhost:${PORT}/search?keyword=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "$KEYWORD")&size=10"
HEADERS=(-H "X-User-Company: $COMPANY_ID" -H "X-User-Id: $EMP_ID" -H "X-User-Role: $ROLE")

echo "target  : $URL"
echo "headers : company=$COMPANY_ID emp=$EMP_ID role=$ROLE"
echo "warmup  : $WARMUP requests"
echo "measure : $N requests"
echo

for i in $(seq 1 "$WARMUP"); do
  curl -s -o /dev/null "${HEADERS[@]}" "$URL" || true
done

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

for i in $(seq 1 "$N"); do
  curl -s -o /dev/null -w "%{time_total}\n" "${HEADERS[@]}" "$URL" >> "$TMP"
done

python3 - "$TMP" <<'PY'
import sys
nums = sorted(float(x.strip()) * 1000 for x in open(sys.argv[1]) if x.strip())
n = len(nums)
def pct(p): return nums[min(int(n * p), n - 1)]
print(f"count : {n}")
print(f"min   : {min(nums):7.1f} ms")
print(f"mean  : {sum(nums)/n:7.1f} ms")
print(f"p50   : {pct(0.50):7.1f} ms")
print(f"p95   : {pct(0.95):7.1f} ms")
print(f"p99   : {pct(0.99):7.1f} ms")
print(f"max   : {max(nums):7.1f} ms")
print()
print("PASS p95<100ms" if pct(0.95) < 100 else f"FAIL p95={pct(0.95):.1f}ms (limit 100ms)")
PY
