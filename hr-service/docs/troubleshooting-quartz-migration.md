# Phase 1 EKS Quartz JDBC 클러스터링 마이그레이션 트러블슈팅

`@Scheduled` + Redis 분산락 패턴 → Quartz JDBC 클러스터링으로 전환하는 과정에서 마주친 이슈와 해결 기록.

---

## 1. EKS 멀티 파드 환경에서 `@Scheduled` 동시 fire 및 동적 스케줄러 비동기화

### 문제사항
- `@Scheduled` 기반 배치(자동 마감, 월별 파티션 사전 생성, 휴가 만료/이월 등)가 EKS 다중 파드 환경에서 **모든 파드에서 동시 fire**
- WorkGroup별 동적 스케줄(`AutoCloseSchedulerManager`)이 **CRUD 받은 파드 1대에만 반영** — 나머지 파드는 옛 cron 시각 또는 삭제된 그룹에 계속 fire
- 같은 잡이 N번 실행되며 데이터 중복 처리 / DDL 메타데이터 락 충돌 / 옛 시각 fire 위험 발생

### 원인분석
- `@Scheduled` 는 JVM 단위 `ScheduledExecutorService` — **파드별 독립 스케줄러** 라 cron 도래 시 N대 모두 fire
- WorkGroup 동적 스케줄은 `ConcurrentHashMap<Long, ScheduledFuture<?>>` 를 **JVM 인메모리** 보관 → 파드 간 핸들 공유 불가
- 정합성 보호용 Redis 분산락(`setIfAbsent`)이 끼워져 있었으나, 락은 "두번째 파드 skip" 만 보장할 뿐 **잡이 N번 fire 되는 것 자체는 막지 못함** + Redis 가 스케줄러 인프라의 SPOF 로 격상
- 결과: 락 경쟁 로그 폭증, Redis 다운 시 잡 일괄 누락 또는 일괄 중복 fire 위험

### 시도방법
- (1차) PartitionScheduler 에 Redis 분산락 핫픽스 — 월 1회 fire 라 출혈은 막지만 Phase 1 Quartz 마이그레이션 시 폐기 대상 → 단일 패턴 정합성 깨짐, 채택 보류
- (2차) ShedLock 도입 — 새 의존성 추가 + 기존 수동 락 패턴과 갈림 → 채택 보류
- (3차) **Quartz JDBC 클러스터링으로 모든 `@Scheduled` + Redis 락 패턴을 단일 마이그레이션** 결정 — `@Scheduled` 자체 제거, Quartz `JobDetail` + `CronTrigger` 로 전환

### 해결방법
- `spring-boot-starter-quartz` + `org.quartz.jobStore.isClustered: true` + JDBC JobStore 적용
- Quartz 11 개 테이블 자동 생성 (`spring.quartz.jdbc.initialize-schema: always` for local, `never` + 수동 DDL for prod)
- WorkGroup 동적 스케줄: 인메모리 핸들 → `Scheduler.scheduleJob()` / `deleteJob()` 호출 시 DB 영속 → **모든 파드 자동 동기화**
- 자동 마감 배치 Redis 락 코드 일괄 제거 (`LOCK_TTL` / `redisTemplate` / `buildLockKey`)
- Misfire 정책: 자동 마감/결근은 멱등 보장 안 됨 → `MISFIRE_INSTRUCTION_DO_NOTHING`
- **운영 효율**: 잡 이력 `QRTZ_FIRED_TRIGGERS` 단일 소스화, 파드 로그 grep 의존도 제거. 수동 트리거가 어느 파드에 요청해도 한 노드만 실행
- **인프라 단순화**: 스케줄러 가용성을 DB 에 일원화하여 Redis 가용성과 디커플링, SPOF 1 개 제거
- **데이터 정합성**: 노드 장애 시 다른 노드가 fire 인계, 회차 누락 위험 제거

---

## 2. Spring Boot Quartz 자동 설정과 yml 직접 명시 충돌 (`DataSource name not set`)

### 문제사항
- `spring-boot-starter-quartz` 의존성 추가 + yml 에 `spring.quartz.*` 설정 후 부팅 시도 → `BeanCreationException` 으로 부팅 실패
- 스택트레이스 끝단: `org.quartz.SchedulerConfigException: DataSource name not set`
- 도메인 빈 생성 자체가 안 되어 다른 모듈 검증 불가

