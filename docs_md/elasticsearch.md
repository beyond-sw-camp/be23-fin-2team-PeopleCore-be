# Elasticsearch — 통합검색 설계 문서

> PeopleCore의 통합검색은 **Elasticsearch 단일 인덱스(`unified_search`)** 위에 **BM25 + kNN 하이브리드 검색**을 얹고, **Debezium CDC**로 색인을 자동화하는 구조입니다. 이 문서는 인덱스 설계, 한국어 분석기, 검색 모드, 멀티테넌트·권한 필터링, 운영 절차를 한 곳에 정리합니다.

[← README로 돌아가기](../README.md)

---

## 1. 개요

| 항목 | 값 |
|------|---|
| 엔진 | Elasticsearch 8.x (Nori 분석기 플러그인 포함) |
| 인덱스 | `unified_search` 단일 인덱스, 타입별 `type` 필드로 구분 |
| 검색 대상 | EMPLOYEE · DEPARTMENT · APPROVAL · CALENDAR |
| 색인 방식 | MySQL binlog → Debezium → Kafka → search-service → ES (1초 내 반영) |
| 검색 모드 | ① BM25 단독(통합검색) / ② BM25 + kNN 하이브리드 RRF(Copilot 내부) |
| 정확도 | **Recall@5 95%** (PoC 측정) |
| 격리 | `companyId` 강제 필터 + 사용자 접근 권한 이중 필터 |

---

## 2. 왜 Elasticsearch인가

- **이종 도메인을 한 번에 검색**해야 함 → 사원·부서·결재·일정을 단일 진입점에서 통합 검색
- **한국어 형태소 분석**과 **부분일치(접두/n-gram)** 동시 지원 필요 → Nori + n-gram 분석기 조합
- **벡터 검색(kNN)** 네이티브 지원 → 의미 기반 검색을 추가 인프라 없이 수용
- **권한·테넌트 필터를 쿼리 단계에서 강제**할 수 있어 데이터 누출 방지에 유리

---

## 3. 데이터 흐름 (CDC 기반 색인 자동화)

```
MySQL (binlog)
  ↓ Debezium MySQL Connector  (binlog 기반 변경 감지)
Kafka topics  (peoplecore.peoplecore.employee, ..._department, ..._approval, ..._calendar)
  ↓ search-service · CdcEventListener
Elasticsearch  (unified_search 인덱스)
  ↓
Search API  (/search-service/search, /search/hybrid)
```

**핵심 원칙**: `hr-service` 등 도메인 서비스는 **Elasticsearch의 존재를 모릅니다**. MySQL에 저장하기만 하면 Debezium이 감지해 자동으로 ES에 반영됩니다 — MSA 간 완전한 decoupling.

---

## 4. 인덱스 설계 — `unified_search`

### 4.1 단일 인덱스 + `type` 분리 전략

도메인별 인덱스를 따로 만들지 않고 **하나의 인덱스에 모든 타입을 넣되 `type` keyword 필드로 구분**합니다.

| 장점 | 설명 |
|------|------|
| 통합 검색 단순화 | 한 번의 쿼리로 사원·부서·결재·일정을 함께 검색 |
| 운영 부담 감소 | 인덱스 매핑·재색인을 한 곳에서 관리 |
| 타입별 카운트 즉시 산출 | `type` aggregation 한 번으로 탭별 결과 수 표시 |

### 4.2 핵심 필드

| 필드 | 타입 | 용도 |
|------|------|------|
| `type` | keyword | EMPLOYEE / DEPARTMENT / APPROVAL / CALENDAR |
| `companyId` | keyword | **멀티테넌트 격리 필수 키** |
| `sourceId` | keyword | 원본 도메인 PK |
| `title` | text(korean) + `.ngram` | 메인 제목·이름 |
| `content` | text(korean) | 본문/설명 |
| `metadata.*` | object | 타입별 부가 정보 (부서명·직급·문서번호·접근권한 등) |
| `content_vector` | dense_vector(1536) | 의미 기반 kNN 검색용 임베딩 |
| `createdAt` | date | 정렬·필터용 |

### 4.3 권한 관련 metadata 필드

| 필드 | 적용 타입 | 의미 |
|------|----------|------|
| `empStatus` | EMPLOYEE | 재직 상태 (ACTIVE만 노출) |
| `isUse` | DEPARTMENT | 사용 중 부서만 노출 |
| `drafterId`, `accessibleEmpIds` | APPROVAL | 기안자 또는 열람권자만 접근 |
| `ownerId`, `isPublic`, `isAllEmployees` | CALENDAR | 본인 일정 또는 공개 일정만 노출 |

---

## 5. 한국어 분석기 설계

Nori 형태소 분석기와 n-gram 분석기를 **인덱싱·검색 단계에서 다르게 적용**해 한국어 정확도와 부분일치 성능을 동시에 확보합니다.

| 분석기 | 사용처 | 특징 |
|--------|--------|------|
| `korean` | 인덱싱 | Nori `decompound_mode: mixed` + 품사 필터 → 어근 추출, 조사 제거 |
| `korean_search` | 검색 시 | 동일 토크나이저 + lowercase (품사 필터 제외해 검색 누락 방지) |
| `korean_ngram` | 부분일치(`title.ngram`) | 2~10자 n-gram 토크나이저로 자동완성·접두 검색 |

