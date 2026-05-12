# Redis 분산 캐싱 — HR 데이터 Cache-Aside

> 결재 문서가 조회될 때마다 부서·직책·회사 등 HR 데이터를 다시 가져오면, **결재 ↔ HR** 사이에 호출량과 의존도가 동시에 쌓입니다. 변경 빈도가 낮은 HR 데이터를 **Redis 에 Cache-Aside 로 캐싱** 하고, 변경 시점에는 **Kafka 이벤트로 즉시 무효화** 해 정합성을 유지하는 구조를 사용합니다.

[← README로 돌아가기](../README.md)

---

## 1. 왜 Redis 인가

### 1-1. 도메인 특성

- 결재 문서 1건 조회 → 부서/직책/회사 정보 5~15회 참조 (기안자, 결재선, 참조/열람자)
- HR 데이터(`Department`, `Title`, `Company`, `Employee`) — **조회 빈도 ↑ / 변경 빈도 ↓**
- 결재는 멀티 파드 환경 — 파드 로컬 캐시는 파드별 상태 상이

### 1-2. 검토한 대안

| 옵션 | 장점 | 단점 | 채택 |
|------|------|------|------|
| 매번 HR 호출 | 일관성 강함 | 호출 비용/지연 누적, HR 장애 전파 | ❌ |
| 로컬 캐시 (Caffeine) | 빠름 (μs) | 파드별 상태 다름, 변경 전파 불가 | ❌ |
| **Redis 분산 캐시** | 파드 공유, ms 응답, TTL/Eviction | 외부 의존성 1개 추가 | ✅ |

---

## 2. 설계 — Cache-Aside + 이벤트 무효화

```
[조회] → Redis HIT ─────────────────────────────────────► return cached
       │
       └ MISS → HR 서비스 호출 → 응답 Redis 저장(TTL 1h) → return

[HR 변경] → 토픽 발행(hr-dept-updated 등) → 컨슈머 → evict(key)
```

### 2-1. 캐시 키 설계

[`HrCacheService.java`](../collaboration-service/src/main/java/com/peoplecore/client/component/HrCacheService.java)

```java
private static final String DEPT_KEY    = "hr:dept:";
private static final String COMPANY_KEY = "hr:company:";
private static final String TITLE_KEY   = "hr:title:";
private static final String EMP_KEY     = "hr:emp:";
private static final Duration TTL = Duration.ofHours(1);
```

- 네임스페이스 prefix(`hr:`) + 엔티티 + id → 충돌 0, 와일드카드 무효화 가능
- TTL 1시간 — 이벤트 누락 시 자연 복구

### 2-2. 조회 — 캐시 우선, 실패는 fail-soft

```java
public DeptInfoResponse getDept(Long deptId) {
    String key = DEPT_KEY + deptId;

    /* ① Redis 캐시 조회 — 실패해도 HR 호출로 진행 */
    try {
        DeptInfoResponse cached = (DeptInfoResponse) redisTemplate.opsForValue().get(key);
        if (cached != null) return cached;
    } catch (Exception e) {
        log.warn("Redis 조회 실패, HR 서비스 직접 호출 deptId={}", deptId);
    }

    /* ② HR 호출 — 실패는 도메인 예외로 변환 */
    DeptInfoResponse response;
    try {
        response = hrServiceClient.getDept(deptId);
    } catch (BusinessException e) {
        throw e;
    } catch (Exception e) {
        throw new BusinessException("HR 서비스 연결 실패: 부서 정보를 조회할 수 없습니다.",
                                    HttpStatus.SERVICE_UNAVAILABLE);
    }

    /* ③ Redis 저장 실패해도 채번/조회는 계속 — 다음 요청에 재시도 */
    try { redisTemplate.opsForValue().set(key, response, TTL); }
    catch (Exception e) { log.warn("Redis 저장 실패 deptId={}", deptId); }

    return response;
}
```

> **fail-soft 원칙** — Redis 장애가 결재 도메인 장애로 전파되지 않도록 모든 캐시 호출은 try-catch 로 격리. Redis 죽어도 결재는 (느리게나마) 동작.

### 2-3. 일괄 조회 — N+1 차단

문서 1건당 결재선 다수 → 사원 정보 N건. 일괄 조회 메서드로 개별 호출을 묶음.

