# 분산 배치/스케줄러 — Quartz JDBC + Spring Batch 마이그레이션

> EKS 다중 파드 환경에서 `@Scheduled` + Redis 분산락 조합이 가진 한계를 **Quartz JDBC 클러스터링 + Spring Batch JobLauncher** 조합으로 정리한 기록입니다. 8개 배치 잡(근태 4 + 휴가 4)을 단일 매트릭스로 통일하고 misfire 정책까지 잡별로 분기한 설계 의사결정을 담았습니다.

[← README로 돌아가기](../README.md)

---

## 1. 개요

| 항목 | Before | After |
|------|--------|-------|
| 스케줄러 | `@Scheduled` (JVM 인메모리) | Quartz JDBC 클러스터링 |
| 분산 fire 방어 | Redis 분산락 (skip 방식) | 단일 노드 fire 보장 |
| 잡 실행 방식 | Service 직접 호출 | `JobLauncher → Spring Batch` |
| 멱등 가드 | Service 별 중복 구현 | `JobParameters` UNIQUE |
| 실행 가시성 | INFO 로그 grep | `BATCH_JOB_EXECUTION` 카운트 |
| 실패 알림 | 수동 | Discord 자동 |
| SPOF | Redis | 없음 |

---

## 2. 배경 — `@Scheduled` + Redis 분산락의 한계

### 문제 사항
- EKS 다중 파드에서 `@Scheduled` 가 파드마다 **동시 fire**
- WorkGroup 동적 스케줄이 CRUD 받은 1대에만 반영 → 다른 파드는 옛 cron / 삭제 그룹에 계속 fire
- 같은 잡 N번 실행 → **데이터 중복 처리 / DDL 메타락 충돌**

### 원인 분석
- `@Scheduled` = JVM 단위 스케줄러 → 파드별 독립 fire
- 동적 스케줄 핸들이 JVM 인메모리 → **파드 간 공유 불가**
- Redis 분산락은 "두번째 파드 skip" 만 보장 (N번 fire 자체는 못 막음) + Redis 가 **SPOF 로 격상**

---

## 3. Redis 분산락 vs Quartz — 본질적 차이

> 둘은 카테고리가 다른 도구입니다. Redis 분산락은 **동시성 제어 도구**, Quartz 는 **작업 예약/실행 엔진** — 대체 관계가 아닙니다.

### 3.1 본질 비교

| 항목 | Redis 분산락 | Quartz Scheduler |
|------|--------------|-------------------|
| 본질 | 동시성 제어 | 작업 예약/실행 엔진 |
| 핵심 질문 | "누가 실행할지" | "언제·무엇을·어떻게 실행할지" |
| 상태 저장 | 락 점유 여부만 | Job · Trigger · 실행 이력 |
| 실패 복구 | 없음 (TTL 해제) | misfire 정책으로 재실행 |
| 모니터링 | 직접 구현 | 내장 `JobListener` / `TriggerListener` |

### 3.2 Redis 분산락 단독 사용의 한계

**① 스케줄링 기능 자체가 없음**

- Redis 분산락은 "지금 실행해도 되는가" 만 답할 뿐 시각 트리거가 없음 → `@Scheduled(cron)` 강제 조합
- `@Scheduled` 는 각 인스턴스가 **독립 fire** → 락이 없으면 3개 파드 동시 실행
- 락이 있어도 **실행 시각은 코드에 하드코딩** — WorkGroup 별 동적 cron 변경은 복잡한 별도 구현 필요
- **Quartz**: `scheduleJob` / `rescheduleJob` 으로 런타임 cron 변경 + DB 영속 → 모든 파드 자동 동기화

**② 끊긴 작업 인계 불가**

```
1. A 서버가 락 획득 (Redis TTL = 10초)
2. A 서버 다운
3. 다른 서버 B / C 는 락 점유 중으로 보여 실행 X
4. A 가 하던 잔여 작업은 그대로 유실
5. TTL 만료 시점엔 이미 다음 cron 근접 — 이번 회차 누락 확정
```
- **Quartz**: `QRTZ_FIRED_TRIGGERS` 에 fire 정보 영속 → 다른 노드가 인계, misfire 정책으로 재실행 결정

