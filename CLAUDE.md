# CLAUDE.md - 전자결재 시스템 가이드

## 프로젝트 개요
- 전자결재 시스템 (PeopleCore) - Spring Boot 마이크로서비스 아키텍처
- Java 17 / Spring Boot 3.5.13 / JPA / QueryDSL / Redis / Kafka

---

## 핵심 설계 원칙

### 상태 패턴 (State Pattern)
- if-else로 짜지 말고 **상태 패턴**을 적용해 객체지향적으로 설계
### 동시성 제어 (Concurrency Control)
- 결재자가 승인 버튼을 누르는 찰나에 기안자가 문서를 회수하는 경우 → **낙관적 락(Optimistic Lock)** 으로 데이터 충돌 방지
- `ApprovalDocument`에 `@Version` 적용 → JPA가 UPDATE 시 `WHERE version = N` 자동 부여
- `ApprovalSeqCounter`에 `@Version` 적용 → 채번 동시성 보장
- 레디스를 쓴다면 레디스를 통한 분산 락으로 해결

### 파일 업로드 보안
- MinIO 같은 클라우드 스토리지에 업로드할 경우 **Pre-signed URL**을 통한 접근 권한 제어 검토

### 쿼리 최적화
- 결재 문서가 수만 건 쌓였을 때 인덱스 설계는 기본
- 복잡한 검색 조건을 위해 **QueryDSL을 활용한 동적 쿼리 최적화** 적용
- 종속성에서 발견된 취약점:
CVE-2024-49203
9.8
SQL Injection
세부 정보 표시…
Mend.io 기반 결과
고려
- 모든 연관 엔티티 조회 시 **N+1 문제 반드시 해결** (JOIN FETCH 또는 QueryDSL)

### 코드 최적화 및 효율성 
- erd 구조를 파악하고 코드를 최고의 효율성으로
- 한 단계씩 순차적으로 진행 
- 코드 추가 및 수정 삭제시 코드및 파일 위치 명시 
- for문 또는 stram으로 반복적 사용보다는 재귀 형식으로 효율성 증대

### 공통 코드 엔티티 사용 권장
- collaboration-service모듈에 있는 코드를 작성할때면 해당 서비스 모듈에 있는 common/entity에 있는 공통 테이블 사용 권장 
- 
---

## 비즈니스 로직

1. **결재 프로세스 엔진** - 순차합의(다음 결재자 자동 활성화), 병렬합의(동시 결재) 처리
2. **문서번호 자동채번** - `number_rule` 테이블 기반 (부서코드-양식코드-날짜-순번)
3. **위임 자동 처리** - 결재 요청 시 부재자이면 대결자에게 자동 전달 (`is_delegated=true`)
4. **알림 발송** - 결재 요청/승인/반려 시 알림 테이블 INSERT + 실시간 알림(WebSocket/SSE)
5. **문서 상태 자동 변경** - 마지막 결재자 승인 시 `doc_status=완료` 자동 전환
6. **긴급문서 우선처리** - `is_emergency=true` 문서 우선 정렬

---

## 인프라

1. **파일 업로드** - MinIO 연동, multipart 처리
2. **서명 이미지 업로드** - S3 저장, URL 반환
3. **페이지네이션** - 모든 목록 API에 Pageable 적용
4. **권한 체크** - 부서 문서함 설정은 인사과만 가능, 문서 접근 권한 검증

---

## 인가 (Authorization)

### @RoleRequired 어노테이션
- API별 역할 기반 접근 제어는 `@RoleRequired` 커스텀 어노테이션으로 처리
- 흐름: 요청 → Gateway (JWT 검증, X-User-Role 헤더 추가) → 각 서비스 (RoleInterceptor가 X-User-Role과 @RoleRequired 비교) → 불일치 시 403 → 일치 시 컨트롤러 실행

```java
// 사용 예시
@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
@PostMapping
public ResponseEntity<...> create(...) { ... }
```

### 컴포넌트 스캔 설정 (필수)
- `@RoleRequired`가 동작하려면 common 패키지의 `RoleInterceptor`가 빈으로 등록되어야 함
- 각 서비스의 `@SpringBootApplication`에서 common 패키지를 스캔 범위에 포함해야 함

```java
@SpringBootApplication(scanBasePackages = {
    "com.peoplecore.collaboration_service",
    "com.peoplecore.common"   // ← 이게 있어야 RoleInterceptor가 빈으로 등록됨
})
```

---

## 커스텀 헤더 (API Gateway → 서비스)

```
X-User-Id        → 사원 ID (claims.getSubject())
X-User-Company   → 회사 ID (UUID)
X-User-Name      → 사원 이름
X-User-Role      → 사원 역할
X-User-Department → 부서 ID
X-User-Grade     → 직급 ID
X-User-Title     → 직책 ID
```

---

## 주요 Request/Response 참고

### 문서 기안 Request Body

```json
{
  "formId": 1,
  "docTitle": "결재문서 제목",
  "docData": { "field1": "value1", "field2": "value2" },
  "isEmergency": false,
  "opinion": "기안의견",
  "approvers": [
    { "empId": 1, "lineRole": "결재자", "lineStep": 1 },
    { "empId": 2, "lineRole": "결재자", "lineStep": 2 }
  ],
  "ccList": [
    { "empId": 3, "lineRole": "참조" }
  ],
  "viewers": [
    { "empId": 4, "lineRole": "열람" }
  ],
  "deptFolderId": 1,
  "personalFolderId": null
}
```

### 문서 상세 Response Body

```json
{
  "docId": 1,
  "docNum": "인사-채용-260402-001",
  "docTitle": "2026년 상반기 개발팀 채용요청",
  "docStatus": "진행중",
  "isEmergency": false,
  "createdAt": "2026-04-02T10:00:00",
  "form": {
    "formId": 1,
    "formName": "채용요청",
    "formHtml": "<div>...</div>",
    "folderName": "인사",
    "retentionYear": 5
  },
  "docData": { "field1": "value1" },
  "drafter": {
    "empId": 1,
    "empName": "김인재",
    "deptName": "경영",
    "gradeName": "차장",
    "titleName": "차장"
  },
  "approvalLines": [
    {
      "lineId": 1,
      "empId": 2,
      "empName": "강희계",
      "lineRole": "결재자",
      "lineStep": 1,
      "lineStatus": "승인",
      "lineComment": "승인합니다",
      "isDelegated": false,
      "updatedAt": "2026-04-02T11:00:00"
    }
  ],
  "attachments": [
    {
      "attachId": 1,
      "fileName": "첨부파일.pdf",
      "fileSize": 1024000,
      "fileUrl": "https://..."
    }
  ]
}
```

### 문서 목록 공통 Response

```json
{
  "content": [
    {
      "docId": 1,
      "docTitle": "제목",
      "docNum": "인사-채용-260402-001",
      "docStatus": "진행중",
      "isEmergency": true,
      "formName": "채용요청",
      "drafterName": "김인재",
      "drafterDept": "경영",
      "createdAt": "2026-04-02",
      "hasAttachment": true
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 50,
  "totalPages": 5
}
```