### 원인분석
- yml 에 `org.quartz.jobStore.class: org.quartz.impl.jdbcjobstore.JobStoreTX` 를 직접 명시했음
- Spring Boot 의 `QuartzAutoConfiguration` 은 `spring.quartz.job-store-type: jdbc` 만 보면 자동으로 `LocalDataSourceJobStore` (Spring DataSource 자동 주입판) 사용
- 사용자가 `jobStore.class` 명시 → Spring Boot 의 `LocalDataSourceJobStore` 가 무시되고 **순수 `JobStoreTX` 가 직접 instantiate**
- 순수 `JobStoreTX` 는 `org.quartz.jobStore.dataSource: <이름>` properties 를 추가 요구 → 그게 없으니 부팅 실패

### 시도방법
- yml `properties` 블록 재검토 — `jobStore.class`, `driverDelegateClass`, `threadPool.class` 가 Spring Boot 자동 설정과 충돌하는지 확인
- Spring Boot 의 `QuartzAutoConfiguration` 소스 검토 → `LocalDataSourceJobStore` 가 DataSource 주입을 자동 처리하는 흐름 확인

### 해결방법
- yml 의 `properties` 에서 다음 3 개 키 제거:
  - `org.quartz.jobStore.class`
  - `org.quartz.jobStore.driverDelegateClass`
  - `org.quartz.threadPool.class` (Quartz 기본값과 동일, 명시 불필요)
- `spring.quartz.job-store-type: jdbc` 만 두면 Spring Boot 가 `LocalDataSourceJobStore` + 메인 DataSource 자동 주입
- 유지할 키: `instanceName`, `instanceId: AUTO`, `tablePrefix`, `isClustered`, `clusterCheckinInterval`, `threadPool.threadCount`, `threadPool.threadPriority`
- **운영 효율**: Spring Boot 자동 설정에 위임 → DataSource 별도 분리 시에도 `@QuartzDataSource` 어노테이션만 추가하면 됨. yml 설정 부피 ↓
- **재발 방지**: 다음 마이그레이션 단계(vacation, partition)에서도 동일 원칙 적용 — Spring Boot 가 알아서 처리하는 키는 명시하지 않음

---

## 3. Spring CronTrigger vs Quartz CronTrigger cron 표현식 호환성

### 문제사항
- `@Scheduled` 시절 사용하던 cron 식 `"0 %d %d * * *"` (6필드) 를 Quartz `CronScheduleBuilder.cronSchedule()` 에 그대로 넘기면 `ParseException`
- 매일 fire 하는 단순 cron 인데 호환 안 됨 — 마이그레이션 작업 도중 발견

### 원인분석
- Spring `CronTrigger` 와 Quartz `CronTrigger` 는 cron 필드 의미가 미묘하게 다름
- **Spring 6필드** (초 분 시 일 월 요일): 와일드카드 `*` 자유롭게 사용 가능
- **Quartz 6필드** (초 분 시 일 월 요일): **일과 요일 필드 둘 중 하나는 반드시 `?`** (둘 다 `*` 허용 안 함 — 모순 정의 방지)
- 즉 매일 fire 시 Spring 은 `* * *` 가능, Quartz 는 `* * ?` 또는 `? * *` 만 가능

### 시도방법
- Quartz 공식 문서 cron 명세 확인 → `?` 의 의미 (no specific value) 발견

### 해결방법
- `toCronExpression()` 메서드 마지막 필드 `*` → `?` 변경
- 매일 fire 라 일=`*`, 요일=`?` 로 고정
- 예: `"0 0 7 * * *"` (Spring) → `"0 0 7 * * ?"` (Quartz)
- **호환성 함정 회피**: 향후 vacation/PartitionScheduler 마이그레이션 시 동일 패턴 적용. cron 식이 정상이라도 `?` 누락 시 부팅 시점이 아닌 트리거 등록 시점에 ParseException 발생하므로, 마이그레이션 직후 검증 쿼리(`SELECT cron_expression FROM QRTZ_CRON_TRIGGERS`)로 즉시 확인 권장

---

## 4. Misfire 정책 결정 — 멱등성 보장 여부에 따른 분기

### 문제사항
- Quartz 트리거가 fire 시각을 놓친 경우(노드 다운, 스레드풀 포화, 클러스터 인계) 어떻게 처리할 것인가
- `FIRE_NOW` (즉시 한 번 만회) / `DO_NOTHING` (다음 정상 시각까지 대기) / `IGNORE_MISFIRE_POLICY` (놓친 만큼 모두 fire) 중 잡마다 어느 것?
- 잘못 선택 시 데이터 중복 처리 또는 누락 발생

