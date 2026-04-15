# Search Infra Operations

통합검색(search-service) 인프라 운영 가이드입니다. Elasticsearch 인덱스, Debezium MySQL 커넥터, Kafka consumer group 3종을 관리합니다.

---

## ⚠️ 반드시 알아야 할 것 (Lessons Learned)

1. **Debezium 커넥터는 같은 이름으로 재등록해도 snapshot이 재실행되지 않습니다.**
   Kafka의 `connect_offsets` 토픽에 offset이 남아있기 때문.
   → 반드시 `DELETE /connectors/{name}/offsets` 선행 필요.

2. **search-service Kafka consumer group도 별도 리셋 대상입니다.**
   Debezium이 새 이벤트를 토픽에 넣어도, consumer가 이미 latest offset에 있으면 새 이벤트를 skip합니다.
   → `kafka-consumer-groups.sh --reset-offsets --to-earliest` 필요.

3. **3단 리셋(Debezium offset + ES 인덱스 + Consumer group) 중 하나라도 빠지면 부분 누락이 발생합니다.**
   과거 사례: ES 인덱스만 지우고 재등록 → `updated_at`이 최신인 48명만 재색인, 나머지 사원 누락.

---

## 1. 재색인이 필요한 경우

- `es-index-mapping.json` 수정 (ES 매핑은 사후 변경 불가 → 재생성 필수)
- Nori analyzer / ngram 등 분석기 설정 변경
- `debezium-connector.json`의 `table.include.list` 변경 (신규 테이블 추가)
- ES / Kafka 데이터 유실·정합성 이슈 의심

---

## 2. 사전 확인 체크리스트

- [ ] MySQL 컨테이너 기동 중 (`docker ps | grep mysql`)
- [ ] Kafka / kafka-connect 기동 중 (`docker ps | grep -E 'kafka|connect'`)
- [ ] search-service 중지 가능한 상태 (팀원에게 공유)
- [ ] (운영환경) 오프피크 시간대 확인
- [ ] 매핑 JSON 문법 검증: `jq . es-index-mapping.json > /dev/null`

---

## 3. 재색인 절차

### 자동 (권장)

```bash
./scripts/search/reindex.sh
```

스크립트는 아래 수동 절차를 순서대로 실행합니다. 중간 실패 시 출력된 단계부터 수동으로 이어서 진행하세요.

### 수동

#### 3.1 search-service 중지

IDE의 Stop 또는 프로세스 kill. Kafka consumer를 미리 종료해야 offset 리셋이 안전하게 반영됩니다.

#### 3.2 상태 리셋 (3단)

**(a) Debezium 커넥터 + offset 삭제**

```bash
curl -X PUT    http://localhost:8083/connectors/peoplecore-mysql-connector/stop
curl -X DELETE http://localhost:8083/connectors/peoplecore-mysql-connector/offsets
curl -X DELETE http://localhost:8083/connectors/peoplecore-mysql-connector
```

**(b) ES 인덱스 삭제 + 재생성**

```bash
curl -X DELETE http://localhost:9200/unified_search
curl -X PUT    http://localhost:9200/unified_search \
  -H 'Content-Type: application/json' \
  -d @scripts/search/es-index-mapping.json
```

**(c) Kafka consumer group offset 리셋**

```bash
docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group search-service-cdc \
  --reset-offsets --to-earliest --all-topics --execute
```

#### 3.3 Debezium 커넥터 재등록 (snapshot 재실행)

```bash
curl -X POST http://localhost:8083/connectors \
  -H 'Content-Type: application/json' \
  -d @scripts/search/debezium-connector.json
```

#### 3.4 search-service 재기동

IDE Run 또는 `./gradlew :search-service:bootRun`.

---

## 4. 검증

### 4.1 커넥터 상태 (RUNNING이어야 함)

```bash
curl -s http://localhost:8083/connectors/peoplecore-mysql-connector/status | jq
```

