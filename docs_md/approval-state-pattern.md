# 결재 문서 상태 관리 — State Pattern

> 결재 문서가 가질 수 있는 5개 상태(`DRAFT / PENDING / APPROVED / REJECTED / CANCELED`)와 상태별 허용 동작(승인/반려/회수/재상신/수정)을 **State Pattern** 으로 캡슐화해, if-else 분기 없이 OCP를 지키며 신규 상태를 확장할 수 있도록 설계했습니다.

[← README로 돌아가기](../README.md)

---

## 1. 왜 상태 패턴인가

결재 문서는 상태에 따라 **같은 메서드라도 동작이 달라야** 합니다.

| 상태 | 승인 | 반려 | 회수 | 상신 | 재기안 | 수정/삭제 |
|------|------|------|------|------|--------|----------|
| `DRAFT` (임시저장) | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ |
| `PENDING` (진행중) | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `APPROVED` (승인) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `REJECTED` (반려) | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| `CANCELED` (회수) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

분기 구현 시 다음과 같이 흩어집니다.

```java
// ❌ 분기가 서비스 곳곳에 퍼지는 패턴
if (status == DRAFT) { ... }
else if (status == PENDING) { ... }
else if (status == REJECTED) { ... }
else throw new BusinessException("처리할 수 없는 상태");
```

- 신규 상태 추가 시 `approve / reject / recall / submit / resubmit` 등 모든 메서드를 열어 분기 보강 → **OCP 위반**
- 한 군데라도 누락하면 잘못된 전이가 통과해 데이터 무결성 손상
- 도메인 규칙이 코드 전반에 산재 → 가독성·테스트성 ↓

---

## 2. 검토한 대안

| 옵션 | 장점 | 단점 | 채택 |
|------|------|------|------|
| if-else / switch 분기 | 단순 | 상태 추가마다 모든 메서드 분기 수정, 누락 위험 | ❌ |
| enum 메서드 내부 분기 | 분기가 한 곳에 모임 | 메서드별 거대 switch 누적, 가독성 저하 | ❌ |
| **State Pattern (인터페이스 + 구현체)** | 상태별 캡슐화, 확장 시 클래스 추가만 | 클래스 수 증가 | ✅ |

---

## 3. 최종 설계

### 3-1. 상태 인터페이스

[`ApprovalState.java`](../collaboration-service/src/main/java/com/peoplecore/approval/entity/status/ApprovalState.java)

```java
public interface ApprovalState {
    void approve(ApprovalDocument document);
    void reject(ApprovalDocument document);
    void recall(ApprovalDocument document);
    void submit(ApprovalDocument document);

    /* 결재 처리(승인/반려/회수) 가능 여부 — PENDING 만 통과 */
    default void ensureOpenForApproval() {
        throw new BusinessException("결재 진행 중인 문서만 처리할 수 있습니다.");
    }

    /* 재기안 가능 여부 — REJECTED 만 통과 */
    default void ensureResubmittable() {
        throw new BusinessException("반려된 문서만 재기안할 수 있습니다.");
    }

    /* 임시저장 단계 작업(수정/삭제/정식 상신) 가능 여부 — DRAFT 만 통과 */
    default void ensureDraftStage() {
        throw new BusinessException("임시 저장 문서만 수정/삭제/상신할 수 있습니다.");
    }
}
```

> **default throw** — 인터페이스에 기본 거부를 두고, **허용 상태만 오버라이드해서 통과**시키는 구조. 신규 상태 클래스를 만들 때 아무 것도 안 해두면 자동으로 안전한 거부가 적용됨.

### 3-2. 상태별 구현체

[`PendingState.java`](../collaboration-service/src/main/java/com/peoplecore/approval/entity/status/PendingState.java) — 결재 진행 중인 상태에서만 승인·반려·회수 허용

```java
public class PendingState implements ApprovalState {
    @Override public void approve(ApprovalDocument document) {
        document.changeStatus(ApprovalStatus.APPROVED);
        document.complete();
    }
    @Override public void reject(ApprovalDocument document) {
        document.changeStatus(ApprovalStatus.REJECTED);
        document.complete();
    }
    @Override public void recall(ApprovalDocument document) {
        document.changeStatus(ApprovalStatus.CANCELED);
        document.complete();
    }
    @Override public void submit(ApprovalDocument document) {
        throw new BusinessException("이미 결재 진행 중인 문서입니다.");
    }
    @Override public void ensureOpenForApproval() { /* 통과 */ }
}
```

다른 상태도 동일 패턴 — [`DraftState`](../collaboration-service/src/main/java/com/peoplecore/approval/entity/status/DraftState.java) / [`ApprovedState`](../collaboration-service/src/main/java/com/peoplecore/approval/entity/status/ApprovedState.java) / [`RejectedState`](../collaboration-service/src/main/java/com/peoplecore/approval/entity/status/RejectedState.java) / [`CanceledState`](../collaboration-service/src/main/java/com/peoplecore/approval/entity/status/CanceledState.java)

### 3-3. enum 으로 상태 ↔ 구현체 바인딩

[`ApprovalStatus.java`](../collaboration-service/src/main/java/com/peoplecore/approval/entity/ApprovalStatus.java)

```java
@Getter
public enum ApprovalStatus {
    DRAFT(new DraftState()),
    PENDING(new PendingState()),
    APPROVED(new ApprovedState()),
    REJECTED(new RejectedState()),
    CANCELED(new CanceledState());

    private final ApprovalState state;

    ApprovalStatus(ApprovalState state) {
        this.state = state;
    }
}
```

### 3-4. 호출부 — 분기 0

```java
// ✅ 서비스는 상태에게 위임만 한다
document.getApprovalStatus().getState().approve(document);
```

- 어떤 상태든 호출은 동일
- 분기는 각 State 클래스 안에서 자기 자신만 책임

---

## 4. 얻은 것

| 항목 | 분기형 | State Pattern |
|------|--------|---------------|
| 신규 상태 추가 | 모든 메서드 분기 수정 | **클래스 1개 추가** |
| 잘못된 전이 차단 | 분기 누락 시 통과 위험 | `default throw` 로 자동 차단 |
| 단위 테스트 | 상태 조합별 분기 폭증 | State 클래스 단위 격리 테스트 |
| 도메인 규칙 위치 | 서비스 전반에 산재 | 상태 클래스 내부에 응집 |

---

## 5. 적용 규칙

| 규칙 | 이유 |
|------|------|
| 상태별 분기 코드는 서비스가 아닌 **State 구현체 안에** | 도메인 규칙 응집 / OCP 보존 |
| 새 상태 추가 = `ApprovalState` 구현체 + `ApprovalStatus` enum 항목만 | 호출부 변경 0 |
| 거부가 기본, 허용이 예외 (`default throw` + 오버라이드) | 안전한 디폴트 — 깜빡해도 잘못된 전이 통과 X |
| 상태 전이는 반드시 `document.changeStatus(...)` 경유 | 영속성 컨텍스트 추적 및 후속 훅(`complete()`) 발동 |

---

## 6. 결론

- 결재처럼 **상태에 따라 동일 메서드 동작이 갈리는 도메인** 은 분기보다 State Pattern 이 자연스러움.
- `default throw + 오버라이드 허용` 구조로 신규 상태는 클래스 1개 추가, 호출부는 무변경.
- 도메인 규칙이 한 곳에 응집되어 **단위 테스트 / 회귀 안전성 / 가독성** 모두 향상.