### 원인분석
- 잡 멱등성 보장 여부에 따라 정책이 갈림
- **자동 마감 / 결근 처리 / 휴가 이월 · 만료** — 같은 날짜 두 번 돌면 잔여 망가지거나 결근 중복 → 멱등 보장 안 됨
- **파티션 사전 생성** — `COUNT(*)` 체크 후 DDL 이라 두 번 돌아도 첫 회차에서 만들고 두 번째는 skip → 멱등 보장
- **Spring Batch 기반 잡** (월차 적립, 연차 부여) — JobInstance 가 같은 파라미터로 두 번 돌면 `JobInstanceAlreadyCompleteException` → JobLauncher 레벨에서 멱등

### 시도방법
- 각 잡별 데이터 처리 패턴 검토 → 멱등성 매트릭스 작성
- 멱등 보장 안 되는 잡을 코드 수준에서 멱등화하는 비용 vs misfire 정책으로 회피하는 비용 비교

### 해결방법
- 정책 매핑:
  - 자동 마감 / 결근 / 잔여 만료 · 이월 → `DO_NOTHING` (놓치면 운영자 알림 → 수동 트리거 복구)
  - 파티션 사전 생성 → `FIRE_NOW` (멱등하니 만회 시도 안전)
  - Spring Batch 기반 잡 → `FIRE_NOW` (JobInstance 가 멱등 보호)
- 잡 클래스 클래스 주석에 정책 + 근거 명시 (`AutoCloseSchedulerManager` 클래스 주석 참고)
- **운영 효율**: "두 번 돌아 데이터 망가지는 것보다 한 번 안 도는 게 복구 쉽다" 원칙 → 보수적 선택. JobListener + Discord webhook 으로 알림 발사 → 운영자가 인지하면 수동 트리거 가능
- **데이터 정합성**: 멱등성 코드를 일일이 짜는 비용 절감, 정책 수준에서 회피

---

## 5. `JobExecutionException` throw 여부 결정 — JobListener 알림 vs misfire 일관

### 문제사항
- AutoCloseJob 에서 `autoCloseForWorkGroup` 호출 중 예외 발생 시 처리 방향 결정 필요
- catch 후 로깅만 하면 잡 실패가 운영자에게 즉시 알려지지 않음 (Quartz JobListener 가 정상 종료로 인식)
- 단순히 throw 하면 즉시 refire 가능 → 자동마감 같은 비멱등 잡은 데이터 중복 처리 위험

### 원인분석
- Quartz Job 인터페이스는 `JobExecutionException` 만 throw 가능. 일반 예외는 자동 변환되지 않음
- Quartz JobListener 는 `jobToBeExecuted` / `jobWasExecuted` 콜백에서 `JobExecutionContext.getException()` 으로 잡 실패를 감지 → Discord webhook 발사 가능
- catch + 로깅만 하면 JobListener 입장에서 잡이 정상 종료한 것으로 보임 → 알림 발사 누락
- `JobExecutionException` 의 `refireImmediately` 플래그(기본 false) 로 즉시 refire 차단 가능

### 시도방법
- 1차: catch + `log.error` → 알림 누락 발견
- Quartz `JobExecutionException` 시그니처 검토 → `JobExecutionException(Throwable cause, boolean refireImmediately)` 생성자 발견

### 해결방법
- `throw new JobExecutionException(e, false)` 로 변경
- `false` = `refireImmediately X` → misfire `DO_NOTHING` 정책과 일관
- JobFailureNotifier(JobListener 구현) 가 `getException()` 감지 → Discord webhook 발사
- 클래스 주석에 의도 명시 (예외 처리 흐름 + JobListener 연계 흐름)
- **운영 효율**: 잡 실패 즉시 알림 → 운영자 인지 → 수동 트리거 복구 흐름 단축
- **데이터 정합성**: refire 차단으로 자동마감 중복 처리 위험 제거
- **재사용성**: vacation 스케줄러 6 개 마이그레이션 시 동일 패턴 적용 (멱등 보장 잡은 `refireImmediately=true` 도 검토 가능)

---

## 6. `application-local.yml` gitignore 추적 제외 — 운영 yml 분리 필요성