**③ misfire 처리 정책 부재**

- "재실행 정책" 이라는 개념 자체가 없음
- 시작 전 다운 / 실행 중 다운 모두 회차가 그냥 사라짐
- 만회하려면 **보상 로직·수동 트리거를 직접 코딩**
- **Quartz**: 트리거 단위 `FIRE_NOW` / `DO_NOTHING` / `IGNORE_MISFIRE_POLICY` 분기 (→ 9장)

**④ 실행 이력 모니터링 불가**

- 알 수 있는 정보 = **"현재 락 점유 여부" 하나뿐**
- 어느 회차가 성공했는지 / 몇 건 처리했는지 / 어느 파드가 실행했는지 모두 안 보임 → `log.info` + grep 의존
- **Quartz + Spring Batch**: `BATCH_JOB_EXECUTION` 의 read/write/skip 카운트 + `QRTZ_FIRED_TRIGGERS` + 내장 Listener 로 이력 자동 영속

---

## 4. Quartz JDBC 클러스터링 — 동작 원리

### 4.1 JDBC 클러스터링이란

여러 서버가 **`QRTZ_*` 테이블을 공유**해 하나의 Job 스케줄을 함께 관리합니다.

- Job 시작 전 **DB row lock** 으로 중복 fire 차단
- 한 노드가 죽으면 다른 노드가 DB 상태를 보고 **자동 인계**

### 4.2 Job 인터페이스 계약

- `Scheduler.scheduleJob` 은 `Class<? extends Job>` 만 수용
- Quartz 가 실행 시각에 **reflection 으로 인스턴스화** → `execute(JobExecutionContext)` 호출
- 메서드 시그니처 불일치 시 NPE

> **리플렉션**: 클래스 이름만으로 정보를 파헤쳐 객체를 강제 생성하는 자바 메커니즘
> **JobExecutionContext**: Quartz 가 실행 시점에 넘기는 컨텍스트 객체

### 4.3 의존성 주입 — 일반 빈과 다른 규칙

| 항목 | 일반 Spring 빈 | Quartz Job |
|------|----------------|-------------|
| 인스턴스 생성 | Spring 컨테이너 | Quartz reflection (매 fire) |
| 호출 생성자 | `@Autowired` 생성자 | **NoArgs 생성자만** |
| 주입 통로 | 생성자 | **필드 `@Autowired`** |
| `final` | ✅ | ❌ (NoArgs 에선 채울 수 없음) |

### 4.4 메타테이블 11개

Quartz 는 Job · Trigger · Lock · 이력을 DB 에 저장. 공식 DDL `tables_mysql_innodb.sql` (Quartz jar 포함) 로 생성합니다.

- **local** : `initialize-schema: always` 자동 생성
- **prod** : `never` + 수동 DDL 적용 (`db/migration/quartz_tables_mysql_innodb.sql`)

### 4.5 장단점

|  | 내용 |
|---|------|
| ✅ 고가용성 | 노드 장애 시 자동 인계 — 중단 없는 실행 |
| ✅ 데이터 보존 | 재시작에도 DB 에 저장된 작업 유실 0 |
| ✅ 부하 분산 | 노드 간 작업 분배 가능 |
| ⚠ DB 의존 | DB 다운 = 스케줄러 정지 (운영 DB 가용성 필수) |
| ⚠ 성능 | DB 락/UPDATE 누적 → JVM 메모리 방식보다 느림 |
| ⚠ 시간 동기화 | 모든 노드 NTP 필수 |

---

## 5. 적용 방법

```gradle
implementation 'org.springframework.boot:spring-boot-starter-quartz'
```

