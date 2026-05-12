# Kafka 비동기 이벤트 — 서비스 간 결합도 분리

> 결재 도메인이 알림·HR·근태·휴가·급여 등 타 서비스 모듈과 **동기 HTTP** 로 묶이면 한쪽 장애가 결재 전체 장애로 전파됩니다. 핵심 트랜잭션과 부수/후행 처리를 **Kafka 토픽으로 분리**하고, 정합성 위반은 **역방향 보상 이벤트**로 풀어 MSA 의 의도된 decoupling 을 확보했습니다.

[← README로 돌아가기](../README.md)

---

## 1. 왜 비동기 이벤트인가

### 1-1. 동기 결합의 비용

```
[결재 승인] ── HTTP ──> [알림 발송]
            ── HTTP ──> [HR 후속처리]
            ── HTTP ──> [근태 반영]
            ── HTTP ──> [캐시 무효화]
```

- 알림 서비스 5초 지연 → **결재 응답도 5초 지연**
- HR 서비스 일시 장애 → **결재 트랜잭션 실패**
- 부수 작업이 핵심 도메인의 SLA 를 좌우 → MSA 의도 훼손

### 1-2. 분산 트랜잭션의 한계

- 2PC(`XA`) — MySQL/Kafka 같은 이종 시스템 묶기 어렵고, 도입 비용 과다
- Saga — 보상 트랜잭션 자체는 합리적이나, 동기 채널로 묶으면 1-1 문제 그대로

→ **이벤트 발행 후 결재 트랜잭션은 즉시 종료**, 후행은 컨슈머가 책임지는 구조가 현실적.

---

## 2. 토픽 설계

### 2-1. 단방향 알림 / 캐시 무효화

| 토픽 | 발행 | 수신 | 목적 |
|------|------|------|------|
| `hr-dept-updated` | hr-service | collaboration-service | 부서 변경 시 캐시 무효화 |
| `hr-title-updated` | hr-service | collaboration-service | 직책 변경 시 캐시 무효화 |
| `alarm-event` | 다수 | collaboration-service | 알림 영구화 + 푸시 |

### 2-2. 결재 흐름 — 양방향 (도메인 이벤트)

| 토픽 | 발행 | 수신 | 의미 |
|------|------|------|------|
| `overtime-approval-doc-created` | collab | hr-service | OT 신청서 기안 알림 |
| `overtime-approval-result` | collab | hr-service | OT 최종 결재 결과 |
| `overtime-request-rejected-by-hr` | hr-service | collab | **보상** — HR 한도 BLOCK 시 결재 자동 반려 |
| `attendance-modify-*` | 양방향 | | 근태 정정 동일 패턴 |
| `vacation-approval-*` | 양방향 | | 휴가 동일 패턴 |
| `payroll-approval-*` / `severance-approval-*` | 양방향 | | 급여/퇴직금 동일 패턴 |
| `resign-approved` | collab | hr-service | 퇴사 결재 결과 |

> 모든 컨슈머 그룹 ID 는 **수신 서비스명** 으로 고정 — 누가 받아도 무관한 무상태 처리. 동일 토픽에 다중 컨슈머가 필요하면 그룹을 분리.

---

## 3. 핵심 패턴

### 3-1. 단방향 무효화 — fire-and-forget

[`HrEventConsumer.java`](../collaboration-service/src/main/java/com/peoplecore/client/consumer/HrEventConsumer.java)

```java
@KafkaListener(topics = "hr-dept-updated", groupId = "collaboration-service")
public void handleDeptUpdated(String message) {
    try {
        DeptUpdatedEvent event = objectMapper.readValue(message, DeptUpdatedEvent.class);
        hrCacheService.evictDept(event.getDeptId());
    } catch (Exception e) {
        log.error("부서 변경 이벤트 처리 실패 error = {}", e.getMessage());
    }
}
```

- 캐시 무효화 실패는 **로그만** — 다음 캐시 갱신에서 자연 복구
- 결재 트랜잭션과 완전 독립

### 3-2. 보상 트랜잭션 — 양방향 정합성 보장

OT 결재 시나리오 — 결재선이 통과해도 HR 측에서 **주간 한도 초과 BLOCK** 정책에 걸리면 결재를 자동 반려시켜야 함.

**발행 측** [`OvertimeRequestRejectedByHrPublisher.java`](../hr-service/src/main/java/com/peoplecore/attendance/publisher/OvertimeRequestRejectedByHrPublisher.java)