**예시**: "홍길동" 검색 시
- `korean` → `홍`, `길동` 토큰 매칭
- `korean_ngram` → `홍길`, `홍길동`, `길동` 등 부분 토큰까지 매칭

---

## 6. 검색 모드

### 6.1 BM25 단독 (통합검색 — `/search-service/search`)

- 키워드 기반 정확 매칭에 최적
- `title^3` · `title.ngram^2` · `content` · `metadata.*` 필드를 multi_match로 가중치 결합
- 사용자에게 노출되는 **표준 통합검색** 경로

### 6.2 BM25 + kNN 하이브리드 (Copilot 내부 — `/search-service/search/hybrid`)

```
사용자 쿼리
  ├─ BM25 검색 (top-K)        ─┐
  └─ 임베딩 → kNN 검색 (top-K) ─┴─ RRF 융합 → 최종 순위
```

| 단계 | 처리 |
|------|------|
| ① BM25 | 키워드 기반 후보 K건 |
| ② 임베딩 생성 | 질의를 1536차원 벡터로 변환 |
| ③ kNN | `content_vector` 기준 코사인 유사 후보 K건 |
| ④ RRF 융합 | `score = Σ 1/(k + rank_i)` 로 두 결과 순위 통합 |

**왜 Copilot 전용인가**: 통합검색은 사용자가 직접 키워드를 입력하는 경로라 BM25 단독이 더 빠르고 예측 가능. Hybrid는 자연어 질의를 LLM이 도구로 호출하는 Copilot 내부 검색에서 의미 기반 보강이 필요한 경로에 한정합니다.

---

## 7. 멀티테넌트 · 권한 필터링

모든 검색 쿼리는 다음 두 단계 필터를 **쿼리 시점에 강제 주입**합니다.

### 7.1 회사 격리 (필수)

```
filter: { term: { companyId: <요청자 companyId> } }
```
JWT에서 추출한 `companyId`를 쿼리 시점에 강제 주입 → 클라이언트가 우회할 수 없음.

### 7.2 사용자 접근 권한 (관리자가 아닌 경우)

타입별 OR 조건으로 묶어 사용자가 볼 수 있는 문서만 통과:

| 타입 | 접근 규칙 |
|------|----------|
| EMPLOYEE | `empStatus = ACTIVE` |
| DEPARTMENT | `isUse = true` |
| APPROVAL | `drafterId == me` OR `me ∈ accessibleEmpIds` |
| CALENDAR | `ownerId == me` OR `isPublic = true` OR `isAllEmployees = true` |

관리자 권한(`isAdmin`)일 경우 회사 격리만 적용하고 권한 필터는 생략합니다.

---

## 8. 성능 지표

| 지표 | 값 | 비고 |
|------|---|------|
| Recall@5 | **95%** | PoC 평가 데이터셋 기준 |
| 색인 지연 | < 1초 | binlog 이벤트 발생 → ES 검색 가능까지 |
| 자동완성 응답 | 수십 ms 수준 | `title.ngram` 기반 prefix |

상세 벤치마크: [`picture/poc-hybrid-search-bench.png`](../picture/poc-hybrid-search-bench.png)

---

## 9. 운영 가이드

### 9.1 로컬 세팅

[README의 "환경 설정 → 통합 검색 (Elasticsearch + Debezium CDC) 로컬 세팅"](../README.md#7-환경-설정) 항목 참고.

핵심만 요약:
1. `application-local.yml`, `debezium-connector.json` 팀 메신저에서 수령 후 배치
2. MySQL binlog 활성화 (최초 1회)
3. `docker-compose up -d` → ES + Kafka + Debezium + `search-init` 자동 기동
4. IntelliJ에서 `config → eureka → gateway → hr → collaboration → search` 순으로 기동

### 9.2 재색인 (매핑 변경·데이터 누락 시)

```bash
./scripts/search/reindex.sh
```

> ⚠️ **반드시 3곳을 모두 리셋**: Debezium offset · ES 인덱스 · Kafka consumer group. 하나라도 빠지면 부분 누락 발생.

상세 절차·검증 방법: [`scripts/search/README.md`](../scripts/search/README.md)

### 9.3 자주 발생하는 트러블

| 증상 | 원인 | 해결 |
|------|------|------|
| Connector `state: FAILED` | DB 비밀번호 불일치 | `debezium-connector.json` 수정 후 재등록 |
| 검색 결과 0건 | Debezium 초기 스냅샷 진행 중 | 30초 대기 후 `/connectors/.../status` 확인 |
| 검색 시 500 에러 | ES 인덱스 매핑 불일치 | 9.2 재색인 절차 수행 |
| `binlog=OFF` | MySQL 재시작 누락 | OS별 재시작 명령 재확인 |

---

## 10. 관련 코드·파일

| 항목 | 위치 |
|------|------|
| 검색 서비스 로직 | `search-service/src/main/java/com/peoplecore/service/SearchService.java` |
| CDC 수신·색인 | `search-service/src/main/java/com/peoplecore/cdc/CdcEventListener.java` |
| 문서 매핑 | `search-service/src/main/java/com/peoplecore/document/SearchDocument.java` |
| 인덱스 매핑 정의 | `scripts/search/es-index-mapping.json` |
| Debezium 커넥터 설정 | `scripts/search/debezium-connector.json` |
| 재색인 스크립트 | `scripts/search/reindex.sh` |
| 운영 가이드 | `scripts/search/README.md` |

---

[← README로 돌아가기](../README.md)
