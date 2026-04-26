#!/usr/bin/env bash
set -euo pipefail

# CDC 지연(approval-service DB 작성 → search-service ES 색인) 측정.
# 동작: ENTER 시점의 APPROVAL doc count(baseline)를 기록 → 새 결재 1건이 ES에 색인되어
#       count가 증가하는 첫 폴링까지의 elapsed 출력.
# 사용법:
#   1) 터미널 A: ./bench-cdc.sh
#   2) 결재 작성 화면 준비 → 제출 직전에 ENTER → 즉시 결재 제출.
#   3) 색인되면 elapsed 출력. 30초 안에 색인 안 되면 FAIL.

POLL_INTERVAL_MS=${POLL_INTERVAL_MS:-200}
TIMEOUT_SEC=${TIMEOUT_SEC:-30}
ES_URL=${ES_URL:-http://localhost:9200}
INDEX=${INDEX:-unified_search}
TYPE=${TYPE:-APPROVAL}

count_docs() {
  curl -s "${ES_URL}/${INDEX}/_count" -H 'Content-Type: application/json' \
    -d "{\"query\":{\"term\":{\"type\":\"${TYPE}\"}}}" \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('count',0))"
}

BASELINE=$(count_docs)
echo "es     : ${ES_URL}/${INDEX}"
echo "type   : $TYPE"
echo "baseline count: $BASELINE"
echo "polling: ${POLL_INTERVAL_MS}ms, timeout ${TIMEOUT_SEC}s"
echo
read -p "결재를 제출한 직후 ENTER 누르세요... " _

START_MS=$(python3 -c 'import time; print(int(time.time()*1000))')
DEADLINE_MS=$((START_MS + TIMEOUT_SEC * 1000))

while true; do
  NOW_MS=$(python3 -c 'import time; print(int(time.time()*1000))')
  if (( NOW_MS > DEADLINE_MS )); then
    echo "FAIL timeout (>${TIMEOUT_SEC}s) — 색인되지 않음 (count 변화 없음)"
    exit 1
  fi

  CURRENT=$(count_docs)
  if (( CURRENT > BASELINE )); then
    ELAPSED_MS=$((NOW_MS - START_MS))
    echo "indexed: count $BASELINE → $CURRENT, elapsed ${ELAPSED_MS} ms"
    if (( ELAPSED_MS < 10000 )); then
      echo "PASS CDC<10s"
    else
      echo "FAIL CDC=${ELAPSED_MS}ms (limit 10000ms)"
    fi
    exit 0
  fi

  python3 -c "import time; time.sleep($POLL_INTERVAL_MS/1000)"
done
