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
   ├─ ApprovalFormCache.get(companyId, SALARY)   ← 메모리 캐시 조회
   │   │ 캐시 miss 시:
   │   ├─ ① CollaborationClient.getFormIdByCode("PAYROLL_RESOLUTION")
   │   │        → GET /approval/forms/by-code?formCode=PAYROLL_RESOLUTION
   │   │        → Long formId
   │   │
   │   └─ ② CollaborationClient.getFormDetailEditing(formId)
   │            → GET /approval/forms/{formId}/edit   (전자결재 "새 문서 작성" 과 동일 경로)
   │            → FormDetailResponse { formHtml, formVersion, formRetentionYear, isProtected, ... }
   │            → formHtml 은 collab 측에서 MinIO 최신본으로 채움
   │
   ├─ dataMap 빌드 (기존 로직 유지) — 기본급, 식대, 합계 등 채움
   │
   └─ ApprovalDraftResDto { htmlTemplate, dataMap, ledgerId, type } 반환
        │
        └─ 프론트가 htmlTemplate의 {{key}} 플레이스홀더를 dataMap으로 치환해 미리보기
```

### 왜 `getFormDetailEditing` 을 재사용하는가

| 이유 | 상세 |
|------|------|
| **일관성** | 전자결재 UI 의 "새 문서 작성" 경로가 이미 이 메서드를 사용. 결의서 미리보기도 본질은 "양식 기반 새 문서 작성"이므로 같은 경로를 탐 |
| **편집 중 정합성** | 관리자가 양식 HTML 을 수정하면 MinIO 가 먼저 갱신되는 설계. `getFormDetailEditing` 은 MinIO 최신본을 반환하므로 두 화면이 **항상 같은 HTML** 을 보여줌 |
| **확장성** | `FormDetailResponse` 에 formVersion / retentionYear / isProtected 등 포함. 향후 결의서가 "상신 당시 양식 버전 기록", "보존연한 FK 저장" 등 기능 추가 시 REST 재호출 없이 대응 |
| **협업 비용** | collab 쪽 신규 엔드포인트 작업이 없음. 본 가이드 4장의 선결 과제가 `FIXED_FORM_CODES` 키 불일치 하나로 축소 |
| **패턴 재활용** | `ApprovalFormIdCache`(ATTENDANCE_MODIFY 용) 가 이미 `getFormIdByCode` 패턴을 정착시켜놓음. 결의서도 같은 패턴 위에 HTML 한 층만 얹음 |

성능 면에서 초회 미스 시 REST 2회 + MinIO 1회 네트워크 홉이 필요하지만, 캐시 적용 시 JVM 수명 동안 회사당 2회(SALARY, RETIREMENT) 미스가 전부라 실효 영향 없음.

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

### 🟢 form HTML 조회 — 기존 엔드포인트 재사용

collab에 이미 존재하는 엔드포인트 2개를 조합해 처리하므로 **신규 엔드포인트 추가 불필요**:

| 메서드 | 경로 | 용도 |
|--------|------|------|
| `GET` | `/approval/forms/by-code?formCode=X` | formCode → formId 반환 (이미 사용 중) |
| `GET` | `/approval/forms/{formId}/edit` | formId → `FormDetailResponse`(formHtml, formVersion 등) 반환. 내부적으로 `getFormDetailEditing` 호출 → **MinIO 최신본** HTML |

`getFormDetailEditing` 의 동작([ApprovalFormService.java:241](../collaboration-service/src/main/java/com/peoplecore/approval/service/ApprovalFormService.java#L241)):

```java
public FormDetailResponse getFormDetailEditing(UUID companyId, Long formId) {
    ApprovalForm approvalForm = approvalFormRepository.findDetailById(formId, companyId)...
    String objectName = String.format("forms/%s/%s_v%d.html",
            companyId, approvalForm.getFormCode(), approvalForm.getFormVersion());
    String formHtml = minioService.getFormHtml(objectName);   // MinIO 최신본
    FormDetailResponse response = FormDetailResponse.from(approvalForm);
    response.setFormHtml(formHtml);
    return response;
}
```

hr-service 는 이 경로를 그대로 타서, 관리자가 양식 편집 중인 순간에도 **전자결재 UI의 새 문서 작성 경로와 동일한 HTML** 을 받음.

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

`getFormIdByCode` 아래에 `getFormDetailEditing` 추가 (formId 기반, FormDetailResponse 반환):

```java
import com.peoplecore.pay.approval.dto.FormDetailResponse;

/**
 * formId 로 양식 상세 조회 (편집용).
 * collab 내부에서 MinIO 최신본 HTML 을 채워 반환.
 * 전자결재 UI 의 "새 문서 작성" 과 동일 경로.
 */
public FormDetailResponse getFormDetailEditing(UUID companyId, Long formId) {
    return restClient.get()
            .uri("/approval/forms/{formId}/edit", formId)
            .header("X-User-Company", companyId.toString())
            .retrieve()
            .body(FormDetailResponse.class);
}
```

**`FormDetailResponse` DTO**

collab의 `com.peoplecore.approval.dto.FormDetailResponse` 와 필드 정렬을 맞추기 위해 hr-service 측에도 동일 DTO 를 둬야 함. 최소 필드:

```java
// hr-service/src/main/java/com/peoplecore/pay/approval/dto/FormDetailResponse.java
package com.peoplecore.pay.approval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)   // collab 이 필드 추가해도 깨지지 않게
public class FormDetailResponse {
    private Long formId;
    private String formCode;
    private String formName;
    private String formHtml;
    private Integer formVersion;
    private Integer formRetentionYear;
    private Boolean formPreApprovalYn;
    private Boolean isProtected;
    // hr-service 에서 필요 없는 필드는 모두 생략 가능 (ignoreUnknown)
}
```

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
            // ① formCode → formId
            Long formId = collaborationClient.getFormIdByCode(companyId, type.getFormCode());
            if (formId == null) {
                throw new CustomException(ErrorCode.APPROVAL_FORM_NOT_FOUND);
            }
            // ② formId → FormDetailResponse (MinIO 최신 HTML 포함)
            FormDetailResponse detail = collaborationClient.getFormDetailEditing(companyId, formId);
            if (detail == null || detail.getFormHtml() == null) {
                throw new CustomException(ErrorCode.APPROVAL_FORM_NOT_FOUND);
            }
            log.info("[ApprovalFormCache] miss → fetch companyId={}, type={}, formId={}, version={}",
                    companyId, type, formId, detail.getFormVersion());
            return new CachedForm(formId, detail.getFormHtml(), detail.getFormVersion());
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

    public record CachedForm(Long formId, String formHtml, Integer formVersion) {}
}
```

