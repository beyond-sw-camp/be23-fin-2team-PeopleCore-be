# 급여/퇴직 지급결의서 전자결재 양식 리팩터링 가이드

> **작성일**: 2026-04-23
> **배경**: 초기에는 전자결재 양식이 MinIO에 자동 업로드되지 않는다고 오해하여, hr-service에서 `급여지급결의서.html` / `퇴직급여지급결의서.html`을 별도 버킷(`approval-form`)에 수동 업로드하고 `ApprovalHtmlTemplateLoader`로 직접 읽는 구조를 만들었음.
>
> 실제로는 **collaboration-service가 회사 생성 시점에 자동으로 양식을 DB+MinIO에 등록**하고 있어, hr-service의 중복 로직 전부를 제거하고 collab의 자동등록 결과를 재사용하는 방향으로 전환한다.

---

## 목차

1. [현재 collaboration-service의 자동등록 동작](#1-현재-collaboration-service의-자동등록-동작)
2. [기존 hr-service 중복 구조와 문제점](#2-기존-hr-service-중복-구조와-문제점)
3. [신규 아키텍처 — 역할 분담](#3-신규-아키텍처--역할-분담)
4. [collaboration-service 쪽 선결 과제 (팀원 협업)](#4-collaboration-service-쪽-선결-과제-팀원-협업)
5. [hr-service 쪽 작업 — 제거 리스트](#5-hr-service-쪽-작업--제거-리스트)
6. [hr-service 쪽 작업 — 신규/수정 리스트](#6-hr-service-쪽-작업--신규수정-리스트)
7. [파일별 상세 변경 사항](#7-파일별-상세-변경-사항)
8. [전환 순서 체크리스트](#8-전환-순서-체크리스트)
9. [검증 시나리오](#9-검증-시나리오)

---

## 1. 현재 collaboration-service의 자동등록 동작

### 흐름 요약

```
[회사 생성 (hr-service CompanyService)]
   │
   ├─ Kafka 토픽 "company-folder-init" 발행 (CompanyCreateEvent)
   │
   ▼
[collaboration-service: CompanyFolderInitConsumer]
   │
   ├─ formService.initFormFolder(companyId)
   │    │
   │    ├─ ApprovalFormFolder "양식모음" 생성 (루트)
   │    ├─ 서브폴더 7개 생성: 스크립트 양식, 보고-시행문, 회계-총무, 일반기안, 휴가, 출장, 인사
   │    ├─ classpath: default-forms/{folderName}/*.html 스캔
   │    ├─ 각 파일 → ApprovalForm 엔티티 저장 (DB)
   │    │     formCode:
   │    │       - FIXED_FORM_CODES 매칭 → 계약 코드 (예: PAYROLL_RESOLUTION)
   │    │       - 미매칭 → "{formName}_001" 자동 생성
   │    ├─ MinIO 업로드: forms/{companyId}/{formCode}_v{version}.html
   │    └─ CommonCode 테이블에도 등록
   │
   └─ FileFolderService로 전사 파일함도 같이 생성
```

### 핵심 파일 경로

| 역할 | 경로 |
|------|------|
| 자동등록 소비자 | `collaboration-service/src/main/java/com/peoplecore/approval/consumer/CompanyFolderInitConsumer.java` |
| 자동등록 로직 | `collaboration-service/src/main/java/com/peoplecore/approval/service/ApprovalFormService.java#initFormFolder` |
| 시드 HTML 파일 | `collaboration-service/src/main/resources/default-forms/보고-시행문/급여지급결의서.html` |
| 시드 HTML 파일 | `collaboration-service/src/main/resources/default-forms/보고-시행문/퇴직급여지급결의서.html` |
| formCode 계약 맵 | `ApprovalFormService.java` 상단의 `FIXED_FORM_CODES` 상수 |

### 결재 양식 계약 formCode (FIXED_FORM_CODES)

```java
private static final Map<String, String> FIXED_FORM_CODES = Map.of(
    "휴가/초과근로신청서",      "OVERTIME_REQUEST",
    "휴가/휴가신청서",          "VACATION_REQUEST",
    "인사/사직서 #2",           "RESIGNATION",
    "보고-시행문/급여지급결의서", "PAYROLL_RESOLUTION",     // ← 급여 결의서
    "보고-시행문/퇴직금지급결의서","SEVERANCE_RESOLUTION",  // ← ⚠️ 실제 파일명 불일치(섹션 4 참조)
    "일반기안/근태정정신청서",   "ATTENDANCE_MODIFY"
);
```

### 내부 REST API (이미 존재)

collaboration-service가 hr-service에 제공 중:

| 메서드 | 경로 | 용도 |
|--------|------|------|
| `POST` | `/approval/init/formfolder` | 회사 생성 시 수동 트리거 (이벤트 실패 대비) |
| `GET`  | `/approval/forms/by-code?formCode=X` | formCode → formId 조회 |

hr-service 측 클라이언트는 `CollaborationClient.getFormIdByCode()`로 호출 중.

---

## 2. 기존 hr-service 중복 구조와 문제점

### 기존 구조

```
hr-service
├── pay/approval/
│   ├── ApprovalFormType.java            ← 자체 enum (formCode 정의)
│   ├── ApprovalHtmlTemplateLoader.java  ← 자체 MinIO 로더 (@PostConstruct)
│   ├── PayrollApprovalDraftService.java ← templateLoader 사용
│   └── SeveranceApprovalDraftService.java
```

### 문제점

| # | 문제 | 영향 |
|---|------|------|
| 1 | `ApprovalHtmlTemplateLoader`가 `@PostConstruct`로 `approval-form` 버킷을 조회 | 팀원 로컬에 해당 버킷/파일 없으면 **hr-service 기동 자체가 실패** (`NoSuchBucket`) |
| 2 | MinIO 객체명이 `급여지급결의서.html` (한글 파일명) | collab가 올리는 `forms/{companyId}/{formCode}_v{version}.html` 과 **완전히 다른 위치**. 심지어 bucketName(`approval-form`)도 collab 쪽 버킷과 다를 가능성 |
| 3 | `ApprovalFormType.formCode`가 `PAYROLL_PAYMENT`, `RETIREMENT_SEVERANCE` | collab의 계약값(`PAYROLL_RESOLUTION`, `SEVERANCE_RESOLUTION`)과 **불일치** → 결재 상신/조회 조인 실패 |
| 4 | 템플릿 내용이 이중 관리 | collab이 양식을 업데이트해도 hr의 수동 업로드본은 그대로 → 데이터 드리프트 |

---

## 3. 신규 아키텍처 — 역할 분담

### 원칙

- **양식(HTML, formCode, folderId 등) 관리 책임은 전부 collaboration-service로 일원화**
- hr-service는 "결의서 데이터(지급 내역)"만 만들고, 양식은 collab에서 읽기
- MinIO 직접 접근 금지 (collab의 REST API 경유)

### 흐름 (신규)

```
[사용자: 결의서 상신 버튼 클릭]
   │
   ▼
[hr-service: PayrollApprovalDraftService.draft()]
   │
   ├─ ① formId 조회 (ApprovalFormIdCache — companyId 기준 메모리 캐시)
   │    캐시 miss 시 → CollaborationClient.getFormIdByCode(companyId, "PAYROLL_RESOLUTION")
   │
   ├─ ② formHtml 조회
   │    CollaborationClient.getFormHtmlByCode(companyId, "PAYROLL_RESOLUTION")
   │    → collab의 DB `approval_form.form_html` 컬럼에서 읽음 (MinIO 직접 접근 X)
   │
   ├─ ③ dataMap 빌드 (기존 로직 유지) — 기본급, 식대, 합계 등 채움
   │
   └─ ApprovalDraftResDto { htmlTemplate, dataMap, ledgerId, type } 반환
        │
        └─ 프론트가 htmlTemplate의 {{key}} 플레이스홀더를 dataMap으로 치환해 미리보기
```

### 상신 시점

프론트에서 결재선 설정 완료 후 상신하면 hr-service → collab으로 Kafka 이벤트. 이때 `formCode` 값이 **collab의 계약 값과 일치**해야 collab 쪽에서 정상 매핑됨.

---

## 4. collaboration-service 쪽 선결 과제 (팀원 협업)

### 🔴 버그: `FIXED_FORM_CODES` 키 불일치

현재 collab의 `ApprovalFormService.java`:

```java
FIXED_FORM_CODES = Map.of(
    ...
    "보고-시행문/퇴직금지급결의서", "SEVERANCE_RESOLUTION",   // ← 키
);
```

하지만 실제 파일:

```
default-forms/보고-시행문/퇴직급여지급결의서.html  ← 파일명
```

매칭 키 조합은 `{folderName}/{formName}` = `"보고-시행문/퇴직급여지급결의서"`.
→ **매칭 실패** → formCode는 자동 생성 규칙(`"퇴직급여지급결의서_001"`) 으로 들어감.

### 해결 (둘 중 하나)

**① (권장) 파일명 통일 — `FIXED_FORM_CODES` 키에 맞춰 파일명을 `퇴직금지급결의서.html`로**

```bash
git mv "collaboration-service/src/main/resources/default-forms/보고-시행문/퇴직급여지급결의서.html" \
       "collaboration-service/src/main/resources/default-forms/보고-시행문/퇴직금지급결의서.html"
```

**② 맵 키를 파일명에 맞춰 변경**

```java
FIXED_FORM_CODES = Map.of(
    ...
    "보고-시행문/퇴직급여지급결의서", "SEVERANCE_RESOLUTION",  // ← 키 수정
);
```

팀 회의에서 정식 명칭을 "퇴직금" vs "퇴직급여" 중 어느 쪽으로 할지 합의 후 선택.

### 🟡 추가 필요: form HTML 조회 REST 엔드포인트

현재 collab에는 `GET /approval/forms/by-code → formId` 만 있고, HTML을 formCode로 바로 가져오는 엔드포인트가 없음. 아래 중 하나 선택:

**옵션 A (권장): 신규 엔드포인트 추가**

collaboration-service `ApprovalFormController.java`:

```java
/** formCode 로 양식 HTML 조회 — hr-service 결의서 미리보기용 */
@GetMapping("/forms/html-by-code")
public ResponseEntity<String> getFormHtmlByCode(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestParam String formCode) {
    String html = approvalFormService.getFormHtmlByCode(companyId, formCode);
    if (html == null) return ResponseEntity.notFound().build();
    return ResponseEntity.ok(html);
}
```

`ApprovalFormService`:

```java
public String getFormHtmlByCode(UUID companyId, String formCode) {
    return approvalFormRepository
            .findByCompanyIdAndFormCodeAndIsActiveTrueAndIsCurrentTrue(companyId, formCode)
            .map(ApprovalForm::getFormHtml)
            .orElse(null);
}
```

> DB 컬럼(`approval_form.form_html`)에 이미 HTML 원문이 저장되어 있으므로 MinIO 접근 없이 바로 반환 가능.

**옵션 B: 기존 `getFormDetail(formId)` 재사용**

hr-service가 먼저 `getFormIdByCode`로 formId를 얻고, 그 뒤 `getFormDetail` 호출해서 `FormDetailResponse.formHtml`을 사용. REST 호출 2번이라 성능상 불리하지만, collab 쪽 수정이 없음.

팀 결정.

---

## 5. hr-service 쪽 작업 — 제거 리스트

### 🗑️ 완전 삭제할 파일

| 파일 | 사유 |
|------|------|
| `hr-service/src/main/java/com/peoplecore/pay/approval/ApprovalHtmlTemplateLoader.java` | 역할 자체가 collab으로 이전됨 |
| `hr-service/src/main/resources/approval-templates/급여지급결의서.html` (있다면) | collab의 `default-forms/` 로 일원화 |
| `hr-service/src/main/resources/approval-templates/퇴직급여지급결의서.html` (있다면) | 동일 |

### 🗑️ 삭제할 설정/의존성

- `application.yml` 의 `minio.bucket.approval-form` 같은 hr-service 전용 버킷 설정 (사용처 없어지므로)
- `ApprovalHtmlTemplateLoader` 에 주입되던 `MinioClient` 가 pay/approval 패키지에서 더는 필요 없음
- 관련 ErrorCode: `APPROVAL_TEMPLATE_NOT_FOUND` 는 hr-service에서 더 이상 발생하지 않으므로 제거 가능 (또는 의미를 "collab 호출 실패"로 재정의)

### 🗑️ 삭제/수정할 데이터

- 기존에 hr-service가 수동 업로드한 MinIO 객체들 (`approval-form/급여지급결의서.html` 등) — 불필요해지므로 **개발/운영 MinIO에서 수동 삭제**
- `approval-form` 버킷 자체를 만들어뒀다면 삭제 (collab가 쓰는 버킷만 남김)

---

## 6. hr-service 쪽 작업 — 신규/수정 리스트

### ✏️ 수정

| 파일 | 변경 내용 |
|------|----------|
| `ApprovalFormType.java` | formCode 값을 collab 계약값으로 교체 + templateFileName 필드 제거 |
| `PayrollApprovalDraftService.java` | `ApprovalHtmlTemplateLoader` 대신 collab REST 호출 |
| `SeveranceApprovalDraftService.java` | 위와 동일 |
| `CollaborationClient.java` | `getFormHtmlByCode()` 메서드 추가 |

### ➕ 신규 추가 (권장)

| 파일 | 역할 |
|------|------|
| `ApprovalFormCache.java` (또는 기존 `ApprovalFormIdCache` 확장) | `PAYROLL_RESOLUTION`, `SEVERANCE_RESOLUTION` 의 formId + formHtml 메모리 캐시 |

---

## 7. 파일별 상세 변경 사항

### 7-1. `ApprovalFormType.java` — 수정

**Before:**
```java
public enum ApprovalFormType {
    SALARY("급여지급결의서.html", "PAYROLL_PAYMENT"),
    RETIREMENT("퇴직급여지급결의서.html", "RETIREMENT_SEVERANCE");

    private final String templateFileName;
    private final String formCode;
    // ...
    public String getTemplateFileName() { return templateFileName; }
    public String getFormCode() { return formCode; }
}
```

**After:**
```java
public enum ApprovalFormType {
    // collaboration-service FIXED_FORM_CODES 와 일치해야 함
    SALARY("PAYROLL_RESOLUTION"),
    RETIREMENT("SEVERANCE_RESOLUTION");

    private final String formCode;

    ApprovalFormType(String formCode) { this.formCode = formCode; }
    public String getFormCode() { return formCode; }
}
```

**변경 포인트:**
- `templateFileName` 필드 제거 (MinIO 직접 접근 안 하니까)
- formCode 값을 collab 계약값으로 교체

### 7-2. `ApprovalHtmlTemplateLoader.java` — 삭제

파일 자체를 제거.

### 7-3. `CollaborationClient.java` — 메서드 추가

`getFormIdByCode` 아래에 추가:

```java
/** formCode 로 양식 HTML 조회 — 결의서 미리보기용 */
public String getFormHtmlByCode(UUID companyId, String formCode) {
    return restClient.get()
            .uri(uri -> uri.path("/approval/forms/html-by-code")
                    .queryParam("formCode", formCode)
                    .build())
            .header("X-User-Company", companyId.toString())
            .retrieve()
            .body(String.class);
}
```

> 옵션 B를 택한 경우는 이 메서드 대신 `getFormDetail(companyId, formId)` 를 쓰도록 호출부 조정.

### 7-4. `ApprovalFormCache.java` — 신규 (권장)

`ApprovalFormIdCache` 와 유사한 패턴으로 formHtml + formId를 함께 캐싱:

```java
package com.peoplecore.pay.approval;

import com.peoplecore.company.service.CollaborationClient;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 회사별 결의서 양식(PAYROLL_RESOLUTION / SEVERANCE_RESOLUTION)의
 * formId + formHtml 메모리 캐시.
 *
 * - 캐시 키: (companyId, ApprovalFormType)
 * - miss 시 collaboration-service REST 호출
 * - 양식 개정 시 invalidate(companyId) 수동 호출로 무효화
 */
@Slf4j
@Component
public class ApprovalFormCache {

    private final CollaborationClient collaborationClient;

    private final ConcurrentHashMap<UUID, Map<ApprovalFormType, CachedForm>> cache
            = new ConcurrentHashMap<>();

    @Autowired
    public ApprovalFormCache(CollaborationClient collaborationClient) {
        this.collaborationClient = collaborationClient;
    }

    public CachedForm get(UUID companyId, ApprovalFormType type) {
        return cache
                .computeIfAbsent(companyId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(type, t -> fetch(companyId, t));
    }

    private CachedForm fetch(UUID companyId, ApprovalFormType type) {
        try {
            Long formId = collaborationClient.getFormIdByCode(companyId, type.getFormCode());
            String html = collaborationClient.getFormHtmlByCode(companyId, type.getFormCode());
            if (formId == null || html == null) {
                throw new CustomException(ErrorCode.APPROVAL_FORM_NOT_FOUND);
            }
            log.info("[ApprovalFormCache] miss → fetch - companyId={}, type={}, formId={}",
                    companyId, type, formId);
            return new CachedForm(formId, html);
        } catch (CustomException ce) { throw ce; }
        catch (Exception e) {
            log.error("[ApprovalFormCache] fetch 실패 companyId={}, type={}, err={}",
                    companyId, type, e.getMessage());
            throw new CustomException(ErrorCode.APPROVAL_FORM_NOT_FOUND);
        }
    }

    public void invalidate(UUID companyId) {
        cache.remove(companyId);
    }

    public record CachedForm(Long formId, String formHtml) {}
}
```

> `ErrorCode.APPROVAL_FORM_NOT_FOUND` 가 없으면 기존 `APPROVAL_TEMPLATE_NOT_FOUND` 를 재활용하거나 이름 변경.

### 7-5. `PayrollApprovalDraftService.java` — 수정

**Before (기존 L34, L61):**
```java
private final ApprovalHtmlTemplateLoader templateLoader;
// ...
String htmlTemplate = templateLoader.load(ApprovalFormType.SALARY);
```

**After:**
```java
private final ApprovalFormCache approvalFormCache;
// ...
ApprovalFormCache.CachedForm form = approvalFormCache.get(companyId, ApprovalFormType.SALARY);
String htmlTemplate = form.formHtml();

return ApprovalDraftResDto.builder()
        .type(ApprovalFormType.SALARY)
        .ledgerId(payrollRunId)
        .htmlTemplate(htmlTemplate)
        .dataMap(dataMap)
        .build();
```

생성자 주입 부분도 `ApprovalHtmlTemplateLoader` → `ApprovalFormCache` 로 교체.

### 7-6. `SeveranceApprovalDraftService.java` — 수정

**Before (L31, L60, L152):**
```java
private final ApprovalHtmlTemplateLoader templateLoader;
// ...
String htmlTemplate = templateLoader.load(ApprovalFormType.RETIREMENT);
// ...
.formCode(ApprovalFormType.RETIREMENT.getFormCode())  // "RETIREMENT_SEVERANCE" ← 잘못된 값
```

**After:**
```java
private final ApprovalFormCache approvalFormCache;
// ...
ApprovalFormCache.CachedForm form = approvalFormCache.get(companyId, ApprovalFormType.RETIREMENT);
String htmlTemplate = form.formHtml();
// ...
.formCode(ApprovalFormType.RETIREMENT.getFormCode())  // ← 이제 "SEVERANCE_RESOLUTION" 자동
```

`formCode` 호출부는 코드 변경 없이도 enum 값 교체만으로 올바른 계약값이 나감.

### 7-7. 상신 이벤트에도 formId 포함 권장

`PayrollApprovalDocCreatedPublisher`, `SeveranceApprovalDocCreatedPublisher` 가 발행하는 이벤트 페이로드에 `formId` 필드를 추가하고, collab의 consumer가 formCode 재조회 없이 formId로 바로 `ApprovalDocument.formId` 를 FK 연결하도록 정리. (Kafka 계약 변경이므로 collab 팀과 동시 배포 필요.)

---

## 8. 전환 순서 체크리스트

### Phase 1: collaboration-service 선결 (팀원 담당)

```
[ ] FIXED_FORM_CODES 키 불일치 해소 (파일명 변경 OR 맵 키 수정)
[ ] GET /approval/forms/html-by-code 엔드포인트 추가 (옵션 A 선택 시)
[ ] 위 변경 반영된 collab 기동 → 기존 회사 DB에 PAYROLL_RESOLUTION / SEVERANCE_RESOLUTION formCode 가 등록되었는지 확인
    SELECT form_code, form_name, is_current FROM approval_form
     WHERE company_id = <테스트회사> AND form_code IN ('PAYROLL_RESOLUTION','SEVERANCE_RESOLUTION');
[ ] 기존 회사는 formCode가 자동생성된 이름("퇴직급여지급결의서_001")으로 남아있을 수 있음 —
    필요 시 UPDATE 로 보정 or 회사 재생성
```

### Phase 2: hr-service 리팩터링

```
[ ] CollaborationClient#getFormHtmlByCode 추가
[ ] ApprovalFormCache 신규 작성
[ ] ApprovalFormType.formCode 값 교체 + templateFileName 필드 제거
[ ] PayrollApprovalDraftService — templateLoader → approvalFormCache
[ ] SeveranceApprovalDraftService — 위와 동일
[ ] ApprovalHtmlTemplateLoader.java 파일 삭제
[ ] (있다면) resources/approval-templates/*.html 삭제
[ ] 컴파일 성공 확인
[ ] 단위테스트 수정 (ApprovalHtmlTemplateLoader mock 쓰던 곳 제거)
```

### Phase 3: 정리

```
[ ] 운영 MinIO 에서 hr-service 가 올렸던 approval-form/* 객체 삭제
[ ] application.yml 의 관련 설정 제거
[ ] ErrorCode.APPROVAL_TEMPLATE_NOT_FOUND 재명명 또는 삭제
```

---

## 9. 검증 시나리오

| # | 테스트 | 기대 결과 |
|---|--------|----------|
| 1 | 신규 회사 생성 → collab 기동 로그 확인 | `ApprovalForm` 테이블에 `formCode=PAYROLL_RESOLUTION`, `SEVERANCE_RESOLUTION` 레코드 생성, MinIO `forms/{companyId}/PAYROLL_RESOLUTION_v1.html` 존재 |
| 2 | hr-service 기동 | `ApprovalHtmlTemplateLoader` 더 이상 없으므로 MinIO 초기화 경로가 사라져 기동 빠름 + 실패 없음 |
| 3 | 급여 결의서 미리보기 API 호출 (`/pay/admin/approval/salary/draft?payrollRunId=X`) | 200 OK, `htmlTemplate` 에 collab DB의 HTML 원문이 내려옴, `dataMap` 정상 채워짐 |
| 4 | 퇴직 결의서 미리보기 동일 | 위와 동일 |
| 5 | 캐시 확인 | 같은 회사에서 재호출 시 로그에 `[ApprovalFormCache] miss → fetch` 가 안 찍힘 (첫 호출만 찍힘) |
| 6 | 결재 상신 후 collab 측 `approval_document.form_id` 레코드 확인 | 정상적으로 PAYROLL_RESOLUTION/SEVERANCE_RESOLUTION formId 로 FK 연결 |

---

## 부록 A — 관련 엔티티 관계도

```
ApprovalFormFolder (collab)
 └─ ApprovalForm (collab, formCode + formHtml)
      ↑
      │ formId (FK)
      │
    ApprovalDocument (collab, 상신된 문서)
      ↑
      │ Kafka event
      │
    PayrollRuns / SeverancePays (hr-service, 결의서 원천 데이터)
```

## 부록 B — collab REST API 요약

| 메서드 | 경로 | 헤더 | 쿼리/바디 | 응답 |
|--------|------|------|-----------|------|
| POST | `/approval/init/formfolder` | `X-User-Company` | - | 201 |
| GET  | `/approval/forms/by-code` | `X-User-Company` | `formCode` | `Long formId` |
| GET  | `/approval/forms/html-by-code` (**신규**) | `X-User-Company` | `formCode` | `String formHtml` |

---

## 부록 C — 이전 코드 대비 메모리/네트워크 변화

| 항목 | 기존 | 신규 | 코멘트 |
|------|------|------|--------|
| 기동 시 I/O | `@PostConstruct`에서 MinIO 2회 호출 | 없음 | 기동 빨라짐, 실패 지점 제거 |
| 양식 요청 시 | 메모리 캐시 hit | 첫 회 collab REST 2회 (formId + html), 이후 캐시 hit | 초회 지연 있지만 동일 회사 반복 호출엔 체감 없음 |
| 데이터 일관성 | hr 수동 업로드본 + collab 자동등록본 **이중화** | collab 단일 소스 | 양식 개정 시 invalidate 한 번이면 끝 |