### 문제사항
- Quartz 도입 시 `application-local.yml` 에 `spring.quartz` 블록 + 운영 배포 절차 주석 추가 → `git status` / `git diff` 에 변경분 안 잡힘
- 핸드오프 / 다른 환경 셋업 시 Step 1 변경분이 누락될 위험
- 운영 환경 배포 시 yml 어디서 가져와야 하는가?

### 원인분석
- `.gitignore:39` 에 `**/src/main/resources/application-local.yml` 등록 — 로컬 환경의 비밀번호 / API key 노출 방지가 본 의도
- `git ls-files --error-unmatch hr-service/src/main/resources/application-local.yml` → 추적 자체 안 됨
- 결과: Quartz yml 설정이 코드 변경분에 반영 안 됨

### 시도방법
- `git check-ignore -v` 로 ignore 출처 추적 → `.gitignore:39` 확인
- ConfigServer (`spring-cloud-config`) 사용 여부 검토 → 현재 미사용

### 해결방법
- **로컬 환경**: `application-local.yml` 그대로 사용 (gitignore 유지). 새 환경 셋업 시 핸드오프 문서의 yml 블록 복붙
- **운영 환경**: 다음 중 택 1
  - `application-prod.yml` 신규 + `.gitignore` 에서 제외 (또는 별도 secrets 관리)
  - ConfigServer 도입 후 외부 git repo 에서 prod yml 관리
- 핸드오프 문서에 yml 전체 블록 명시 (검증 절차 + 키별 설명 포함)
- 운영 배포 시 `initialize-schema: never` + DDL 수동 적용 절차도 yml 상단 주석에 명시 (배포 담당자 환기)
- **운영 안전성**: 비밀번호 노출 방지 + 환경별 yml 분리 명확화
- **재발 방지**: Phase 1 후속 마이그레이션 시에도 yml 변경분은 별도 핸드오프 문서로 관리

---

# Phase 2 Spring Batch JobLauncher 전환 트러블슈팅

Service 직접 호출 잡 4 개 (MonthlyAccrual / AnnualTransition / AnnualGrant HIRE / MenstrualMonthlyGrant) 를 Spring Batch JobLauncher 경로로 전환하면서 마주친 이슈와 해결 기록.

---

## 7. Service 직접 호출 잡의 운영 가시성 부재

### 문제사항
- vacation 6 개 잡 중 4 개가 `Quartz Job → XxxScheduler.run() → XxxService.method()` 직접 호출 구조
- 회사 N 개 × 사원 M 명 처리 결과가 INFO 로그에만 남음 — "오늘 몇 개 처리됐고 몇 개 실패했나" 가 로그 grep 으로만 확인 가능
- 같은 잡 두 번 호출 시 멱등 가드는 각 Service 내부에서 `existsByXxx` SELECT 로 개별 구현 → 잡마다 검증 매트릭스 비대화
- 멀티 노드에서 한 노드만 fire (Quartz lock) 하지만, 그 한 노드에서 실패했을 때 재실행 안전성 보장은 잡마다 재구현 필요

### 원인분석
- Service 직접 호출은 BATCH_JOB_INSTANCE / EXECUTION / STEP_EXECUTION 같은 메타 영속성이 없음
- read_count / write_count / commit_count / rollback_count / skip_count 자동 집계 X
- 운영자가 "어떤 회사가 몇 명 처리되고 몇 명 실패했나" 를 보려면 파드 로그 + 시점 추론 필요
- BalanceExpiry / PromotionNotice / AnnualGrantFiscal 은 이미 Spring Batch JobLauncher 패턴이라 4 잡만 동떨어진 구조 → 가시성 / 운영 패턴 갈라짐

### 시도방법
- 각 Service 에 "오늘 이미 처리됐는지" 멱등 가드 메서드를 추가하는 안 검토 → 4 잡 × 멱등 가드 코드 중복 + 검증 비용 증가 → 채택 보류
- 기존 Spring Batch 패턴 (BalanceExpiry / AnnualGrantFiscal) 의 JobConfig + ItemReader/Writer 구조 분석 → 동일 패턴으로 통일 결정