```java
public void publish(OvertimeRequestRejectedByHrEvent event) {
    try {
        kafkaTemplate.send(TOPIC, objectMapper.writeValueAsString(event));
        log.info("[OvertimeRequestRejected] 발행 - docId={}", event.getApprovalDocId());
    } catch (Exception e) {
        log.error("[OvertimeRequestRejected] 발행 실패 - docId={}", event.getApprovalDocId());
    }
}
```

**수신 측 — Retry + DLT** [`OvertimeRequestRejectedByHrConsumer.java`](../collaboration-service/src/main/java/com/peoplecore/approval/consumer/OvertimeRequestRejectedByHrConsumer.java)

```java
@RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2))
@KafkaListener(topics = "overtime-request-rejected-by-hr", groupId = "collaboration-service")
@Transactional
public void handleRejectedByHr(String message) {
    OvertimeRequestRejectedByHrEvent event = objectMapper.readValue(...);

    ApprovalDocument document = documentRepository
        .findByDocIdAndCompanyId(event.getApprovalDocId(), event.getCompanyId())
        .orElseThrow(() -> new BusinessException("자동 반려 대상 문서를 찾을 수 없습니다."));

    /* 멱등성 — 이미 처리된 문서는 스킵 */
    if (document.getApprovalStatus() != ApprovalStatus.PENDING) {
        log.info("[Kafka] 이미 처리된 문서 — OT 자동 반려 스킵.");
        return;
    }

    document.reject();
    lineRepository.findByDocId_DocIdOrderByLineStep(document.getDocId()).stream()
        .filter(l -> l.getApprovalLineStatus() == ApprovalLineStatus.PENDING)
        .forEach(l -> l.reject(event.getRejectReason()));
}

@DltHandler
public void handleDlt(String message) {
    log.error("[DLT] OT 자동 반려 최종 실패, message: {}", message);
}
```

| 요소 | 역할 |
|------|------|
| `@RetryableTopic(attempts=3, backoff=exp)` | 일시 장애 자동 재시도 — Kafka Connect 가 retry 토픽 자동 생성 |
| 멱등성 가드 (`status != PENDING` 스킵) | 재시도/중복 발행 시에도 동일 결과 보장 |
| `@DltHandler` | 3회 실패 메시지는 DLT 토픽으로 격리, 운영자 수동 개입 |
| `@Transactional` | DB 반영은 원자적 — 부분 반려 방지 |

---

## 4. 운영 규칙

| 규칙 | 이유 |
|------|------|
| 결재 핵심 트랜잭션이 외부 서비스 호출에 의존하지 않음 | 부수 장애가 결재로 전파 X |
| 모든 컨슈머는 **멱등성** 보장 (상태 기반 스킵) | 재시도/중복 발행 안전 |
| 일시 장애 = `@RetryableTopic`, 반복 실패 = `@DltHandler` | 자동 복구와 운영 가시성 분리 |
| 도메인 이벤트 명명 = `{도메인}-{사실}` (`-rejected-by-hr` 등) | 토픽 이름만으로 흐름 파악 |
| 컨슈머 그룹 ID = 수신 서비스명 고정 | 다중 파드에서도 단일 처리 보장 |
| 발행 실패는 로그만, 결재 트랜잭션은 진행 | 채널 장애가 본 흐름을 막지 않음 |

---

## 5. 얻은 것

| 항목 | 동기 호출 | Kafka 분리 |
|------|----------|-----------|
| 알림 지연 | 결재 응답 지연 누적 | 결재 응답 무관 |
| HR 일시 장애 | 결재 실패 | 결재 성공, 후행만 재시도 |
| 다중 컨슈머 추가 | 발행자 코드 수정 | 토픽 구독자 추가만 |
| 정합성 위반 | 즉시 실패 | 역방향 보상 이벤트로 복구 |
| 운영 가시성 | 분산 로그 추적 | DLT + 토픽 단위 모니터링 |

---

## 6. 결론

- MSA 에서 동기 결합은 **장애와 지연을 그대로 전파** — Kafka 비동기 이벤트로 핵심 트랜잭션과 부수 작업을 분리.
- 정합성은 분산 트랜잭션 대신 **도메인 이벤트 + 멱등 컨슈머 + 역방향 보상** 으로 풀어 운영 부담을 낮춤.
- Retry / DLT 인프라까지 묶어 **자동 복구되는 영역과 사람이 봐야 하는 영역** 을 명확히 구분.