```java
public List<EmployeeSimpleResDto> getEmployees(List<Long> empIds) {
    List<EmployeeSimpleResDto> result = new ArrayList<>();
    List<Long> missedIds = new ArrayList<>();

    /* ① Redis 개별 HIT 수집 */
    for (Long empId : empIds) {
        EmployeeSimpleResDto cached = (EmployeeSimpleResDto) redisTemplate.opsForValue().get(EMP_KEY + empId);
        if (cached != null) { result.add(cached); continue; }
        missedIds.add(empId);
    }

    /* ② MISS 분만 HR 한 번에 호출 */
    if (!missedIds.isEmpty()) {
        List<EmployeeSimpleResDto> fetched = hrServiceClient.getEmployees(missedIds);
        for (EmployeeSimpleResDto emp : fetched) {
            redisTemplate.opsForValue().set(EMP_KEY + emp.getEmpId(), emp, TTL);
            result.add(emp);
        }
    }
    return result;
}
```

- N건 HIT + (M건만) HR 1회 호출 → 결재 목록 진입 시 HR 호출이 사실상 상수에 수렴.

### 2-4. 부서 조상 체인 — 캐시 재귀 활용

권한 검증/문서함 가시성에 부서 계층이 필요. 부모 부서를 캐시 경유로 탐색.

```java
public List<Long> getDeptAncestors(Long deptId) {
    List<Long> ancestors = new ArrayList<>();
    Long current = deptId;
    int depth = 0;
    while (current != null) {
        if (++depth > MAX_DEPT_DEPTH) break;  // 사이클 방어
        ancestors.add(current);
        DeptInfoResponse dept = getDept(current);   // ← 캐시 경유 재귀
        current = dept == null ? null : dept.getParentDeptId();
    }
    return ancestors;
}
```

- 한 부서당 Redis 1회 → 깊이 N 이면 N회 (대부분 캐시 HIT)
- HR 직접 호출 시 N번 왕복이 발생했을 경로를 캐시로 흡수

---

## 3. 무효화 — Kafka 이벤트로 즉시 갱신

HR 측에서 부서/직책이 바뀌면 캐시는 즉시 죽어야 함. **TTL 만 의존하면 최대 1시간 stale.**

[`HrEventConsumer.java`](../collaboration-service/src/main/java/com/peoplecore/client/consumer/HrEventConsumer.java)

```java
@KafkaListener(topics = "hr-dept-updated", groupId = "collaboration-service")
public void handleDeptUpdated(String message) {
    DeptUpdatedEvent event = objectMapper.readValue(message, DeptUpdatedEvent.class);
    hrCacheService.evictDept(event.getDeptId());
}

@KafkaListener(topics = "hr-title-updated", groupId = "collaboration-service")
public void handleTitleUpdated(String message) { ... }
```

- HR 트랜잭션 커밋 → 이벤트 발행 → 캐시 evict → 다음 조회 시 자동 재적재
- 컨슈머 실패해도 **TTL 1시간** 이 안전망

---

## 4. 운영 규칙

| 규칙 | 이유 |
|------|------|
| 모든 Redis 호출은 try-catch 로 격리 (fail-soft) | Redis 장애가 결재 장애로 전파 X |
| 캐시 키는 `hr:{entity}:{id}` 네임스페이스 prefix | 충돌 방지, 와일드카드 evict 가능 |
| TTL = 1시간 | 이벤트 누락 시 자연 복구 |
| 다건 조회는 캐시 HIT 분리 + MISS 분만 일괄 호출 | HR 호출량 상수화 |
| HR 변경은 반드시 Kafka 이벤트 발행 | 캐시 즉시 동기화 |
| 캐시 저장 실패는 로그만, 본 로직 계속 | 부속 장애가 본 흐름 차단 X |

---

## 5. 얻은 것

| 항목 | 매번 HR 호출 | Redis Cache-Aside |
|------|-------------|-------------------|
| 결재 문서 조회 응답 | HR 호출 N회 누적 | 캐시 HIT 시 ms 단위 |
| HR 서비스 부하 | 결재 트래픽에 비례 | 변경 시점 + 캐시 만료 시만 |
| 멀티 파드 일관성 | 파드별 호출 차이 | 공유 캐시로 동일 응답 |
| HR 장애 시 결재 | 즉시 영향 | 캐시 HIT 분은 정상 응답 |
| 정합성 (변경 반영) | 즉시 | 이벤트 기반 즉시 + TTL 안전망 |

---

## 6. 결론

- 결재 ↔ HR 처럼 **조회 빈도 ↑ / 변경 빈도 ↓** 한 경계는 Cache-Aside 가 거의 항상 정답.
- **fail-soft + Kafka 이벤트 무효화 + TTL 안전망** 3중 구조로 캐시 일관성과 가용성을 동시에 확보.
- 같은 패턴(`hrCacheService.getXxx()` + `hr-xxx-updated` 토픽) 을 다른 도메인 경계에도 그대로 복제할 수 있어 확장 비용 낮음.