### 해결방법
- 4 잡에 `XxxJobConfig` (Job + Step + Reader + Writer) 신규 추가
- `XxxScheduler.run()` 본체 = "회사 순회 + 회사별 `jobLauncher.run()`" 로 슬림화. 사원 단위 처리 로직은 ItemReader/Writer 로 이동
- 기존 `XxxService.method()` 시그니처 변경 없음 → ItemWriter 가 그대로 위임 호출 (Service 코드 변경 0)
- 잡 본체 (`XxxJob.java` Quartz 진입점) / SchedulerConfig (Quartz 등록) 변경 없음 — 위임 대상만 바꿈
- **운영 효율**: BATCH_JOB_INSTANCE 1 row = 회사 1 개 처리 단위. 운영자가 "오늘 어느 회사가 어떻게 끝났나" 를 SQL 한 줄로 확인. read/write 카운트로 처리량 / 누락 즉시 파악
- **데이터 정합성**: 회사별 격리 → 한 회사 잡 실패가 다른 회사 영향 0
- **알림 자동화**: BatchFailureListener (Phase 1 에서 8 잡 자동 attach 구조 마련됨) 가 Step FAILED / 부분 실패 (skipCount > 0) 자동 감지 → Discord 알림. 4 잡 코드 변경 0 으로 자동 적용

---

## 8. JobInstance UNIQUE 제약을 활용한 재실행 자동 차단

### 문제사항
- 운영자가 같은 회사 / 같은 날 잡을 두 번 트리거하면 어떻게 할 것인가
- Service 직접 호출 시절은 각 Service 가 동일 처리 이력을 DB 에서 SELECT 해 중복 가드 → 잡마다 가드 코드 + SELECT 비용 발생
- 멱등 가드 빠진 잡은 두 번째 실행 시 데이터 중복 처리 위험 (월차 두 번 적립, 연차 두 번 발생 등)

### 원인분석
- Spring Batch 의 BATCH_JOB_INSTANCE 는 (job_name, job_key) UNIQUE 제약. job_key 는 JobParameters 의 hash
- 같은 (companyId, targetDate) 로 두 번째 호출 → `JobInstanceAlreadyCompleteException` → 잡 자체가 안 돔
- 4 잡 모두 (companyId, targetDate) 로 통일하면 잡별 멱등 가드 코드 0 으로 동일 효과
- 회사별 분리 (전사 통합 1 인스턴스가 아님) → 한 회사 실패가 다른 회사 영향 0 + 회사별 추적
- 잡 이름이 다르면 같은 (companyId, targetDate) 도 별도 인스턴스 → HIRE 와 FISCAL 이 같은 키 써도 충돌 X (`annualGrantHireJob` vs `annualGrantFiscalJob`)

### 시도방법
- JobParameters 키 후보 비교: (targetDate) 전사 통합 vs (companyId, targetDate) 회사별 → 격리성 / 추적성 / 회사별 트리거 가능성 따져 후자 채택
- HIRE / FISCAL 같은 (companyId, targetDate) 사용 시 BATCH_JOB_INSTANCE 충돌 가능성 검토 → job_name 다르면 별도 인스턴스 확인

### 해결방법
- 4 잡 모두 JobParameters = `(companyId, targetDate)` 로 통일 (BalanceExpiry / AnnualGrantFiscal 와 동일 컨벤션)
- 두 번째 호출 시 `JobInstanceAlreadyCompleteException` catch → INFO 로그 ("이미 완료") 흡수
- Scheduler 가 회사별 정책/유형 누락을 선검증 → 누락 회사는 JobInstance 생성 자체 skip (불필요한 행 차단)
- **운영 효율**: 운영자가 잡 재실행해도 안전. "오늘 한 번 더 돌려도 되나?" 고민 불필요
- **데이터 정합성**: 같은 회사/같은 날 두 번 실행 자체가 차단 → 중복 처리 0
- **부수 효과**: misfire 정책을 FIRE_NOW 로 안전 상향 가능 (다음 항목)

---

## 9. misfire 정책 `DO_NOTHING` → `FIRE_NOW` 안전 상향

### 문제사항
- Phase 1 시점은 4 잡 모두 misfire = `DO_NOTHING` 보수적 선택 (Service 직접 호출이라 두 번 돌면 데이터 중복 위험)
- 노드 다운 / 스레드풀 포화 등으로 fire 시각 놓치면 다음 정상 시각까지 잡 누락
- 운영자가 수동 트리거 복구해야 함 → 알림 → 인지 → 트리거의 절차 비용