> `formVersion` 은 상신 이벤트에 포함시켜 collab 측에서 어느 버전 양식으로 상신됐는지 추적 가능하게 하려는 용도 (선택). 현 시점에 불필요하면 제거해도 무방.

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
String htmlTemplate = form.formHtml();   // ← collab → MinIO 최신본

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

### 7-7. 상신 파이프라인 마무리 (필수)

현재 상신 파이프라인은 **publisher 만 만들어져 있고 consumer 와 submit 호출부가 비어있는 미완성 상태**. 결의서 상신 기능을 동작시키려면 아래 5단계가 전부 필요합니다.

#### 7-7-1. 이벤트 DTO에 `formId` 필드 추가 + formCode 주석 수정

`common/src/main/java/com/peoplecore/event/PayrollApprovalDocCreatedEvent.java`:

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PayrollApprovalDocCreatedEvent {
    private UUID companyId;
    private Long payrollRunId;
    private Long drafterId;
    private Long formId;                 // ← 신규 추가
    private String formCode;             // "PAYROLL_RESOLUTION"  ← 주석 교체
    private String htmlContent;
    private List<ApprovalLineDto> approvalLine;
}
```

`SeveranceApprovalDocCreatedEvent.java` 도 동일하게 `formId` 필드 추가 + 주석을 `"SEVERANCE_RESOLUTION"` 으로 교체.

> **왜 formId 를 같이 보내나?** collab 의 consumer 가 `ApprovalDocument` 생성 시 `formId` FK 를 채워야 하는데, 이벤트에 formCode 만 있으면 consumer 에서 매번 `approval_form` 테이블 조회해 formId 변환해야 함. publisher(hr-service) 가 이미 `ApprovalFormCache.get()` 으로 formId 를 알고 있으니 **그대로 실어서 보내는 게 효율적**. consumer 의 DB 조회 1회 절약 + formCode→formId 변환 로직 중복 제거.

#### 7-7-2. `SeveranceApprovalDraftService.submit()` — formId 주입

기존([L138-158](../be23-fin-PeopleCore/hr-service/src/main/java/com/peoplecore/pay/approval/SeveranceApprovalDraftService.java#L138-L158))에 `formId` 한 줄 추가:

```java
@Transactional
public void submit(UUID companyId, Long userId, ApprovalSubmitReqDto reqDto) {
    SeverancePays sev = severancePaysRepository
            .findBySevIdAndCompany_CompanyId(reqDto.getLedgerId(), companyId)
            .orElseThrow(() -> new CustomException(ErrorCode.SEVERANCE_NOT_FOUND));

    if (sev.getSevStatus() != SevStatus.CONFIRMED) {
        throw new CustomException(ErrorCode.SEVERANCE_STATUS_INVALID);
    }

    // 캐시에서 formId 조회 (draft 단계에서 이미 warm-up 됐을 확률 높음)
    ApprovalFormCache.CachedForm form =
            approvalFormCache.get(companyId, ApprovalFormType.RETIREMENT);

    severanceApprovalDocCreatedPublisher.publish(SeveranceApprovalDocCreatedEvent.builder()
            .companyId(companyId)
            .sevId(sev.getSevId())
            .empId(sev.getEmployee().getEmpId())
            .drafterId(userId)
            .formId(form.formId())                       // ← 신규
            .formCode(ApprovalFormType.RETIREMENT.getFormCode())  // "SEVERANCE_RESOLUTION"
            .htmlContent(reqDto.getHtmlContent())
            .approvalLine(reqDto.getApprovalLine())
            .build());

    log.info("[SeveranceApproval] 상신 발행 - sevId={}, formId={}, drafterId={}",
            sev.getSevId(), form.formId(), userId);
}
```

`approvalFormCache` 필드 주입은 7-5 단계에서 이미 해둔 상태.

#### 7-7-3. `PayrollApprovalDraftService.submit()` — 구현 (현재 빈 메서드)

현재([L158-162](../be23-fin-PeopleCore/hr-service/src/main/java/com/peoplecore/pay/approval/PayrollApprovalDraftService.java#L158-L162))는 주석만:

```java
@Transactional
public void submit(UUID companyId, Long userId, ApprovalSubmitReqDto reqDto) {
    // SeveranceApprovalDraftService.submit()와 동일 패턴
    // SalaryApprovalDocCreatedEvent 발행
}
```

Severance 를 참고해 실제 구현:

```java
@Transactional
public void submit(UUID companyId, Long userId, ApprovalSubmitReqDto reqDto) {
    PayrollRuns run = payrollRunsRepository
            .findByPayrollRunIdAndCompany_CompanyId(reqDto.getLedgerId(), companyId)
            .orElseThrow(() -> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));

    if (run.getPayrollStatus() != PayrollStatus.APPROVED) {
        throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
    }

    ApprovalFormCache.CachedForm form =
            approvalFormCache.get(companyId, ApprovalFormType.SALARY);

    docCreatedPublisher.publish(PayrollApprovalDocCreatedEvent.builder()
            .companyId(companyId)
            .payrollRunId(run.getPayrollRunId())
            .drafterId(userId)
            .formId(form.formId())
            .formCode(ApprovalFormType.SALARY.getFormCode())   // "PAYROLL_RESOLUTION"
            .htmlContent(reqDto.getHtmlContent())
            .approvalLine(reqDto.getApprovalLine())
            .build());

    log.info("[PayrollApproval] 상신 발행 - payrollRunId={}, formId={}, drafterId={}",
            run.getPayrollRunId(), form.formId(), userId);

    // 상태 전이: APPROVED → IN_APPROVAL (요구사항에 따라 조정)
    // run.changeStatus(PayrollStatus.IN_APPROVAL);
}
```

#### 7-7-4. collab-service consumer 신규 작성 (현재 없음)

해당 토픽을 소비하는 코드가 collab 쪽에 전혀 없음. 두 개 신규 생성.

**설계 원칙**: 신규 전용 서비스 메서드(`createFromHrEvent`)를 만들지 않고, **기존 `ApprovalDocumentService.createDocument` 를 그대로 재사용**. 프론트 직접 상신 경로와 hr 이벤트 기반 상신 경로가 동일한 로직을 타도록 하여 11 단계 로직 중복을 제거.

##### 사전 작업: 기존 코드에 최소 확장

**(a) `DocumentCreateRequest` 에 hrRef 필드 2개 추가 (nullable)**

```java
// collaboration-service/.../approval/dto/DocumentCreateRequest.java
public class DocumentCreateRequest {
    // ... 기존 필드
    private String hrRefType;   // nullable — 프론트 직접 상신 시 null
    private Long hrRefId;       // nullable
}
```

**(b) `createDocument` 의 엔티티 빌더에 2줄만 추가**

```java
ApprovalDocument document = ApprovalDocument.builder()
        .companyId(companyId)
        .docNum(docNum)
        // ... 기존 필드들
        .hrRefType(request.getHrRefType())   // ← 추가
        .hrRefId(request.getHrRefId())       // ← 추가
        .build();
