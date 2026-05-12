# 결재 문서함 동적 검색 — QueryDSL 설계

> 결재 문서함은 **9개 문서함(대기/예정/참조열람/완료/수신/기안/임시/개인/부서) × 다중 선택 조건(제목·기안자·기간·양식·상태) × 결재선 기반 필터** 가 조합되는 화면입니다. JPQL 문자열 결합으로는 타입 안전성을 잃고, Specification 으로는 가독성이 죽기에 **QueryDSL + `BooleanBuilder`** 로 동적 쿼리를 일원화했습니다.

[← README로 돌아가기](../README.md)

---

## 1. 도메인 요구사항

### 1-1. 문서함이 9개, 각자 필터 규칙이 다름

| 문서함 | 핵심 조건 |
|--------|----------|
| 결재 대기 | 나=APPROVER, lineStatus=PENDING, **내 step = 최소 PENDING step** |
| 결재 예정 | 나=APPROVER, lineStatus=PENDING, **내 step > 최소 PENDING step** |
| 참조/열람 대기 | 나=REFERENCE/VIEWER, isRead=false |
| 결재 완료 | 나=APPROVER, lineStatus=APPROVED/REJECTED |
| 수신 문서함 | 모든 역할 참여 문서 (DRAFT 제외) |
| 기안 / 임시저장 | 기안자=나, status 분기 |
| 개인 폴더 | `PersonalFolderDocument` JOIN |
| 부서 문서함 | 부서원 기안 OR 부서원 결재라인 참여 |

### 1-2. 공통 조건이 모든 문서함에 들어가야 함

- `companyId` 필수
- 검색어(제목·기안자·문서번호)
- 기간(`createdAt` 범위)
- 양식 필터 — **양식 버전 박제 정책** 때문에 `formCode` 단위 매칭
- 상태 필터
- 보존연한 만료 제외

### 1-3. 헤더 배지에 들어갈 **문서함별 카운트** 가 필요

- 9개 문서함 × 매 요청 카운트 → 단순 구현 시 9~12회 쿼리 발산

---

## 2. 검토한 대안

| 옵션 | 장점 | 단점 | 채택 |
|------|------|------|------|
| JPQL 문자열 concat | 단순 | 타입 검증 불가, 런타임 오류, SQL Injection 표면 | ❌ |
| JPA Criteria API (Specification) | 타입 안전 | 극도로 verbose, 가독성 저하 | ❌ |
| 문서함 메서드별 정적 JPQL | 가독성 | 조건 조합 폭발(2^N), 공통 조건 중복 | ❌ |
| **QueryDSL + `BooleanBuilder`** | 타입 안전 + 동적 조립 + Q 클래스 재사용 | 초기 학습/세팅 비용 | ✅ |

---

## 3. 설계

### 3-1. 공통 필터 헬퍼

[`ApprovalDocumentCustomRepositoryImpl.java`](../collaboration-service/src/main/java/com/peoplecore/approval/repository/ApprovalDocumentCustomRepositoryImpl.java)

```java
private BooleanBuilder applyCommonFilters(UUID companyId, DocumentListSearchDto searchDto) {
    BooleanBuilder builder = new BooleanBuilder();
    builder.and(doc.companyId.eq(companyId));                 // 회사 필수
    if (searchDto == null) return builder;

    if (hasText(searchDto.getSearch())) {                     // 텍스트 검색
        builder.and(doc.docTitle.containsIgnoreCase(s)
                .or(doc.empName.containsIgnoreCase(s))
                .or(doc.docNum.containsIgnoreCase(s)));
    }
    if (searchDto.getStartDate() != null)
        builder.and(doc.createdAt.goe(searchDto.getStartDate().atStartOfDay()));
    if (searchDto.getEndDate() != null)
        builder.and(doc.createdAt.lt(searchDto.getEndDate().plusDays(1).atStartOfDay()));
    if (searchDto.getFormId() != null) {
        String formCode = approvalFormRepository.findById(...).map(...).orElse(null);
        if (formCode != null) builder.and(doc.formId.formCode.eq(formCode));
    }
    if (hasText(searchDto.getStatus()))
        builder.and(doc.approvalStatus.eq(ApprovalStatus.valueOf(searchDto.getStatus())));

    builder.and(notExpired());                                // 보존연한
    return builder;
}
```

- **빈 검색 조건은 자동으로 빠짐** → 9개 문서함이 모두 같은 헬퍼 재사용
- 문서함별 메서드는 **본인 고유 조건만 `.and()` 로 추가**

### 3-2. 결재선 기반 필터 — 위임까지 고려