### 원인분석
- `DO_NOTHING` 의 보수적 선택 근거 = "두 번 도는 위험" → Phase 2 후 JobInstance UNIQUE 제약이 두 번째 실행 자체를 차단 → 위험 사라짐
- 즉 Phase 2 마이그레이션의 자연스러운 후속 단계
- MenstrualMonthlyGrant 는 추가로 Service 내부 동월 ACCRUAL 이력 가드 (이중 보호) → FIRE_NOW 안전성 더 강함

### 시도방법
- 잡별 멱등 보장 매트릭스 재작성:
  - 4 잡 모두: JobInstance UNIQUE → 회사 + 날짜 단위 멱등 보장
  - MenstrualMonthlyGrant: + Service 내부 동월 가드 → 이중 보호
  - BalanceExpiry: 만료 두 번 처리 시 expiredDays 부풀 위험 → `DO_NOTHING` 유지 (Spring Batch 멱등이지만 잡 정의상 보수적 선택)
  - Partition: 멱등 DDL → `FIRE_NOW` 유지

### 해결방법
- 4 SchedulerConfig 의 `buildTrigger()` 1 줄 변경: `withMisfireHandlingInstructionDoNothing()` → `withMisfireHandlingInstructionFireAndProceed()`
- 주석에 정책 + 근거 명시 (JobInstance UNIQUE 의존)
- **운영 효율**: misfire 시 즉시 만회 → 알림 / 수동 트리거 절차 사라짐 (놓친 잡이 자동 복구)
- **데이터 정합성**: JobInstance UNIQUE 가 이중 실행 자체 차단 → FIRE_NOW 안전성 보장
- **재발 방지**: BalanceExpiry 같이 잡 정의상 보수적 선택을 유지해야 하는 잡은 클래스 주석에 명시 (`DO_NOTHING` 유지 근거)

---

## 10. AnnualGrant HIRE 잡 Reader 선택 — JpaCursor JPQL 한계 우회

### 문제사항
- HIRE 잡 사원 조회 = 회사 + 입사기념일 매치 (`MONTH(empHireDate) = ? AND DAY(empHireDate) = ?`) + 비윤년 3/1 → 2/29 입사자 보정 병합
- 다른 3 잡은 모두 `JpaCursorItemReader` (JPQL) 로 표현 가능했지만 HIRE 만 표현 어려움
- 표준 패턴에서 벗어나면 후속 유지보수 비용 / 검증 매트릭스 갈림

### 원인분석
- 기존 `EmployeeRepository.findByCompanyIdAndHireMonthDayAndEmpStatusIn` 은 native query 기반 (JPQL 의 `FUNCTION('MONTH', ...)` 호환성 / 가독성 문제로 native 채택)
- `JpaCursorItemReader.queryString` 은 JPQL 만 받음 → native query 직접 사용 불가
- 2/29 보정 (비윤년 3/1 fire 시 2/29 입사자 추가 조회 + dedup 병합) 까지 reader 가 처리해야 함

### 시도방법
- `JpaPagingItemReader` + 다른 표현 시도 → 동일하게 JPQL 만 받음 → 한계 동일
- `JdbcCursorItemReader` → SQL 직접. Employee 엔티티 매핑 RowMapper 별도 작성 필요 → 기존 native query repository 재사용 불가, 비용 큼
- `ListItemReader<Employee>` → 사전 조회 결과 인메모리 감싸기. 매치자 수 적으면 안전
- 입사기념일 매치자 수 측정 → 회사당 평균 0 ~ 수십 명, 통상 100 명 미만 → 인메모리 부담 미미

### 해결방법
- `ListItemReader<Employee>` 채택. Reader 가 `EmployeeRepository.findByCompanyIdAndHireMonthDayAndEmpStatusIn` 직접 호출 + 2/29 보정 (HashSet dedup) 까지 수행 → 기존 검증된 native query 재사용
- Writer 에서 `yearsOfService < 2` 가드 (1 년차는 AnnualTransition 담당이라 reader 단계에서 거를 수 없음 — 입사기념일 == today 만 필터)
- 4 잡 중 HIRE 만 ListItemReader 사용. 클래스 주석에 선택 근거 명시 (native + 2/29 보정 + 매치자 수 적음)
- **운영 효율**: 기존 native 쿼리 + 2/29 보정 로직 재사용 → 검증된 코드 베이스 유지. 신규 SQL/RowMapper 작성 없음
- **확장성**: 향후 매치자 수가 폭증할 경우 `JdbcCursorItemReader` 로 마이그레이션 가능 (Spring Batch ItemReader 인터페이스 동일 → JobConfig 만 교체)