### 4.2 ES 문서 수 = DB 소스 row 수

```bash
# ES 타입별 문서 수
curl -s "http://localhost:9200/unified_search/_search?pretty" \
  -H 'Content-Type: application/json' \
  -d '{ "size": 0, "aggs": { "t": { "terms": { "field": "type" } } } }'

# DB 비교 (docker-compose의 mysql 서비스명이 다르면 수정)
docker exec <mysql-container> mysql -uroot -p1234 peoplecore -e \
  "SELECT 'employee' t, COUNT(*) FROM employee
   UNION SELECT 'department', COUNT(*) FROM department;"
```

두 결과의 count가 일치해야 정상.

### 4.3 Consumer lag = 0

```bash
docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group search-service-cdc
```

모든 파티션의 `LAG`이 0이어야 snapshot 소비 완료.

### 4.4 Smoke test (핵심 키워드)

```bash
# 복합명사 매칭
curl -s 'http://localhost:9200/unified_search/_search?pretty' \
  -H 'Content-Type: application/json' \
  -d '{"query":{"multi_match":{"query":"인사","fields":["title","metadata.deptName"]}}}'

# 부분 매칭 (ngram)
curl -s 'http://localhost:9200/unified_search/_search?pretty' \
  -H 'Content-Type: application/json' \
  -d '{"query":{"multi_match":{"query":"영희","fields":["metadata.empName.ngram"]}}}'
```

추가로 FE `SearchModal`에서 실제 검색 1회.

---

## 5. 트러블슈팅

### Q. `curl POST /connectors` → `409 Connector already exists`

이전 커넥터가 지워지지 않음. `curl -X DELETE .../connectors/peoplecore-mysql-connector` 재실행.

### Q. Snapshot이 재실행되지 않음 (로그에 `Snapshot ended` 안 나옴)

- offset 삭제 누락: `DELETE /connectors/{name}/offsets` 실행
- 그래도 안 되면: `debezium-connector.json`의 `topic.prefix` 값을 새 값(예: `peoplecore-v2`)으로 변경 후 재등록

### Q. ES에 일부 row만 색인됨

- Kafka consumer offset 리셋 누락: 3.2 (c) 단계 실행
- search-service를 먼저 중지하지 않고 리셋: 중지 후 리셋, 이후 재기동

### Q. `kafka-consumer-groups.sh ... --reset-offsets` → `Error: Assignments can only be reset if the group is inactive`

search-service가 아직 실행 중. Stop 후 재시도.

### Q. ngram 매핑 에러: `The difference between max_gram and min_gram ... must be less than or equal to: [1]`

`es-index-mapping.json`의 `settings`에 `"index.max_ngram_diff": 18` 유무 확인.

### Q. `_analyze` 결과에 복합명사 원형이 안 보임 (mixed 모드인데)

Nori 사전에 해당 복합명사가 등록되어 있지 않은 경우. 일반적인 단어("국민은행" 등)로 테스트하거나, 사내 용어는 **동의어 필터** 또는 **쿼리 레이어 동의어 확장**으로 해결 (사용자 사전은 멀티테넌트 환경에 부적합).

---

## 6. 관련 파일

| 파일 | 설명 |
|---|---|
| `es-index-mapping.json` | ES 인덱스 매핑 (analyzer, 필드 정의) |
| `debezium-connector.json` | Debezium MySQL 커넥터 설정 |
| `reindex.sh` | 재색인 자동화 스크립트 |
| `../../Dockerfile.elasticsearch` | Nori 플러그인 설치 포함 ES 이미지 |
| `../../docker-compose.yml` | `search-init` 서비스로 초기 부트스트랩 |

---

## 7. 변경 이력

| 일자 | 변경 | 담당 |
|---|---|---|
| 2026-04-14 | Nori + ngram 멀티필드 도입, metadata 명시 매핑, 재색인 절차 정립 | hjw |