```

프론트 직접 상신은 둘 다 null → NULL 허용 복합 unique 제약이라 충돌 없음 (MySQL UNIQUE 는 NULL 끼리는 검사 제외).

##### `PayrollApprovalDocCreatedConsumer.java`

```java
package com.peoplecore.approval.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.dto.DocumentCreateRequest;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.repository.ApprovalDocumentRepository;
import com.peoplecore.approval.service.ApprovalDocumentService;
import com.peoplecore.client.dto.EmpDetailResponse;
import com.peoplecore.client.HrServiceClient;
import com.peoplecore.event.PayrollApprovalDocCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollApprovalDocCreatedConsumer {

    private static final String HR_REF_TYPE = "PAYROLL_RUN";

    private final ApprovalDocumentService approvalDocumentService;
    private final ApprovalDocumentRepository approvalDocumentRepository;
    private final HrServiceClient hrServiceClient;
    private final ObjectMapper objectMapper;

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2))
    @KafkaListener(topics = "salary-approval-doc-created", groupId = "collaboration-approval")
    public void consume(String message) {
        PayrollApprovalDocCreatedEvent event;
        try {
            event = objectMapper.readValue(message, PayrollApprovalDocCreatedEvent.class);
        } catch (Exception e) {
            log.error("[Collab] 급여결의서 역직렬화 실패 - 스킵: {}", message, e);
            return;   // 포맷 오류는 재시도 무의미
        }

        try {
            // (1) 멱등 사전 체크
            Optional<ApprovalDocument> existing = approvalDocumentRepository
                    .findByCompanyIdAndHrRefTypeAndHrRefId(
                            event.getCompanyId(), HR_REF_TYPE, event.getPayrollRunId());
            if (existing.isPresent()) {
                log.info("[Collab] 급여결의서 중복 수신 - skip. payrollRunId={}, docId={}",
                        event.getPayrollRunId(), existing.get().getDocId());
                return;
            }

            // (2) 기안자 정보 조회
            EmpDetailResponse drafter = hrServiceClient.getEmployee(
                    event.getCompanyId(), event.getDrafterId());

            // (3) 결재선 변환: ApprovalLineDto → ApprovalLineRequest
            //     이벤트엔 approverId/order/approvalType 만 있고,
            //     collab 엔티티는 이름/부서/직급/직책까지 필요 → 결재자별 hr 조회
            List<DocumentCreateRequest.ApprovalLineRequest> lineRequests =
                    event.getApprovalLine().stream()
                            .map(line -> {
                                EmpDetailResponse approver = hrServiceClient.getEmployee(
                                        event.getCompanyId(), line.getApproverId());
                                return DocumentCreateRequest.ApprovalLineRequest.builder()
                                        .empId(line.getApproverId())
                                        .empName(approver.getEmpName())
                                        .empDeptId(approver.getDeptId())
                                        .empDeptName(approver.getDeptName())
                                        .empGrade(approver.getGradeName())
                                        .empTitle(approver.getTitleName())
                                        .approvalRole(line.getApprovalType())
                                        .lineStep(line.getOrder())
                                        .build();
                            })
                            .toList();

            // (4) DocumentCreateRequest 로 변환
            DocumentCreateRequest request = DocumentCreateRequest.builder()
                    .formId(event.getFormId())
                    .docType("NEW")
                    .docTitle(String.format("급여지급결의서 (#%d)", event.getPayrollRunId()))
                    .docData(objectMapper.writeValueAsString(
                            java.util.Map.of("html", event.getHtmlContent())))   // JSON 컬럼 호환
                    .approvalLines(lineRequests)
                    .hrRefType(HR_REF_TYPE)
                    .hrRefId(event.getPayrollRunId())
                    .build();

            // (5) 기존 createDocument 재사용
            Long docId = approvalDocumentService.createDocument(
                    event.getCompanyId(),
                    event.getDrafterId(),
                    drafter.getEmpName(),
                    drafter.getDeptId(),
                    drafter.getGradeName(),
                    drafter.getTitleName(),
                    request
            );

            log.info("[Collab] 급여결의서 생성 완료 - payrollRunId={}, docId={}, formId={}",
                    event.getPayrollRunId(), docId, event.getFormId());

        } catch (DataIntegrityViolationException dup) {
            log.warn("[Collab] 급여결의서 unique 충돌 - 동시성 중복 skip. payrollRunId={}",
                    event.getPayrollRunId());
        } catch (Exception e) {
            log.error("[Collab] 급여결의서 처리 실패 - retry 대상: payrollRunId={}, err={}",
                    event.getPayrollRunId(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("[Collab] 급여결의서 최종 실패 - DLT 이동: {}", message);
        // TODO: 운영 알림 훅 (Slack/이메일)
    }
}
```

##### `SeveranceApprovalDocCreatedConsumer.java`

동일 패턴. 차이는:
- 토픽: `severance-approval-doc-created`
- `HR_REF_TYPE = "SEVERANCE"`
- 식별자: `event.getSevId()`
- docTitle: `String.format("퇴직급여지급결의서 (#%d)", event.getSevId())`

##### 확인 필요한 주변 메서드

| 호출 | 확인 사항 |
|------|---------|
| `hrServiceClient.getEmployee(companyId, empId)` | 기존에 있는지 확인. 없으면 hr-service 에 `GET /internal/employees/{empId}` 엔드포인트 + 클라이언트 메서드 추가 (기존 `hrCacheService.getCompany/getDept` 와 동일 패턴) |
| `ApprovalDocumentRepository#findByCompanyIdAndHrRefTypeAndHrRefId` | 신규 쿼리 메서드 추가 필요 — 7-7-5 섹션 참조 |
| `createDocument` 내부의 `ATTENDANCE_MODIFY` 검증 로직 | `PAYROLL_RESOLUTION` / `SEVERANCE_RESOLUTION` formCode 에선 안 타므로 hr 경로에도 무해. 그대로 둠 |

#### 7-7-5. 멱등성·실패 처리

Kafka 는 **at-least-once** 가 기본이라 동일 메시지가 2회 이상 수신될 가능성 상존:
- 네트워크 지연으로 commit 이 producer 에 늦게 반영
- consumer 리밸런싱 중 offset commit 실패 → 재할당 후 같은 메시지 재처리
- `@RetryableTopic` 자체가 retry 토픽으로 동일 메시지를 다시 흘려보냄

따라서 같은 hr 대장(예: payrollRunId=42)에 대한 상신 이벤트가 2회 수신되어도 **`ApprovalDocument` 는 1개만 생성**되어야 함. 아래 3중 방어:

##### (A) DB 레벨 중복 방어 — 권장 1순위

`ApprovalDocument` 엔티티에 hr 출처 추적용 컬럼 2개 + unique 제약 추가.

**entity 변경 (`collaboration-service/.../entity/ApprovalDocument.java`):**

```java
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"company_id", "doc_num"}),
    @UniqueConstraint(
        name = "uk_approval_doc_hr_ref",
        columnNames = {"company_id", "hr_ref_type", "hr_ref_id"}
    )
})
public class ApprovalDocument {
    // ...기존 필드

    /** 외부(hr-service) 상신 원천 구분자 — "PAYROLL_RUN" / "SEVERANCE" / null(프론트 직접 상신) */
    @Column(length = 20)
    private String hrRefType;

    /** 외부 원천 식별자 — PAYROLL_RUN → payrollRunId, SEVERANCE → sevId */
    private Long hrRefId;
}
```

**DB 마이그레이션:**

```sql
ALTER TABLE approval_document
    ADD COLUMN hr_ref_type VARCHAR(20) NULL,
    ADD COLUMN hr_ref_id   BIGINT      NULL;

-- NULL 허용 복합 unique (MySQL 은 NULL 끼리는 unique 검사에서 제외됨 → 프론트 직접 상신에 영향 없음)
ALTER TABLE approval_document
    ADD CONSTRAINT uk_approval_doc_hr_ref
    UNIQUE (company_id, hr_ref_type, hr_ref_id);
```

이제 중복 INSERT 시 `DataIntegrityViolationException` 이 터지고, 아래 (B) 에서 이를 잡아 swallow.

##### (B) Consumer 애플리케이션 레벨 방어 — DB 방어와 병행

**`ApprovalDocumentRepository` 에 조회 메서드 추가:**

```java
Optional<ApprovalDocument> findByCompanyIdAndHrRefTypeAndHrRefId(
        UUID companyId, String hrRefType, Long hrRefId);
```

**Consumer 내부 — "사전 체크 + DB unique 예외 중복 방어":**

```java
@KafkaListener(topics = "salary-approval-doc-created", groupId = "collaboration-approval")
public void consume(String message) {
    PayrollApprovalDocCreatedEvent event;
    try {
        event = objectMapper.readValue(message, PayrollApprovalDocCreatedEvent.class);
    } catch (Exception e) {
        log.error("[Collab] 급여결의서 역직렬화 실패 - 메시지 스킵: {}", message, e);
        return;  // 포맷 오류는 재시도해도 해결 불가 → DLT 넘기지 말고 skip
    }

    try {
        // (B-1) 사전 중복 체크 — 대부분의 재수신은 여기서 걸러짐
        Optional<ApprovalDocument> existing = approvalDocumentRepository
                .findByCompanyIdAndHrRefTypeAndHrRefId(
                        event.getCompanyId(), HR_REF_TYPE, event.getPayrollRunId());
        if (existing.isPresent()) {
            log.info("[Collab] 급여결의서 중복 수신 - skip. payrollRunId={}, docId={}",
                    event.getPayrollRunId(), existing.get().getDocId());
            return;
        }

        // (B-2) 기존 createDocument 재사용해 실제 생성
        //       → 7-7-4 참조. DocumentCreateRequest 에 hrRefType/Id 담아서 호출
        EmpDetailResponse drafter = hrServiceClient.getEmployee(
                event.getCompanyId(), event.getDrafterId());

        // 결재선 변환 (결재자별 hr 조회) — 7-7-4 참조
        List<DocumentCreateRequest.ApprovalLineRequest> lineRequests =
                event.getApprovalLine().stream()
                        .map(line -> {
                            EmpDetailResponse approver = hrServiceClient.getEmployee(
                                    event.getCompanyId(), line.getApproverId());
                            return DocumentCreateRequest.ApprovalLineRequest.builder()
                                    .empId(line.getApproverId())
                                    .empName(approver.getEmpName())
                                    .empDeptId(approver.getDeptId())
                                    .empDeptName(approver.getDeptName())
                                    .empGrade(approver.getGradeName())
                                    .empTitle(approver.getTitleName())
                                    .approvalRole(line.getApprovalType())
                                    .lineStep(line.getOrder())
                                    .build();
                        })
                        .toList();

        DocumentCreateRequest request = DocumentCreateRequest.builder()
                .formId(event.getFormId())
                .docType("NEW")
                .docTitle(String.format("급여지급결의서 (#%d)", event.getPayrollRunId()))
                .docData(objectMapper.writeValueAsString(
                        java.util.Map.of("html", event.getHtmlContent())))
                .approvalLines(lineRequests)
                .hrRefType(HR_REF_TYPE)
                .hrRefId(event.getPayrollRunId())
                .build();

        Long docId = approvalDocumentService.createDocument(
                event.getCompanyId(),
                event.getDrafterId(),
                drafter.getEmpName(),
                drafter.getDeptId(),
                drafter.getGradeName(),
                drafter.getTitleName(),
                request
        );

        // (B-2-완료) 성공 로그
        log.info("[Collab] 급여결의서 생성 완료 - payrollRunId={}, docId={}, formId={}",
                event.getPayrollRunId(), docId, event.getFormId());

    } catch (DataIntegrityViolationException dup) {
        // (B-3) 사전 체크 와 생성 사이에 동시 실행된 다른 consumer instance가 먼저 만든 경우 —
        // unique 제약이 터진 것이므로 중복으로 간주하고 swallow
        log.warn("[Collab] 급여결의서 unique 충돌 - 동시성 중복으로 간주 skip. payrollRunId={}",
                event.getPayrollRunId());
    } catch (Exception e) {
        log.error("[Collab] 급여결의서 처리 실패 - retry 대상: payrollRunId={}, err={}",
                event.getPayrollRunId(), e.getMessage(), e);
        throw new RuntimeException(e);  // @RetryableTopic 이 재시도 처리
    }
}
```

> **설계 메모**:
> - 별도 `createFromHrEvent` 서비스 메서드를 만들지 **않음**. 기존 `createDocument` 를 재사용하여 프론트 직접 상신과 hr 이벤트 상신이 같은 로직을 탐 → 11단계 로직 중복 제거.
> - `hrRefType/Id` 는 consumer 가 알고 있는 고정값(`HR_REF_TYPE` 상수)과 이벤트의 도메인 ID(`payrollRunId` / `sevId`) 를 조합해 `DocumentCreateRequest` 에 실어 전달.
> - `Severance Consumer` 는 동일 패턴에 상수만 `"SEVERANCE"` 로 교체, 도메인 ID 를 `event.getSevId()` 로.

##### 로그 레벨 가이드

| 상황 | 레벨 | 이유 |
|------|------|------|
| 정상 생성 | `INFO` | 정상 운영 흐름 — 감사/추적에 필요 |
| 중복 수신 skip | `INFO` | 비정상은 아님 (Kafka 특성상 예상 가능) |
| unique 충돌 | `WARN` | 동시성 상황 — 자주 보이면 consumer 동시성 재검토 필요 |
| 재시도 대상 예외 | `ERROR` | 일시 장애 — 반복되면 DLT 로 격리됨 |
| 역직렬화 실패 | `ERROR` (`return` 으로 skip) | 포맷 오류는 재시도 무의미 |

##### (C) 재시도 + DLT — 일시 장애 복구

`@RetryableTopic` 을 붙이면 예외 시 retry 토픽으로 3회 재시도, 그래도 실패하면 DLT(`.DLT` 접미사 토픽) 로 격리:

```java
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2),     // 1s → 2s → 4s
    autoCreateTopics = "true"
)
@KafkaListener(topics = "salary-approval-doc-created", groupId = "collaboration-approval")
public void consume(String message) { /* 위 본문 */ }

@DltHandler
public void handleDlt(String message) {
    log.error("[Collab] 급여결의서 최종 실패 - DLT 로 이동: {}", message);
    // TODO: 운영 알림(Slack, 이메일) 훅
    //       또는 dead_letter 테이블에 저장해 관리자 수동 개입 대기
}
```

> `@RetryableTopic` 과 `@KafkaListener` 의 `groupId` 조합 방식은 기존 [`CompanyFolderInitConsumer.java:41-43`](../be23-fin-PeopleCore/collaboration-service/src/main/java/com/peoplecore/approval/consumer/CompanyFolderInitConsumer.java#L41-L43) 패턴 그대로. 동일 모듈에서 이미 검증된 설정.

##### 실패 유형별 의사결정 표

| 예외 유형 | 결과 | 이유 |
|----------|------|------|
| `JsonProcessingException` (역직렬화 실패) | skip (retry 안 함) | 메시지 포맷 오류는 재시도해도 영원히 실패. 로그만 남기고 넘어감 |
| `DataIntegrityViolationException` (unique 충돌) | skip | 중복 수신 확정. swallow |
| `BusinessException` (예: 유효하지 않은 formId) | retry 후 DLT | 데이터 정합성 문제 — 재시도로 해결 안 되면 관리자 개입 필요 |
| 일시적 DB 장애 / 네트워크 타임아웃 | retry (Backoff) | 자가 회복 가능 |
| 예상치 못한 `RuntimeException` | retry 후 DLT | 보수적으로 재시도 |

##### 체크리스트

```
[ ] ApprovalDocument 엔티티 — hrRefType, hrRefId 필드 + @UniqueConstraint 추가
[ ] DB 마이그레이션 — ADD COLUMN + ADD CONSTRAINT 적용
    (ddl-auto=create 이면 엔티티 반영만으로 재기동 시 자동)
[ ] ApprovalDocumentRepository — findByCompanyIdAndHrRefTypeAndHrRefId 쿼리 메서드 추가
[ ] DocumentCreateRequest — hrRefType, hrRefId 필드 2개 추가 (nullable)
[ ] ApprovalDocumentService.createDocument — 엔티티 빌더에 .hrRefType/.hrRefId 2줄 추가
[ ] Consumer 2개 — 사전 중복 체크 + createDocument 호출 + DataIntegrityViolationException 핸들링
[ ] Consumer 2개 — @RetryableTopic + @DltHandler
[ ] hrServiceClient#getEmployee(companyId, empId) 존재 확인 / 없으면 추가
[ ] 운영 — DLT 모니터링 알림 설정 (Slack/이메일)
```

##### 멱등성 테스트 시나리오

| # | 시나리오 | 기대 |
|---|---------|------|
| 1 | 같은 메시지를 consumer 에 **2회 consume** 호출 | 첫 번째만 ApprovalDocument 생성, 두 번째는 "중복 수신 skip" 로그 |
| 2 | DB 에 ApprovalDocument 삭제 후 동일 메시지 재전달 | 새 레코드 생성 (정상. 실제 운영에선 발생 안 해야 함) |
| 3 | DB 저장 도중 의도적 RuntimeException | retry 토픽으로 재시도, 3회 실패 후 `.DLT` 에 메시지 도착 |
| 4 | 두 consumer instance 가 동시에 같은 메시지 처리 | 한 쪽만 성공, 다른 쪽은 unique 충돌 → skip |

#### 7-7-6. 양식 수정 시 hr-service 캐시 무효화

##### 왜 필요한가

`ApprovalFormCache` 는 첫 조회 시 collab 에서 받아온 `formHtml` 을 메모리에 보관해 이후 호출의 네트워크 비용을 절감. 하지만 **관리자가 collab 에서 양식 HTML 을 수정하면 캐시에 남은 구버전이 계속 반환** 되는 드리프트 문제가 생긴다.

```
[T0] 관리자가 "급여지급결의서" HTML 수정 → collab DB + MinIO 갱신
[T1] hr-admin 이 결의서 상신 버튼 클릭
     → hr 는 캐시에 남은 T0 이전 HTML 반환
     → 미리보기는 구버전 양식
     → 상신 결과도 구버전 기준
[T2] JVM 재기동 또는 몇 시간 뒤
     → 드디어 캐시 리프레시 → 새 양식 반영
```

B 옵션을 고른 근거 중 하나가 "관리자 편집 중 정합성" 이었으므로, 이 드리프트를 해결하지 않으면 선택 이유가 퇴색됨. 아래 A(권장) + C(안전망) 조합으로 해결.

##### (A) 이벤트 기반 실시간 invalidate — 권장

**흐름 요약**

```
[collab: ApprovalFormService.updateForm()]
    │ 양식 수정 완료
    ▼
[ApprovalFormUpdatedEvent 발행]
    │ Kafka "approval-form-updated"
    ▼
[hr: ApprovalFormUpdatedConsumer]
    │ approvalFormCache.invalidate(companyId) 호출
    ▼
[다음 get() 호출 시 캐시 miss → collab 에서 최신 HTML 재조회]
```

##### A-1. 이벤트 DTO — `common/src/main/java/com/peoplecore/event/ApprovalFormUpdatedEvent.java` (신규)

```java
package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ApprovalFormUpdatedEvent {
    private UUID companyId;
    private Long formId;
    private String formCode;       // 특정 양식만 무효화하고 싶을 때 사용
    private Integer formVersion;   // 참고용 (로깅·디버깅)
}
```

##### A-2. collab 쪽 publisher — `collaboration-service/.../approval/publisher/ApprovalFormUpdatedPublisher.java` (신규)

```java
package com.peoplecore.approval.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.ApprovalFormUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalFormUpdatedPublisher {

    private static final String TOPIC = "approval-form-updated";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(ApprovalFormUpdatedEvent event) {
        try {
            kafkaTemplate.send(TOPIC, objectMapper.writeValueAsString(event));
            log.info("[Kafka] {} 발행 - companyId={}, formCode={}",
                    TOPIC, event.getCompanyId(), event.getFormCode());
        } catch (Exception e) {
            // 캐시 무효화 실패는 비즈니스 트랜잭션에 영향 주지 않음 — 로그만
            log.error("[Kafka] {} 발행 실패 - err={}", TOPIC, e.getMessage());
        }
    }
}
```

##### A-3. collab 쪽 호출부 — `ApprovalFormService.updateForm()` 끝에 한 줄 추가

기존 [ApprovalFormService.java:377 부근](../be23-fin-PeopleCore/collaboration-service/src/main/java/com/peoplecore/approval/service/ApprovalFormService.java#L377):

```java
public FormDetailResponse updateForm(UUID companyId, Long formId, ApprovalFormUpdateRequest request) {
    // ... 기존: DB 업데이트, MinIO 업로드

    // ← 추가
    approvalFormUpdatedPublisher.publish(ApprovalFormUpdatedEvent.builder()
            .companyId(companyId)
            .formId(form.getFormId())
            .formCode(form.getFormCode())
            .formVersion(form.getFormVersion())
            .build());

    return FormDetailResponse.from(form);
}
```

##### A-4. hr 쪽 consumer — `hr-service/.../pay/approval/consumer/ApprovalFormUpdatedConsumer.java` (신규)

```java
package com.peoplecore.pay.approval.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.ApprovalFormUpdatedEvent;
import com.peoplecore.pay.approval.ApprovalFormCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalFormUpdatedConsumer {

    private final ApprovalFormCache approvalFormCache;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "approval-form-updated", groupId = "hr-form-cache-invalidate")
    public void consume(String message) {
        try {
            ApprovalFormUpdatedEvent event =
                    objectMapper.readValue(message, ApprovalFormUpdatedEvent.class);
            approvalFormCache.invalidate(event.getCompanyId());
            log.info("[ApprovalFormCache] invalidate - companyId={}, formCode={}",
                    event.getCompanyId(), event.getFormCode());
        } catch (Exception e) {
            // 캐시 무효화 실패도 치명적이지 않음 — 재시도 없이 로그만
            log.error("[ApprovalFormUpdatedConsumer] 처리 실패: {}", message, e);
        }
    }
}
```

> **설계 결정 메모:**
> - `@RetryableTopic` **안 붙임** — 캐시 무효화는 실패해도 다음 양식 수정 시 다시 이벤트가 오거나, TTL/재기동으로 결국 해소됨. retry+DLT 오버헤드가 이득보다 큼.
> - `invalidate(companyId)` 로 회사 전체 캐시(SALARY + RETIREMENT) 를 한 번에 비움. `formCode` 만 골라 invalidate 하려면 `ApprovalFormCache` 에 `invalidate(companyId, formType)` 오버로드 추가.
> - consumer `groupId = "hr-form-cache-invalidate"` 는 hr-service 인스턴스들이 **각자 다 소비**하도록 다른 consumer group 과 분리.

##### (B) 선택적 백업 — TTL 캐시

Kafka 이벤트 놓치거나 publisher 가 실패한 경우 대비. Caffeine 을 써서 하드 TTL 적용:

```gradle
implementation 'com.github.ben-manes.caffeine:caffeine:3.1.8'
```

`ApprovalFormCache` 내부를 `Cache<Key, CachedForm>` 으로 교체:

```java
private final Cache<CacheKey, CachedForm> cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(30))   // 보험용 TTL — 30분이면 대부분의 관리자 편집은 반영됨
        .maximumSize(500)                            // 회사 × 양식 조합 상한
        .build();

public CachedForm get(UUID companyId, ApprovalFormType type) {
    return cache.get(new CacheKey(companyId, type), k -> fetch(k.companyId(), k.type()));
}

public void invalidate(UUID companyId) {
    cache.asMap().keySet().removeIf(k -> k.companyId().equals(companyId));
}

record CacheKey(UUID companyId, ApprovalFormType type) {}
```

이벤트(A)와 TTL(B)을 **병행**하면:
- 평상시: 이벤트로 즉시 무효화 (체감 지연 0)
- 이벤트 유실 시: 최대 30분 후 자동 재조회

##### (C) 안전망 — 관리자 전용 수동 invalidate 엔드포인트

운영 중 "양식이 이상하게 나와" 리포트 오면 즉시 대응할 수 있게:

```java
// hr-service - ApprovalDraftController 또는 별도 admin 컨트롤러
@RoleRequired({"HR_SUPER_ADMIN"})
@PostMapping("/admin/approval/cache/invalidate")
public ResponseEntity<Void> invalidateApprovalFormCache(
        @RequestHeader("X-User-Company") UUID companyId) {
    approvalFormCache.invalidate(companyId);
    log.info("[ApprovalFormCache] 수동 invalidate - companyId={}", companyId);
    return ResponseEntity.noContent().build();
}
```

operator 가 Postman/curl 로 호출해 즉시 캐시 비우기.

##### 7-7-6 체크리스트

```
[ ] common: ApprovalFormUpdatedEvent DTO 추가
[ ] collab: ApprovalFormUpdatedPublisher 신규 작성
[ ] collab: ApprovalFormService.updateForm() 끝에 publish() 호출 추가
[ ] hr: ApprovalFormUpdatedConsumer 신규 작성
[ ] (선택) hr: ApprovalFormCache 를 ConcurrentHashMap → Caffeine 으로 교체하여 TTL 추가
[ ] (선택) hr: 관리자 전용 수동 invalidate 엔드포인트 추가
```

##### 검증 시나리오

| # | 시나리오 | 기대 |
|---|---------|------|
| 1 | hr 에서 결의서 미리보기 → 1차 호출 | 캐시 miss → collab 호출 → 캐시에 v1 HTML 저장 |
| 2 | 2차 호출 (같은 회사) | 캐시 hit → 즉시 v1 반환 |
| 3 | collab 에서 관리자가 HTML 수정 → formVersion v2 로 저장 | `approval-form-updated` 이벤트 발행, hr 소비 → invalidate |
| 4 | 수정 직후 hr 에서 결의서 미리보기 | 캐시 miss (방금 invalidate) → collab 호출 → v2 HTML 반환 |
| 5 | Kafka 일시 장애로 이벤트 유실 | TTL(B) 적용 시 30분 뒤 자동 해소 / 미적용 시 수동 invalidate(C) 또는 재기동 필요 |

### 7-7 체크리스트 요약

```
[ ] common: PayrollApprovalDocCreatedEvent + SeveranceApprovalDocCreatedEvent 에 formId 필드 추가, formCode 주석 교체
[ ] hr: SeveranceApprovalDraftService.submit() 에 formId 주입
[ ] hr: PayrollApprovalDraftService.submit() 구현 (현재 빈 메서드)
[ ] collab: DocumentCreateRequest 에 hrRefType, hrRefId 필드 추가 (nullable)
[ ] collab: ApprovalDocumentService.createDocument — 엔티티 빌더에 hrRefType/Id 2줄 추가
[ ] collab: PayrollApprovalDocCreatedConsumer 신규 작성 (salary-approval-doc-created)
[ ] collab: SeveranceApprovalDocCreatedConsumer 신규 작성 (severance-approval-doc-created)
[ ] collab: ApprovalDocumentRepository#findByCompanyIdAndHrRefTypeAndHrRefId 쿼리 메서드 추가
[ ] collab: hrServiceClient#getEmployee 존재 확인 / 없으면 hr 쪽에 엔드포인트 추가
[ ] 멱등성: hr_ref_type + hr_ref_id unique 인덱스 + consumer 내부 중복 검사
[ ] 재시도: @RetryableTopic + DLT 설정 (CompanyFolderInitConsumer 참고)
```

> **배포 순서**: common 모듈(이벤트 DTO) 변경은 hr / collab 양쪽에 공유되므로 **두 서비스를 동시에 배포**해야 이벤트 직렬화/역직렬화 스키마가 맞음. 이 변경만은 롤링 배포 시 순서 주의.

---

## 8. 전환 순서 체크리스트

### Phase 1: collaboration-service 선결 (팀원 담당)

```
[ ] FIXED_FORM_CODES 키 불일치 해소 (둘 중 하나)
    옵션① 파일명 변경: 퇴직급여지급결의서.html → 퇴직금지급결의서.html
    옵션② 맵 키 변경: "보고-시행문/퇴직금지급결의서" → "보고-시행문/퇴직급여지급결의서"

[ ] 위 변경 반영된 collab 기동 → 기존 회사 DB 에 formCode 가 올바르게 등록됐는지 확인
    SELECT form_code, form_name, is_current FROM approval_form
     WHERE company_id = <테스트회사> AND form_code IN ('PAYROLL_RESOLUTION','SEVERANCE_RESOLUTION');

[ ] 기존 회사는 formCode 가 자동생성 이름("퇴직급여지급결의서_001") 으로 남아있을 수 있음 —
    필요 시 UPDATE 로 보정 or 회사 재생성
```

> 엔드포인트 신설 작업은 **없음**. `GET /approval/forms/by-code` 와 `GET /approval/forms/{formId}/edit` 둘 다 이미 존재.

### Phase 2: hr-service 리팩터링

```
[ ] hr-service 에 FormDetailResponse DTO 추가 (collab DTO 와 호환되는 최소 필드 + @JsonIgnoreProperties)
[ ] CollaborationClient#getFormDetailEditing(companyId, formId) 메서드 추가
[ ] ApprovalFormCache 신규 작성 (companyId × FormType → {formId, formHtml, formVersion})
[ ] ApprovalFormType.formCode 값 교체 (PAYROLL_RESOLUTION / SEVERANCE_RESOLUTION) + templateFileName 필드 제거
[ ] PayrollApprovalDraftService — templateLoader → approvalFormCache
[ ] SeveranceApprovalDraftService — 위와 동일
[ ] ApprovalHtmlTemplateLoader.java 파일 삭제
[ ] (있다면) resources/approval-templates/*.html 삭제
[ ] 컴파일 성공 확인
[ ] 단위테스트 수정 (ApprovalHtmlTemplateLoader mock 쓰던 곳 제거, CollaborationClient mock 으로 교체)
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
| 3 | 급여 결의서 미리보기 API 호출 (`/pay/admin/approval/salary/draft?payrollRunId=X`) | 200 OK, `htmlTemplate` 에 collab MinIO 최신 HTML 이 내려옴, `dataMap` 정상 채워짐 |
| 4 | 퇴직 결의서 미리보기 동일 | 위와 동일 |
| 5 | 캐시 확인 | 같은 회사에서 재호출 시 로그에 `[ApprovalFormCache] miss → fetch` 가 안 찍힘 (첫 호출만 찍힘) |
| 6 | 결재 상신 후 collab 측 `approval_document.form_id` 레코드 확인 | 정상적으로 PAYROLL_RESOLUTION/SEVERANCE_RESOLUTION formId 로 FK 연결 |
| 7 | **관리자가 양식 HTML 수정 → 저장 직후 hr 결의서 미리보기** | 수정된 HTML 이 반영돼 보여짐 (MinIO 최신본 기준). 단, hr 캐시 invalidate 가 필요한 경우 `ApprovalFormCache.invalidate(companyId)` 호출 또는 JVM 재기동 |
| 8 | 전자결재 UI 의 새 문서 작성 화면과 hr 결의서 미리보기 HTML 비교 | **완전히 동일**해야 함 (둘 다 `getFormDetailEditing` 경유) |

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

## 부록 B — hr-service 가 호출하는 collab REST API

전부 **기존 엔드포인트 재사용**. collab 측 신규 엔드포인트 작업 없음.

| 메서드 | 경로 | 헤더 | 파라미터 | 응답 | 용도 |
|--------|------|------|----------|------|------|
| POST | `/approval/init/formfolder` | `X-User-Company` | - | 201 | 회사 생성 시 수동 재트리거 (Kafka 실패 대비) |
| GET  | `/approval/forms/by-code` | `X-User-Company` | `?formCode=X` | `Long formId` | formCode → formId |
| GET  | `/approval/forms/{formId}/edit` | `X-User-Company` | path `formId` | `FormDetailResponse`<br>(formHtml = MinIO 최신본) | formId → 양식 상세(결의서 미리보기용) |

---

## 부록 C — 이전 코드 대비 메모리/네트워크 변화

| 항목 | 기존 | 신규 | 코멘트 |
|------|------|------|--------|
| 기동 시 I/O | `@PostConstruct` 에서 hr-service 가 직접 MinIO 2회 호출 | 없음 | 기동 빨라짐, `NoSuchBucket` 실패 지점 제거 |
| 양식 요청 시 (캐시 hit) | 메모리 캐시 | 메모리 캐시 | 동일 |
| 양식 요청 시 (캐시 miss) | — (기동 시 전부 로드) | hr→collab 2회 + collab→MinIO 1회 = ~100-200ms | 회사당 FormType 별 1회. 결의서 상신은 월 1~2회/회사라 실효 영향 없음 |
| 데이터 일관성 | hr 수동 업로드본 + collab 자동등록본 **이중화** | collab 단일 소스 (MinIO 최신본) | 관리자 양식 편집이 즉시 결의서 미리보기에 반영 |
| MinIO 직접 의존 | hr-service 가 직접 `approval-form` 버킷 접근 | hr-service 는 MinIO 직접 접근 없음. collab 만 MinIO 와 통신 | hr-service 의 MinIO 설정 불필요 (다른 기능에서 쓰지 않으면 `MinioClient` 빈도 제거 가능) |


## 부록 D — 용어 정리

| 용어 | 의미 |
|------|------|
| 양식(Form) | 결재 템플릿 (e.g. "급여지급결의서") — `approval_form` 테이블 |
| 양식 HTML | 템플릿 원문 (`{{drafterName}}` 같은 플레이스홀더 포함) |
| 결의서 | 양식을 기반으로 hr-service 가 데이터를 채워 만든 결재 대상 문서 |
| formCode | 양식의 영문 계약 식별자 (e.g. `PAYROLL_RESOLUTION`). hr/collab 공통 상수 |
| dataMap | 플레이스홀더 → 실제 값 매핑 (e.g. `{"drafterName":"홍길동"}`) |
| editing 경로 | collab 내부 로직 중 "새 문서 작성/양식 편집" 시 MinIO 최신본을 읽는 경로 |