---

## 부록: 검증 쿼리 (운영 모니터링용)

```sql
-- 등록된 잡 목록
SELECT job_group, job_name, job_class_name
FROM QRTZ_JOB_DETAILS
ORDER BY job_group, job_name;

-- 트리거 cron + 다음 fire 시각 (KST 변환)
SELECT trigger_group, trigger_name, cron_expression, time_zone_id,
       FROM_UNIXTIME(next_fire_time / 1000) AS next_fire_kst
FROM QRTZ_CRON_TRIGGERS
JOIN QRTZ_TRIGGERS USING (sched_name, trigger_name, trigger_group)
ORDER BY next_fire_time;

-- 클러스터 노드 헬스
SELECT instance_name, FROM_UNIXTIME(last_checkin_time / 1000) AS last_checkin_kst,
       checkin_interval
FROM QRTZ_SCHEDULER_STATE;

-- fire 중인 트리거 (실시간)
SELECT entry_id, instance_name, trigger_name, trigger_group,
       FROM_UNIXTIME(fired_time / 1000) AS fired_kst, state
FROM QRTZ_FIRED_TRIGGERS;
```

### Phase 2 Spring Batch 모니터링

```sql
-- Phase 2 신규 잡 실행 이력 (회사별 / 날짜별)
SELECT i.job_name, e.status, e.exit_code,
       FROM_UNIXTIME(e.create_time / 1000) AS create_kst,
       FROM_UNIXTIME(e.end_time / 1000) AS end_kst
FROM BATCH_JOB_INSTANCE i
JOIN BATCH_JOB_EXECUTION e ON i.job_instance_id = e.job_instance_id
WHERE i.job_name IN ('monthlyAccrualJob', 'annualTransitionJob',
                     'annualGrantHireJob', 'menstrualMonthlyGrantJob')
ORDER BY e.create_time DESC LIMIT 50;

-- read / write / skip 카운트 (성능 + 부분 실패 추적)
SELECT s.step_name, s.status,
       s.read_count, s.write_count, s.commit_count, s.rollback_count, s.skip_count,
       FROM_UNIXTIME(s.start_time / 1000) AS start_kst,
       FROM_UNIXTIME(s.end_time / 1000) AS end_kst
FROM BATCH_STEP_EXECUTION s
WHERE s.step_name IN ('monthlyAccrualStep', 'annualTransitionStep',
                      'annualGrantHireStep', 'menstrualMonthlyGrantStep')
ORDER BY s.start_time DESC LIMIT 50;

-- 같은 (companyId, targetDate) 재실행 차단 검증
-- 정상이면 (job_name, company_id, target_date) 조합당 row 1 개만 존재 (중복 호출 차단됨)
SELECT i.job_name,
       MAX(CASE WHEN p.parameter_name = 'companyId' THEN p.parameter_value END) AS company_id,
       MAX(CASE WHEN p.parameter_name = 'targetDate' THEN p.parameter_value END) AS target_date,
       COUNT(DISTINCT i.job_instance_id) AS instance_cnt
FROM BATCH_JOB_INSTANCE i
JOIN BATCH_JOB_EXECUTION e ON i.job_instance_id = e.job_instance_id
JOIN BATCH_JOB_EXECUTION_PARAMS p ON e.job_execution_id = p.job_execution_id
WHERE i.job_name IN ('monthlyAccrualJob', 'annualTransitionJob',
                     'annualGrantHireJob', 'menstrualMonthlyGrantJob')
GROUP BY i.job_name, e.job_execution_id
HAVING instance_cnt > 1;
-- 결과 행이 0 이면 정상 (UNIQUE 제약 작동). > 0 이면 동일 키 중복 인스턴스 발생 → 조사 필요

-- 부분 실패 (skipCount > 0) 발생 잡 — Discord WARN 알림 발사 대상
SELECT i.job_name, s.step_name, s.read_count, s.write_count, s.skip_count,
       FROM_UNIXTIME(s.start_time / 1000) AS start_kst
FROM BATCH_STEP_EXECUTION s
JOIN BATCH_JOB_EXECUTION e ON s.job_execution_id = e.job_execution_id
JOIN BATCH_JOB_INSTANCE i ON e.job_instance_id = i.job_instance_id
WHERE s.skip_count > 0
ORDER BY s.start_time DESC LIMIT 30;
```