```yaml
batch:
  job:
    enabled: false
  jdbc:
    initialize-schema: always

quartz:
  job-store-type: jdbc
  jdbc:
    initialize-schema: always   # prod 는 never + 수동 DDL
  properties:
    org.quartz.scheduler.instanceName: PeopleCoreClusteredScheduler
    org.quartz.scheduler.instanceId: AUTO
    org.quartz.jobStore.class: org.quartz.impl.jdbcjobstore.JobStoreTX
    org.quartz.jobStore.driverDelegateClass: org.quartz.impl.jdbcjobstore.StdJDBCDelegate
    org.quartz.jobStore.tablePrefix: QRTZ_
    org.quartz.jobStore.isClustered: true
    org.quartz.jobStore.clusterCheckinInterval: 10000
    org.quartz.threadPool.class: org.quartz.simpl.SimpleThreadPool
    org.quartz.threadPool.threadCount: 5
    org.quartz.threadPool.threadPriority: 5
```

**핵심 프로퍼티**

| 키 | 의미 |
|----|------|
| `isClustered: true` | 한 노드만 fire 보장 (DB row lock) |
| `instanceId: AUTO` | 파드별 자동 식별 (HOSTNAME + timestamp) |
| `clusterCheckinInterval` | 노드 다운 감지 주기 (ms) |
| `threadCount` | 동시 fire 가능 잡 수 — 운영 후 조정 |

---

## 6. Service 직접 호출 → Spring Batch JobLauncher 전환

### 문제 사항
- vacation 4잡: `Quartz Job → Scheduler.run() → Service` 직접 호출 구조
- 처리 결과 INFO 로그만 → "어느 회사 몇 명 처리/실패" grep 으로만 확인
- 멱등 가드 = Service 마다 `existsByXxx` SELECT **중복 구현**

### 원인 분석
- Service 직접 호출 → `BATCH_JOB_INSTANCE` / `BATCH_JOB_EXECUTION` 메타 영속성 **0**
- read / write / skip / rollback 카운트 자동 집계 X

### JobLauncher 경유 vs Service 직접 호출

| 항목 | JobLauncher | Service 직접 호출 |
|------|--------------|--------------------|
| 중복 실행 차단 | `BATCH_JOB_INSTANCE` UNIQUE 자동 | 없음 |
| 멱등 책임자 | 프레임워크 (DB 제약) | Service 코드 |
| 청크 / 재시작 | 실패 지점부터 재시작 | 없음 |
| 추적 | read/write/skip 카운트 영속 | 로그뿐 |

### 해결 방법
- 4잡에 `XxxJobConfig` 추가, `Scheduler.run()` = **회사 순회 + `jobLauncher.run()`** 로 슬림화 (Service 시그니처 변경 0)
- `JobParameters = (companyId, targetDate)` → `BATCH_JOB_INSTANCE` UNIQUE 가 같은 회사·같은 날 두번째 호출 자체 차단
- `BatchFailureListener` → FAILED / `skipCount > 0` 감지 → Discord 자동 알림

### 효과
- 회사 1 처리 = 1 row + read/write 카운트로 즉시 파악
- 4잡 + 기존 4잡 = **8잡 단일 매트릭스**

---

## 7. `JobExecutionException` throw 여부 — 알림 vs misfire 일관

### 문제 사항
- `AutoCloseJob` 예외 발생 시 처리 방향 결정 필요
- catch + 로깅만 → **알림 누락**
- 단순 throw → 즉시 refire → **비멱등 잡 중복 처리**

### 원인 분석
- catch + 로깅 → `JobListener` 입장에서 잡 정상 종료 → `getException()` 감지 X → Discord 알림 누락
- 단순 throw → Quartz **즉시 refire** → 자동마감 같은 비멱등 잡은 데이터 중복 처리

### 해결 방법

```java
// refire 차단 + 알림 발사 동시 달성
throw new JobExecutionException(e, false);
```

- misfire `DO_NOTHING` 정책과 일관 (refire X)
- vacation 6 스케줄러 동일 패턴 적용

### 효과
- 실패 즉시 Discord 알림
- 자동마감 중복 처리 위험 0

---

## 8. AutoClose 도메인 이중성 — 2-Step + Reader 가드

