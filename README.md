# PeopleCore - 전자결재 시스템

Spring Boot 기반 마이크로서비스 아키텍처로 구현한 전자결재 시스템입니다.
문서 기안부터 결재 완료까지의 전체 결재 프로세스를 지원하며, HR 시스템과 연동하여 조직 기반의 결재 워크플로우를 제공합니다.

---

## 프로젝트 문서

- [WBS 및 요구사항 명세서](https://docs.google.com/spreadsheets/d/1ALYx-2p5l8czzkQxdX7Dp3tdlmTaNh0fP9mfEfIhK14/edit?usp=sharing)
- [기획서](https://docs.google.com/document/d/1LhBwkw5gadTXXApqSiI7-_ngIhpgbRAm/edit?usp=sharing&ouid=113011859077434472718&rtpof=true&sd=true)
- [ERD (ERDCloud)](https://www.erdcloud.com/d/zu3piDR4rivmATs4d)
- [화면 설계 (Figma)](https://www.figma.com/design/GRt4wS7G4Gc4oMM8hzOZSi/PeopleCore-%ED%99%94%EB%A9%B4-%EC%84%A4%EA%B3%84?node-id=15-71&t=PgQIPCy8W7eyuK2v-1)

---

## ERD

<details>
<summary>ERD 전체</summary>

![ERD 전체](picture/PEOPLECORE.png)

</details>

<details>
<summary>HR / 기타 모듈 ERD</summary>

![HR / 기타 모듈 ERD](picture/peoplecore-another.png)

</details>

<details>
<summary>Collaboration 모듈 ERD (전자결재, 게시판, 캘린더, 알림)</summary>

![Collaboration 모듈 ERD](picture/PEOPLECORE-PURPLE.png)

</details>

---

<details>
<summary><h2>기술 스택</h2></summary>

### Backend
| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.5.13, Spring Cloud 2025.0.1 |
| ORM / Query | Spring Data JPA, QueryDSL 5.1.0 (Jakarta) |
| Database | MySQL |
| Cache | Redis |
| Message Broker | Apache Kafka, Spring Cloud Stream |
| Search Engine | Elasticsearch (Spring Data Elasticsearch) |
| File Storage | MinIO 8.5.7 (S3 호환 오브젝트 스토리지) |

### Microservice Infrastructure
| 분류 | 기술 |
|------|------|
| API Gateway | Spring Cloud Gateway (WebFlux) |
| Service Discovery | Netflix Eureka (Server / Client) |
| Config Management | Spring Cloud Config Server |
| Circuit Breaker | Resilience4j |
| Event Bus | Spring Cloud Bus (Kafka Binder) |
| Monitoring | Spring Boot Actuator |

### Security / Auth
| 분류 | 기술 |
|------|------|
| JWT | JJWT 0.11.5 |
| Encryption | Spring Security Crypto |
| SMS 인증 | CoolSMS (nurigo) 4.3.0 |

### Build / Utility
| 분류 | 기술 |
|------|------|
| Build Tool | Gradle (멀티모듈) |
| Code Generation | Lombok 1.18.32, MapStruct 1.5.5.Final |
| Batch / Scheduler | Spring Batch + Spring `@Scheduled` (연차 부여 / 잔여 만료 / 근태 자동 마감 / 파티션 증설) |

### Database 설계 특이사항
| 분류 | 기술 |
|------|------|
| 파티셔닝 | MySQL `RANGE COLUMNS(work_date)` — `attendance`, `commute_record` 월별 파티셔닝 (복합 PK) |
| 동시성 제어 | JPA `@Version` 낙관적 락, `PESSIMISTIC_WRITE` + Redis 분산 락 혼용 |

</details>

---

<details>
<summary><h2>서비스 모듈 구성</h2></summary>

```
PeopleCore/
├── api-gateway/            # API 라우팅, JWT 검증, 사용자 헤더 주입
├── eureka-server/          # 서비스 디스커버리
├── config-server/          # 중앙 설정 관리
├── common/                 # 공통 엔티티, 설정, 인터셉터
├── hr-service/             # 인사 관리 (사원, 부서, 직급, 급여, 근태)
├── collaboration-service/  # 전자결재, 게시판, 캘린더, 알림
└── search-service/         # Elasticsearch 기반 통합 검색
```

</details>

---

<details>
<summary><h2>통합 검색 (Elasticsearch + Debezium CDC) 로컬 세팅</h2></summary>

MySQL의 변경 이벤트를 Debezium이 binlog 기반으로 감지하여 Kafka → search-service → Elasticsearch로 전파합니다. hr-service 코드는 MySQL에만 저장하면 되고, 검색 색인은 완전히 분리되어 있습니다.

> 🔧 **운영 가이드**: 인덱스 재색인·트러블슈팅 절차는 [scripts/search/README.md](scripts/search/README.md) 참고.

### 빠른 시작

1. **팀 메신저에서 받은 2개 파일을 지정 위치에 배치** (0번 참고)
2. **MySQL binlog 활성화** (최초 1회, 1번 참고)
3. **`docker-compose up -d`**
4. **IntelliJ에서 서비스 기동** — config → eureka → gateway → hr → collaboration → **search**

### 0. 사전 파일 배치 (팀 메신저에서 수령)

아래 두 파일은 git에 포함되지 않으므로 팀 메신저에서 받아 지정 위치에 저장하세요.

| 파일 | 배치 위치 |
|------|-----------|
| `application-local.yml` | `search-service/src/main/resources/application-local.yml` |
| `debezium-connector.json` | `scripts/search/debezium-connector.json` |

> `debezium-connector.json`의 `database.password`가 본인 로컬 MySQL 비밀번호와 다르면 수정 필요.

### 1. MySQL binlog 활성화 (최초 1회)

MySQL 설정 파일의 `[mysqld]` 섹션에 추가:

```ini
log_bin = mysql-bin
binlog_format = ROW
binlog_row_image = FULL
server_id = 1
```

**설정 파일 위치 & 재시작 (OS별)**

| OS | 설정 파일 | 재시작 |
|----|----------|--------|
| Windows | `C:\ProgramData\MySQL\MySQL Server 8.0\my.ini` | 서비스 관리자 → MySQL 재시작 |
| Mac (Homebrew) | `/opt/homebrew/etc/my.cnf` (Apple Silicon) 또는 `/usr/local/etc/my.cnf` (Intel) | `brew services restart mysql` |
| Mac (공식 설치) | `/etc/my.cnf` 또는 `/usr/local/mysql/etc/my.cnf` | `sudo /usr/local/mysql/support-files/mysql.server restart` |

DataGrip/DBeaver에서 검증:
```sql
SHOW VARIABLES WHERE Variable_name IN ('log_bin','binlog_format','binlog_row_image','server_id');
```
- `log_bin=ON`, `binlog_format=ROW`, `binlog_row_image=FULL`, `server_id≥1` 이어야 함

### 2. 인프라 기동

프로젝트 루트에서:
```bash
docker-compose up -d
```

자동으로 다음이 실행됩니다:
- Elasticsearch (9200) + Kibana (5601)
- Kafka (9092) + Kafka Connect + Debezium (8083)
- `search-init` 컨테이너가 ES 인덱스 생성 + Debezium Connector 자동 등록

### 3. 세팅 검증

```bash
curl http://localhost:9200/unified_search                       # 인덱스 존재 확인
curl http://localhost:8083/connectors                           # ["peoplecore-mysql-connector"]
curl http://localhost:8083/connectors/peoplecore-mysql-connector/status   # state: RUNNING
```

### 4. 서비스 기동 (IntelliJ)

아래 순서대로 기동:
1. `config-server`
2. `eureka-server`
3. `api-gateway`
4. `hr-service`
5. `collaboration-service`
6. **`search-service`** — 통합검색 기능 사용을 위해 필수

### 5. 사용

- 통합검색 API: `GET /search-service/search?keyword=...&type=EMPLOYEE|DEPARTMENT|APPROVAL|CALENDAR`
- MySQL INSERT/UPDATE/DELETE → Debezium이 감지 → ES 자동 색인 (1초 내)
- 데이터는 Docker Volume(`es-data`, `kafka-data`)에 영속화되어 재시작에도 유지

### 6. 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| Connector `state: FAILED` | DB 비밀번호 불일치 | `scripts/search/debezium-connector.json`의 `database.password` 수정 → `curl -X DELETE .../peoplecore-mysql-connector` → `docker-compose restart search-init` |
| search-service 기동 실패 | `application-local.yml` 없음 | 팀 메신저에서 받아 `search-service/src/main/resources/`에 배치 |
| 검색 결과 0건 | Debezium 초기 스냅샷 진행 중 | `curl .../status`로 상태 확인, 30초 대기 |
| 검색 시 500 에러 | ES 인덱스 매핑 불일치 | 아래 "초기화" 절차 수행 |
| binlog 설정 후에도 OFF | MySQL 재시작 안 됨 | 위 1번 표의 재시작 명령 재확인 |

### 7. 재색인이 필요한 경우

매핑 변경·데이터 누락 등으로 인덱스를 처음부터 다시 만들어야 할 때는 **Debezium offset, ES 인덱스, Kafka consumer group 3곳을 모두 리셋**해야 합니다. 하나라도 빠지면 부분 누락이 발생합니다.

```bash
./scripts/search/reindex.sh
```

상세 절차·트러블슈팅·검증 방법은 [scripts/search/README.md](scripts/search/README.md) 참고.

### 아키텍처

```
MySQL (binlog)
  ↓ Debezium MySQL Connector
Kafka topics (peoplecore.peoplecore.employee 등)
  ↓ search-service CdcEventListener
Elasticsearch (unified_search 인덱스)
  ↓
Search API (/search-service/search)
```

`hr-service`는 Search 로직을 전혀 알지 못하며, DB 저장만 담당합니다. MSA에서의 완전한 decoupling 달성.

</details>

---

<details>
<summary><h2>주요 기능</h2></summary>

<details>
<summary><h3>전자결재</h3></summary>

#### 결재 프로세스 전체 흐름

```
┌─────────┐     ┌──────────┐     ┌──────────────────────────────┐     ┌───────────────┐
│ 양식 선택 │ ──→ │ 문서 작성  │ ──→ │         결재 요청 (상신)        │ ──→ │   결재 진행     │
│         │     │          │     │                              │     │               │
│ · 양식 폴더 │     │ · 임시저장  │     │ · 문서번호 자동채번              │     │ · 순차 결재     │
│   계층 탐색 │     │   (DRAFT) │     │   (슬롯+날짜+순번 조합)         │     │   (lineStep)  │
│ · 버전 관리 │     │ · 결재선   │     │ · 위임 자동 처리                │     │ · 승인/반려     │
│ · 작성 권한 │     │   템플릿   │     │   (부재 시 대결자 전환)          │     │ · 회수 (기안자)  │
│   (ALL/   │     │   불러오기  │     │ · 첫 번째 결재자 알림 발송       │     │ · 반려 후 재기안  │
│  DEPT/    │     │ · 첨부파일  │     │   (Kafka 비동기)              │     │               │
│  PERSONAL)│     │ · 긴급문서  │     │ · 자동 분류 규칙 적용            │     │               │
└─────────┘     └──────────┘     └──────────────────────────────┘     └───────┬───────┘
                                                                              │
                                                                              ↓
┌───────────────────────────────────────────────────────────────────────────────────────┐
│                              결재 완료                                                  │
│                                                                                       │
│  · 마지막 결재자 승인 시 문서 상태 자동 전환 (PENDING → APPROVED)                             │
│  · 서명이 포함된 완성 HTML 문서 생성 → MinIO 아카이빙                                        │
│  · 기안자 및 관련자에게 완료 알림 발송                                                       │
│  · 개인 문서함 / 부서 문서함에 자동 분류                                                     │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

#### 문서 상태 전이

```
임시저장(DRAFT) → 결재 요청(PENDING) → 승인(APPROVED) / 반려(REJECTED) / 회수(CANCELED)
                                         ↑
                              반려 후 재기안 ──┘
```

각 상태는 **State Pattern**으로 관리되어, 상태별로 허용되는 동작만 실행 가능합니다.

#### 문서함 시스템

- **개인 문서함** : 사용자가 직접 폴더를 생성하고, 자동 분류 규칙(제목/양식/기안자/부서 조건 AND 결합)을 설정하여 문서를 자동 분류
- **부서 문서함** : 대기함, 수신함, 발신함, 참조함, 열람함으로 구성

</details>

<details>
<summary><h3>공통 테이블 설계</h3></summary>

모듈마다 댓글, 즐겨찾기, 첨부파일 테이블을 개별적으로 만들면 테이블 수가 급증합니다. 이를 방지하기 위해 `entityType + entityId` 복합 키 패턴으로 공통 테이블을 설계하여 여러 모듈(전자결재, 게시판, 캘린더 등)에서 하나의 테이블을 재사용합니다.

| 공통 테이블 | 용도 | 주요 필드 |
|-------------|------|-----------|
| `CommonComment` | 댓글 | `entityType`, `entityId`, `parentCommentId`(대댓글), 작성자 정보 비정규화 |
| `CommonBookmark` | 즐겨찾기 | `entityType`, `entityId`, `empId` (동일 엔티티 중복 방지 UK) |
| `CommonCodeGroup` / `CommonCode` | 코드 그룹 → 코드 | `groupCode`로 그룹 분류, `codeValue`로 코드 값 관리, 정렬 순서 지원 |
| `CommonAttachFile` | 첨부파일 | `entityType`, `entityId`, MinIO 연동 (`storedFileName`, `fileUrl`) |

**설계 원칙**

- **FK 없는 느슨한 결합** : MSA 환경에서 모듈 간 DB 외래키 제약 없이 `entityType + entityId`로 논리적 연결
- **작성자 정보 비정규화** : 댓글/즐겨찾기에 사원명, 부서명, 직급명을 스냅샷으로 저장하여 HR 서비스 호출 없이 조회 가능
- **하나의 테이블로 다수 모듈 지원** : 전자결재 댓글, 게시판 댓글, 캘린더 댓글 등을 `entityType` 값만 다르게 하여 동일 테이블에서 관리

</details>

<details>
<summary><h3>근태 관리 (Attendance)</h3></summary>

#### 핵심 기능

- **출퇴근 기록** — IP 기반 근무지 판정(사내/외근), UNIQUE 제약으로 중복 체크인 방어
- **지각/조퇴/초과근무 자동 계산** — 근무 그룹 기준 시간과 실제 기록을 비교해 상태·시간을 자동 산출
- **근무 그룹(WorkGroup)** — 사원별 표준 근무시간·근무요일·휴게시간 정의
- **초과근무 정책** — 주간 최대 초과시간 제한 및 정책 액션(`NOTIFY` / `BLOCK`)
- **주간/월간 집계 API** — 휴가 · 출퇴근 동시 집계
- **근태 정정** — 결재 승인 시 마감 해제 후 자동 재계산

</details>

<details>
<summary><h3>휴가 관리 (Vacation)</h3></summary>

#### 핵심 기능

- **연차 자동 부여** — HIRE(입사기념일) / FISCAL(회계연도) 두 가지 부여 방식 지원
- **연차 신청 ↔ 전자결재 연동 (Kafka)** — 결재 상신/결과 이벤트를 수신해 휴가 요청 상태와 잔여를 자동 반영
- **잔여 연차 관리** — `total / used / pending / expired` 구분, `Ledger` 테이블로 원장 추적
- **만료 & 촉진 통지** — 만료일 도래 시 자동 소멸, 만료 7일 전 / 당일 2차 통지
- **영업일 기준 휴가 일수 산정** — 주말 + 공휴일 제외

</details>

<details>
<summary><h3>배치 / 스케줄러 (Batch)</h3></summary>

근태·휴가 도메인은 일·월 단위 반복 처리와 정책 기반 일괄 반영이 많아, Spring Batch + `@Scheduled` 조합으로 배치 계층을 구성했습니다.

| 배치명 | 주기 | 역할 |
|--------|------|------|
| `AnnualGrantScheduler` | 매일 | HIRE / FISCAL 방식 연차 자동 부여 |
| `BalanceExpiryScheduler` | 매일 | 만료일 도래 연차 자동 소멸, Ledger `EXPIRED` 기록 |
| `PromotionNoticeScheduler` | 매일 | 만료 7일 전(1차) / 당일(2차) 촉진 통지 발송 |
| `AttendanceAutoCloseBatch` | 매월 | 전월 근태 자동 마감, 미체크아웃 건 상태 보정 |
| `PartitionScheduler` | 매월 | `commute_record` / `attendance` 다음 달 파티션 자동 증설 |
| `PayrollReflectBatch` | 월마감 | 확정 근태 · 휴가 데이터를 급여 반영 분 컬럼으로 전송 |

</details>

</details>

---

<details>
<summary><h2>문제 해결 사례</h2></summary>

<details>
<summary><h3>전자결재</h3></summary>

<details>
<summary>1. 동시성 충돌 - 결재자 승인 vs 기안자 회수</summary>

**문제**

결재자가 승인 버튼을 누르는 동시에 기안자가 문서를 회수하면, 두 요청이 동시에 처리되어 데이터 정합성이 깨지는 문제가 발생합니다.

**해결 - JPA 낙관적 락 (Optimistic Lock)**

`ApprovalDocument` 엔티티에 `@Version` 필드를 적용했습니다. JPA가 UPDATE 쿼리 실행 시 `WHERE version = N` 조건을 자동으로 부여하여, 먼저 처리된 요청만 성공하고 뒤늦게 도착한 요청은 `OptimisticLockException`이 발생합니다.

```java
@Version
private Long version;
```

</details>

<details>
<summary>2. 채번 동시성 - 동시 기안 시 중복 번호 발급</summary>

**문제**

여러 사용자가 동시에 문서를 기안하면 같은 순번이 발급되어 문서번호가 중복될 수 있습니다.

**해결 - 비관적 락 + 낙관적 락 + 재시도**

`ApprovalSeqCounter`에 이중 락을 적용했습니다. 채번 시 `PESSIMISTIC_WRITE` 락으로 카운터 행을 선점하고, `@Version`으로 추가 안전장치를 두었습니다. `DataIntegrityViolationException` 발생 시 최대 3회까지 자동 재시도합니다.

```
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM ApprovalSeqCounter s WHERE ...")
Optional<ApprovalSeqCounter> findWithLock(...);
```

</details>

<details>
<summary>3. 상태 전이 복잡도 - State Pattern 적용</summary>

**문제**

문서 상태(임시저장/진행중/승인/반려/회수)별로 허용되는 동작이 다른데, if-else로 관리하면 상태가 추가될 때마다 분기문이 늘어나 유지보수가 어렵습니다.

**해결 - 상태 패턴 (State Pattern)**

`ApprovalStatus` enum에 각 상태별 State 객체를 내장했습니다. 각 State가 `approve()`, `reject()`, `recall()`, `submit()` 동작의 허용 여부를 스스로 판단하므로, 새로운 상태가 추가되어도 기존 코드를 수정할 필요가 없습니다.

```
ApprovalStatus
├── DRAFT    → DraftState    : submit만 허용
├── PENDING  → PendingState  : approve, reject, recall 허용
├── APPROVED → ApprovedState : 모든 동작 차단
├── REJECTED → RejectedState : submit(재기안)만 허용
└── CANCELED → CanceledState : 모든 동작 차단
```

</details>

<details>
<summary>4. 동적 검색 조건 - QueryDSL 활용</summary>

**문제**

문서함 목록 조회 시 제목, 기안자, 문서번호, 날짜 범위, 양식, 상태 등 다양한 조건이 선택적으로 조합됩니다. 정적 쿼리로는 모든 조합을 대응할 수 없고, 문자열 기반 동적 쿼리는 타입 안전성이 없습니다.

**해결 - QueryDSL + BooleanBuilder**

`ApprovalDocumentCustomRepositoryImpl`에서 `BooleanBuilder`를 활용한 동적 쿼리를 구현했습니다. 입력된 조건만 WHERE절에 추가되고, EXISTS 서브쿼리로 결재선 기반 필터링(대기함, 참조함 등)을 처리합니다.

- 공통 필터를 재사용 가능한 헬퍼 메서드로 분리 (`applyCommonFilters`)
- Content 쿼리와 Count 쿼리를 분리하여 Count 시 불필요한 JOIN 제거
- `CASE + SUM` 집계로 여러 문서함의 건수를 단일 쿼리로 조회 (`countAllBoxes`)
- `fetchJoin`으로 N+1 문제 해결

</details>

<details>
<summary>5. 문서번호 유연성 - Strategy Pattern 적용</summary>

**문제**

회사마다 문서번호 형식이 다릅니다. 어떤 회사는 부서코드를 넣고, 어떤 회사는 양식명을 넣으며, 커스텀 텍스트를 원하는 경우도 있습니다. 하드코딩하면 회사별 요구사항에 대응할 수 없습니다.

**해결 - SlotTypeRegistry + Strategy Pattern**

각 슬롯 타입(`CompanyNameSlot`, `DeptCodeSlot`, `FormCodeSlot`, `CustomSlot` 등)을 독립된 전략 객체로 구현하고, `SlotTypeRegistry`가 런타임에 적절한 슬롯을 선택합니다. 새로운 슬롯 타입이 필요하면 구현체만 추가하면 됩니다.

```
SlotTypeRegistry
├── COMPANY_NAME → CompanyNameSlot
├── DEPT_CODE    → DeptCodeSlot
├── DEPT_NAME    → DeptNameSlot
├── FORM_CODE    → FormCodeSlot
├── FORM_NAME    → FormNameSlot
├── CUSTOM       → CustomSlot
└── NONE         → NoneSlot
```

</details>

<details>
<summary>6. Kafka - 비동기 이벤트 기반 알림 및 캐시 무효화</summary>

**도입 배경**

결재 요청, 승인, 반려 시마다 관련자에게 알림을 보내야 합니다. 동기 방식으로 처리하면 알림 발송 실패가 결재 트랜잭션 자체를 실패시킬 수 있고, 응답 시간도 길어집니다. 또한 HR 서비스에서 부서 정보가 변경되면 Collaboration 서비스의 캐시를 즉시 무효화해야 합니다.

**적용 방식**

| 토픽 | 발행자 | 소비자 | 목적 |
|------|--------|--------|------|
| `alarm-event` | Collaboration (결재/댓글) | Collaboration (AlarmConsumer) | 결재 이벤트 발생 시 실시간 알림 생성 및 푸시 |
| `hr-dept-updated` | HR Service (부서 변경 시) | Collaboration (HrEventConsumer) | 부서 정보 변경 시 Redis 캐시 무효화 |

- 결재 서비스와 알림 서비스 간 느슨한 결합으로, 알림 실패가 결재 로직에 영향을 주지 않음
- 서비스 간 이벤트 전파로 데이터 정합성을 비동기적으로 유지

</details>

<details>
<summary>7. Redis - 캐시 및 인증 토큰 관리</summary>

**도입 배경**

Collaboration 서비스에서 결재 문서 조회 시 기안자의 부서명, 직급명 등 HR 데이터가 필요합니다. 매 요청마다 HR 서비스를 호출하면 서비스 간 의존도가 높아지고 응답 시간이 증가합니다. 또한 JWT Refresh Token과 SMS 인증 코드처럼 TTL이 필요한 임시 데이터를 관리할 저장소가 필요합니다.

**적용 방식**

| 인스턴스 | DB | 키 패턴 | TTL | 용도 |
|----------|-----|---------|-----|------|
| Redis 1 (6379) | DB 0 | `RT:{empId}` | 7일 | JWT Refresh Token 저장 |
| Redis 1 (6379) | DB 1 | `SMS_CODE:{phone}` | 3분 | SMS 인증 코드 |
| | | `SMS_COOLDOWN:{phone}` | 60초 | 재전송 쿨다운 |
| | | `SMS_FAIL:{phone}` | 10분 | 실패 횟수 추적 (5회 초과 시 차단) |
| Redis 2 (6380) | DB 0 | `hr:dept:{deptId}` | 1시간 | 부서 정보 캐시 |
| | | `hr:company:{companyId}` | 1시간 | 회사 정보 캐시 |
| | | `hr:emp:{empId}` | 1시간 | 사원 정보 캐시 |

- **Cache-Aside 패턴** : 캐시 미스 시 HR 서비스 호출 후 결과를 캐시에 저장
- **Kafka 연동 캐시 무효화** : `hr-dept-updated` 이벤트 수신 시 해당 부서 캐시 즉시 삭제
- **SMS 보안** : 쿨다운, 실패 횟수 제한, 차단 기능으로 SMS 폭탄 공격 방지

</details>

</details>

<details>
<summary><h3>근태 관리</h3></summary>

<details>
<summary>1. 대용량 근태 데이터 - MySQL 월별 파티셔닝</summary>

**문제**

`commute_record`, `attendance` 테이블은 전 사원 × 매일 누적되는 구조로 수년 내 수천만 행이 쌓입니다. 단일 테이블로 운영하면 인덱스 깊이가 깊어져 월별/주간 집계 쿼리가 느려집니다.

**해결 - RANGE COLUMNS 파티셔닝 + 복합 PK + 자동 증설**

`work_date` 기준 월별 `RANGE COLUMNS` 파티셔닝을 적용했습니다. MySQL 파티션 키는 PK 의 일부여야 하므로 `(com_rec_id, work_date)` 복합 PK 로 설계했고, JPA 엔티티는 단일 `id` 만 매핑하여 읽기를 단순화했습니다.

- **파티션 프루닝 규칙** : WHERE 절에 `work_date BETWEEN :start AND :end` 를 raw 컬럼 그대로 넣어 함수 래핑 금지 → 해당 월 파티션만 스캔
- **UPDATE 규칙** : 복합 PK 특성상 JPA dirty checking 대신 native 쿼리 + `work_date` 조건 포함 필수
- **자동 증설** : `PartitionScheduler` 가 매월 `REORGANIZE PARTITION pmax` 로 다음 달 파티션을 미리 생성

</details>

<details>
<summary>2. 체크인 동시 호출 - Race Condition 방어</summary>

**문제**

사용자가 출근 버튼을 연타하거나 네트워크 재전송으로 같은 `(empId, workDate)` 에 대해 체크인이 중복 생성될 수 있습니다.

**해결 - UNIQUE 제약 + `saveAndFlush`**

DB 레벨 UNIQUE 제약으로 중복을 원천 차단하고, `saveAndFlush` 로 예외를 즉시 감지해 비즈니스 예외로 변환했습니다. 애플리케이션 락 없이 DB 제약만으로 정합성을 보장합니다.

</details>

<details>
<summary>3. 주간 집계 N+1 - 메모리 인덱싱으로 해결</summary>

**문제**

주간 근태 집계는 사원별 × 날짜별 × 상태별 매트릭스가 필요해, 순진하게 짜면 사원마다 7일치를 개별 조회하는 N+1 이 발생합니다.

**해결 - 3쿼리 일괄 조회 + 메모리 맵 인덱싱**

QueryDSL `Projections.constructor` 로 필요한 컬럼만 플랫하게 3번에 나눠 조회한 뒤, 애플리케이션에서 `Map<empId, Map<workDate, 상태>>` 로 인덱싱하여 O(1) 조회로 매트릭스를 구성합니다.

</details>

</details>

<details>
<summary><h3>휴가 관리</h3></summary>

<details>
<summary>1. 공휴일 조회 - Redis 캐시 + 벌크 조회</summary>

**문제**

휴가 일수 산정에서 공휴일 조회가 반복적으로 발생합니다. 매년 반복되는 법정공휴일과 회사별 일회성 공휴일이 섞여 있어 쿼리 조건도 복잡합니다.

**해결 - 월 단위 Redis 캐시 + 저장 구조 분리**

- 반복 공휴일은 `월+일` 만 저장, 일회성 공휴일은 연/월/일 전체 저장하여 조회 쿼리 단순화
- `biz-holidays:{companyId}:{yyyy-MM}` 키로 월 단위 Redis 캐시 (TTL 6h)
- `HolidaysLookupRepository` 는 날짜 리스트를 벌크 `IN` 절 한 번에 조회

</details>

<details>
<summary>2. 결재 이벤트 Kafka 재시도 - 지수 백오프 + 멱등 처리</summary>

**문제**

전자결재 서비스에서 발행되는 `approval-doc-created` / `approval-result` 이벤트 처리 중 일시 장애가 발생하면 연차 잔여 정합성이 깨집니다. 재시도 시 중복 처리로 잔여가 이중 차감될 위험도 있습니다.

**해결 - `@RetryableTopic` + 멱등 키 검증**

- `@RetryableTopic(attempts = 3, backoff = @Backoff(delay = 10s, multiplier = 2))` 로 10s → 20s → 40s 지수 백오프
- 소비자 진입 시 `findByCompanyIdAndApprovalDocId` 로 중복 수신 체크, 이미 처리된 이벤트는 no-op

</details>

</details>

<details>
<summary><h3>배치 / 스케줄러</h3></summary>

<details>
<summary>1. 멀티 파드 중복 실행 - Redis 분산 락</summary>

**문제**

EKS 환경에서 hr-service 파드가 N개 떠 있으면 `@Scheduled` 가 파드마다 실행되어 연차 부여/만료 배치가 중복 실행됩니다. 같은 사원에게 연차가 이중 부여되거나 이중 소멸될 수 있습니다.

**해결 - `SETNX` 기반 일일 멱등성 락**

`setIfAbsent("{batchName}:{yyyy-MM-dd}", podId, Duration.ofMinutes(10))` 로 당일 최초 진입 파드만 배치를 수행하고, 나머지 파드는 즉시 종료합니다.

- 키에 날짜를 포함해 **일일 멱등성** 보장 (재시작되어도 같은 날엔 재실행 안 됨)
- TTL 10분으로 락 홀더가 OOM 등으로 죽어도 자동 해제

</details>

<details>
<summary>2. Chunk 기반 대용량 처리 - 메모리 · 롤백 범위 제한</summary>

**문제**

전 사원 대상 배치를 단일 트랜잭션으로 처리하면 수천 건 단위에서 메모리 사용량이 급증하고, 중간 실패 시 모든 작업이 롤백됩니다.

**해결 - Spring Batch Chunk(500) + Skip/Retry 정책**

`ItemReader → ItemProcessor → ItemWriter` 를 Chunk 500 건 단위로 커밋하여 메모리 사용량을 일정하게 유지합니다. `JobRepository` 에 실행 이력을 기록해 재시작 시 실패 지점부터 재개할 수 있도록 구성했습니다.

</details>

</details>

</details>
