#!/bin/bash
# Search Infra Reindex Script
#
# Debezium 커넥터 + ES 인덱스 + Kafka consumer offset을 3단으로 리셋해서
# unified_search 인덱스를 처음부터 재색인합니다.
#
# 사용법: ./scripts/search/reindex.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

CONNECTOR="peoplecore-mysql-connector"
INDEX="unified_search"
CONSUMER_GROUP="search-service-cdc"
ES="http://localhost:9200"
CONNECT="http://localhost:8083"
KAFKA_CONTAINER="kafka"
KAFKA_BOOTSTRAP="localhost:9092"

C_RED='\033[0;31m'
C_GREEN='\033[0;32m'
C_YELLOW='\033[0;33m'
C_BLUE='\033[0;34m'
C_RESET='\033[0m'

log()  { echo -e "${C_BLUE}→${C_RESET} $*"; }
ok()   { echo -e "${C_GREEN}✓${C_RESET} $*"; }
warn() { echo -e "${C_YELLOW}!${C_RESET} $*"; }
err()  { echo -e "${C_RED}✗${C_RESET} $*" >&2; }

# --- 사전 체크 ---

command -v curl >/dev/null || { err "curl 필요"; exit 1; }
command -v docker >/dev/null || { err "docker 필요"; exit 1; }

log "사전 체크: ES, Kafka Connect, Kafka 컨테이너"
curl -sf "$ES" > /dev/null || { err "ES 접근 불가: $ES"; exit 1; }
curl -sf "$CONNECT" > /dev/null || { err "Kafka Connect 접근 불가: $CONNECT"; exit 1; }
docker ps --format '{{.Names}}' | grep -q "^${KAFKA_CONTAINER}$" || { err "kafka 컨테이너 미기동"; exit 1; }
ok "인프라 기동 확인"

# --- 사용자 확인 ---

warn "이 스크립트는 인덱스 삭제 및 재색인을 수행합니다."
warn "진행 전 search-service를 반드시 중지했는지 확인하세요."
read -r -p "    search-service를 중지했습니까? (y/N) " ans
[[ "$ans" == "y" || "$ans" == "Y" ]] || { err "중단"; exit 1; }

# --- 3.2 (a) 커넥터 + offset 리셋 ---

log "Debezium 커넥터 중지 + offset + 커넥터 삭제"
curl -sf -X PUT    "$CONNECT/connectors/$CONNECTOR/stop"    > /dev/null 2>&1 || true
curl -sf -X DELETE "$CONNECT/connectors/$CONNECTOR/offsets" > /dev/null 2>&1 || true
curl -sf -X DELETE "$CONNECT/connectors/$CONNECTOR"         > /dev/null 2>&1 || true
ok "커넥터 제거"

# --- 3.2 (b) ES 인덱스 재생성 ---

log "ES 인덱스 '$INDEX' 삭제 + 재생성"
curl -sf -X DELETE "$ES/$INDEX" > /dev/null 2>&1 || true

HTTP_CODE=$(curl -s -o /tmp/es-create.json -w "%{http_code}" \
    -X PUT "$ES/$INDEX" \
    -H 'Content-Type: application/json' \
    -d @"$SCRIPT_DIR/es-index-mapping.json")

if [[ "$HTTP_CODE" != "200" ]]; then
    err "인덱스 생성 실패 (HTTP $HTTP_CODE)"
    cat /tmp/es-create.json >&2
    exit 1
fi
ok "인덱스 재생성"

# --- 3.2 (c) Kafka consumer group offset 리셋 ---

log "Kafka consumer group '$CONSUMER_GROUP' offset을 earliest로 리셋"
if docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-consumer-groups.sh \
       --bootstrap-server "$KAFKA_BOOTSTRAP" --list | grep -q "^${CONSUMER_GROUP}$"; then
    docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-consumer-groups.sh \
        --bootstrap-server "$KAFKA_BOOTSTRAP" \
        --group "$CONSUMER_GROUP" \
        --reset-offsets --to-earliest --all-topics --execute > /dev/null
    ok "consumer offset 리셋"
else
    warn "consumer group '$CONSUMER_GROUP' 미존재 (search-service 최초 기동 시 자동 생성됨, skip)"
fi

# --- 3.3 커넥터 재등록 ---

log "Debezium 커넥터 재등록 (snapshot 재실행)"
HTTP_CODE=$(curl -s -o /tmp/connector-create.json -w "%{http_code}" \
    -X POST "$CONNECT/connectors" \
    -H 'Content-Type: application/json' \
    -d @"$SCRIPT_DIR/debezium-connector.json")

if [[ "$HTTP_CODE" != "201" && "$HTTP_CODE" != "200" ]]; then
    err "커넥터 등록 실패 (HTTP $HTTP_CODE)"
    cat /tmp/connector-create.json >&2
    exit 1
fi
ok "커넥터 등록"

# --- 완료 안내 ---

echo ""
ok "인프라 리셋 완료"
echo ""
echo "다음 단계:"
echo "  1. search-service 재기동"
echo "  2. 검증:"
echo "       curl -s '$ES/$INDEX/_count?pretty'"
echo "       curl -s '$CONNECT/connectors/$CONNECTOR/status' | jq"
echo "       docker exec $KAFKA_CONTAINER /opt/kafka/bin/kafka-consumer-groups.sh \\"
echo "         --bootstrap-server $KAFKA_BOOTSTRAP --describe --group $CONSUMER_GROUP"
echo ""
echo "자세한 절차: scripts/search/README.md"