### 문제 사항
- vacation 4잡 = 단일 도메인 (Reader → Writer → Service 위임)
- AutoClose 는 한 잡에 **두 도메인** (`CommuteRecord` 미체크아웃 + `Employee` 결근) → `ItemReader` 단일 타입 제약으로 한 Step 처리 X
- 기존 `autoCloseForWorkGroup` 전체 `@Transactional` → 사원 1건 실패 시 **WorkGroup 전체 롤백**

### 후보 비교

| 옵션 | 결과 | 채택 |
|------|------|------|
| Tasklet 1개로 Service 통째 호출 | vacation 패턴과 갈림, 가시성 0 | ❌ |
| 2-Step + Reader 가드 | vacation `PromotionNotice` stage 분기와 동일 | ✅ |

### 해결 방법

**2-Step 분리**
- **Step 1** `autoCloseStep` : `Reader<CommuteRecord>` → `closeOne` 위임
- **Step 2** `absentStep` : `Reader<Employee>` → `markAbsentOne` 위임

**Reader 가드**
- 소정근무요일 / 공휴일 / 휴가자 판정은 Reader 안에서
- 미충족 시 `ListItemReader<>(List.of())` → 자연 종료 (Decider 빈 X)

**트랜잭션 분해**
- 회사 단위 → 사원 1건당 `@Transactional(REQUIRES_NEW)`
- 의존성 7개 → 2개

### 효과
- 8잡 동일 매트릭스 (Reader / Writer / `REQUIRES_NEW` / `JobParameters`)
- 사원 1건 실패가 전체 안 막음
- `skip + skipLimit` item 단위 집계로 가시성 확보

---

## 9. misfire 정책 매트릭스 — 잡별 멱등성 분기

### misfire 란
트리거가 cron 시각에 Job 을 실행하지 못한 상황 — **모든 노드 다운 / 스레드풀 포화 / 클러스터 인계 중** 등.

### 정책 종류 (트리거 단위 설정)

| 정책 | 동작 | 적합 잡 |
|------|------|---------|
| `FIRE_NOW` | 놓친 만큼 즉시 1회 실행 | 멱등 + 만회 안전 |
| `DO_NOTHING` | 건너뛰고 다음 cron 까지 대기 | 비멱등 / 알림 UX 보호 필요 |
| `IGNORE_MISFIRE_POLICY` | 시각 지났어도 N번 모두 실행 | 거의 안 씀 |

> 비용 큰 비멱등 잡은 `DO_NOTHING` + 다음날 알림으로 **개발자 수동 처리**가 효율적.

### 단계별 의사결정

| Phase | 결정 | 이유 |
|-------|------|------|
| 1 | 정책 회피 채택 | 멱등화 비용 > 정책 회피 비용 |
| 2 | 4잡 `DO_NOTHING` → `FIRE_NOW` 안전 상향 | JobInstance UNIQUE 확보 |
| 3 | AutoClose 는 `DO_NOTHING` 유지 | UNIQUE 있어도 알림 UX 보호 |

### 최종 매트릭스 — 8잡

| 정책 | 잡 수 | 대상 |
|------|------|------|
| `FIRE_NOW` | 6 | 멱등 + 만회 안전 잡 |
| `DO_NOTHING` | 2 | BalanceExpiry(만료 두 번 위험), AutoClose(알림 UX) |

- `JobExecutionException(e, false)` 와 일관 (→ 7장)
- 누락 시 `AdminAttendanceJobController` 수동 트리거 복구

---

## 10. 최종 효과 요약

| 지표 | Before | After |
|------|--------|-------|
| 분산 fire 방어 | Redis SPOF | Quartz 단일 노드 보장 |
| 동적 스케줄 동기화 | 1대만 반영 | 전 파드 자동 |
| 실행 가시성 | INFO 로그 grep | `BATCH_JOB_EXECUTION` + 카운트 |
| 멱등 가드 | 4잡 중복 코드 | `JobParameters` UNIQUE |
| 실패 감지 | 수동 | Discord 자동 |
| 사원 1건 실패 영향 | 회사 전체 롤백 | item 단위 skip |
| 회차 누락 (노드 장애) | 발생 | 자동 인계로 0 |