```java
private BooleanExpression actorLineMatches(QApprovalLine actorLine, Long empId) {
    QApprovalDelegation delegation = new QApprovalDelegation("activeDelegation");
    LocalDate today = LocalDate.now();
    return actorLine.empId.eq(empId)
        // 위임 처리 완료 라인 — 원 결재자가 자기 문서함에서 봐야 함
        .or(actorLine.isDelegated.eq(true).and(actorLine.lineDelegatedId.eq(empId)))
        // 활성 위임의 대리자 — 위임자 라인을 내 문서함에 노출
        .or(JPAExpressions.selectOne().from(delegation)
                .where(delegation.empId.eq(actorLine.empId),
                       delegation.deleEmpId.eq(empId),
                       delegation.isActive.eq(true),
                       delegation.startAt.loe(today),
                       delegation.endAt.goe(today))
                .exists());
}
```

> 결재선 매칭은 **본인 / 위임된 라인의 원 결재자 / 활성 위임의 대리자** 3가지를 OR 로 묶음. 분기 조건이 6~7곳에서 동일하게 쓰여서 **헬퍼로 추출 → 회귀 위험 0**.

### 3-3. 문서함별 카운트 — 9쿼리 → 2쿼리

배지용 카운트를 단순 구현하면 9개 문서함 × 별도 count() 가 됩니다. **`CaseBuilder` 로 한 번에 합산** 해서 결재선 기반 7개를 1쿼리에 모음.

```java
// 결재선 기반 7개 문서함 카운트 — 단일 쿼리
Tuple result = jpaQueryFactory
    .select(waitingCase.sum(), ccViewCase.sum(), upcomingCase.sum(),
            approvedCase.sum(), ccViewBoxCase.sum(), inboxCase.sum())
    .from(line).join(line.docId, doc)
    .where(actorLineMatches(line, empId), doc.companyId.eq(companyId), notExpired())
    .fetchOne();
```

| 항목 | Before | After |
|------|--------|-------|
| 카운트 쿼리 수 | 9~12회 | **2~3회** (결재선 1 + 기안자 1 + 부서/폴더 보조) |
| N+1 위험 | 문서함 수에 비례 | 상수 |

### 3-4. 페이징 — count 쿼리 생략 최적화

```java
private Page<DocumentListResponseDto> executedWithPagination(
        JPAQuery<DocumentListResponseDto> contentQuery,
        JPAQuery<Long> countQuery, Pageable pageable) {
    List<DocumentListResponseDto> content = contentQuery
            .offset(pageable.getOffset()).limit(pageable.getPageSize()).fetch();
    return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
}
```

- `PageableExecutionUtils` — content 수 < pageSize 이면 **count 쿼리 자체를 생략**. 단건 페이지 응답에서 불필요한 왕복 제거.

### 3-5. 첨부파일 존재 여부 — N+1 차단 서브쿼리

```java
private BooleanExpression hasAttachment() {
    return JPAExpressions.selectOne()
            .from(attachment).where(attachment.docId.eq(doc)).exists();
}
```

- 목록 행마다 첨부 조회하는 N+1 → 단일 쿼리 내 EXISTS 서브쿼리로 1회 처리.

---

## 4. 효과

| 항목 | 결과 |
|------|------|
| 9개 문서함 공통 조건 | 헬퍼 하나로 일원화 (중복 0) |
| 결재선 매칭 분기(위임 포함) | 헬퍼 한 군데, 6+ 메서드에서 재사용 |
| 헤더 배지 카운트 | 9~12쿼리 → **2~3쿼리** |
| 타입 검증 | 컴파일 타임 (런타임 SQL 오류 0) |
| 페이지 응답 | `PageableExecutionUtils` 로 count 생략 |

---

## 5. 운영 규칙

| 규칙 | 이유 |
|------|------|
| 모든 동적 조건은 `BooleanBuilder` + `BooleanExpression` 헬퍼로 추출 | 중복 제거, 회귀 안전 |
| 결재선/위임 매칭은 반드시 `actorLineMatches()` 경유 | 위임 정책 변경 시 한 군데만 수정 |
| 첨부·읽음 같은 부속 정보는 EXISTS 서브쿼리 | N+1 차단 |
| 문서함별 카운트는 `CaseBuilder().sum()` 으로 합치기 | 쿼리 왕복 최소화 |
| Q 클래스는 필드 선언부에 캐싱 | new 비용 제거 |

---

## 6. 결론

- 결재 문서함처럼 **조건 조합 × 문서함 종류** 가 폭발하는 도메인은 QueryDSL 의 동적 조립이 사실상 강제 선택.
- **공통 헬퍼 + 결재선 매칭 헬퍼 + `CaseBuilder` 합산** 3가지를 도입해 코드 중복은 거의 0, 카운트 쿼리는 한 자릿수로 압축.
- JPQL 문자열 결합 대비 **타입 안전성 + SQL Injection 표면 제거** 가 보너스.
