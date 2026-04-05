# 회사/계약 관리 CRUD 가이드

> WBS + ERD 기반 정리 | Spring Boot + JPA
> 작성일: 2026-04-04

---

## 1. 테이블 → Entity 매핑

### 1-1. Company Entity

```
패키지: com.peoplecore.entity
```

| ERD 컬럼 | Java 필드 | 타입 | 비고 |
|----------|-----------|------|------|
| company_id | companyId | UUID | @Id @UuidGenerator |
| company_name | companyName | String | @Column(unique=true, nullable=false) |
| founded_at | foundedAt | LocalDate | nullable |
| company_ip | companyIp | String | @Column(unique=true) |
| contract_start_at | contractStartAt | LocalDate | nullable=false |
| contract_end_at | contractEndAt | LocalDate | nullable=false |
| contract_type | contractType | ContractType(enum) | @Enumerated(STRING) |
| max_employees | maxEmployees | Integer | |
| company_status | companyStatus | CompanyStatus(enum) | @Enumerated(STRING), default PENDING |

**Enum 2개 필요:**
- `CompanyStatus`: PENDING, ACTIVE, SUSPENDED, EXPIRED
- `ContractType`: MONTHLY, YEARLY

**비즈니스 메서드 (Entity 안에 작성):**
- `changeStatus(CompanyStatus newStatus)` — 상태 변경 + 검증
- `extendContract(LocalDate newEndDate, Integer newMaxEmployees, ContractType newType)` — 연장 처리, EXPIRED면 ACTIVE 복구

---

### 1-2. ContractNotification Entity

```
패키지: com.peoplecore.entity
```

| ERD 컬럼 | Java 필드 | 타입 | 비고 |
|----------|-----------|------|------|
| contract_notification_id | id | Long | @Id @GeneratedValue(IDENTITY) |
| company_id | company | Company | @ManyToOne @JoinColumn(name="company_id") |
| notified_at | notifiedAt | LocalDateTime | 알림 발송 시각 |
| days_before | daysBefore | Integer | 30, 14, 7, 1 중 하나 |

**중복 발송 방지 로직:** company_id + days_before 조합으로 이미 발송 여부 체크

---

## 2. API 엔드포인트 & CRUD 명세

### 2-1. 회사 등록 (기본정보 + 최고관리자 계정)

```
POST /internal/companies
```

**Request Body:**
```json
{
  "companyName": "테스트기업",
  "foundedAt": "2020-03-15",
  "companyIp": "192.168.1.100",
  "contractStartAt": "2026-04-01",
  "contractEndAt": "2027-03-31",
  "contractType": "YEARLY",
  "maxEmployees": 50,
  "adminName": "홍길동",
  "adminEmail": "admin@test.com"
}
```

**처리 흐름:**
1. Company 저장 (companyStatus = PENDING, UUID 자동 발급)
2. Employee 테이블에 ADMIN 계정 생성 (emp_role = 'ADMIN')
3. 임시 비밀번호 생성 (SecureRandom, 12자리)
4. 이메일 발송: 회사 UUID + 계정정보 (adminEmail로)
5. companyStatus → ACTIVE 변경

**Response:** 201 Created
```json
{
  "company": {
    "companyId": "uuid-...",
    "companyName": "테스트기업",
    "companyStatus": "ACTIVE",
    ...
  },
  "admin": {
    "companyId": "uuid-...",
    "adminEmail": "admin@test.com",
    "adminName": "홍길동",
    "message": "임시 비밀번호가 admin@test.com로 전달되었습니다"
  }
}
```

**필요 클래스:**
- DTO: `CompanyCreateRequest`, `CompanyCreateResponse`, `CompanyResponse`, `AdminAccountResponse`
- Service: `CompanyService.createCompany()`, `AdminAccountService.createAdminAccount()`

---

### 2-2. 회사 단건 조회

```
GET /internal/companies/{companyId}
```

**처리:** companyRepository.findById → CompanyResponse 반환

**Response:** 200 OK → CompanyResponse

---

### 2-3. 회사 목록 조회 (상태별 필터)

```
GET /internal/companies
GET /internal/companies?status=ACTIVE
```

**처리:** status 파라미터 있으면 findByStatus, 없으면 findAll

**Response:** 200 OK → List<CompanyResponse>

---

### 2-4. 계약 상태 변경

```
PATCH /internal/companies/{companyId}/status
```

**Request Body:**
```json
{
  "status": "SUSPENDED",
  "reason": "미납으로 인한 일시 중지"
}
```

**상태 전이 규칙 (Service에서 검증):**
```
PENDING  → ACTIVE       (계약 확정)
ACTIVE   → SUSPENDED    (관리자 수동 중지)
ACTIVE   → EXPIRED      (수동 만료)
SUSPENDED → ACTIVE      (재활성화)
```
그 외 전이는 IllegalStateException 발생

**상태별 서비스 접근 제한 (별도 Filter/Interceptor에서 처리):**
| 상태 | 로그인 | 메시지 |
|------|--------|--------|
| ACTIVE | 허용 | - |
| PENDING | 차단 | "계약 확정 대기 중입니다" |
| SUSPENDED | 차단 | "서비스가 일시 중지되었습니다" |
| EXPIRED | 차단 | "계약이 만료되었습니다" |

**필요 클래스:**
- DTO: `CompanyStatusUpdateRequest`
- Service: `CompanyService.updateStatus()` + `validateStatusTransition()`

---

### 2-5. 계약 연장

```
PATCH /internal/companies/{companyId}/contract/extend
```

**Request Body:**
```json
{
  "newContractEndAt": "2028-03-31",
  "maxEmployees": 100,
  "contractType": "YEARLY"
}
```

**처리 흐름:**
1. 만료일 재설정 (contractEndAt = newContractEndAt)
2. maxEmployees, contractType 변경 (값이 있으면만)
3. EXPIRED 상태이면 → ACTIVE 자동 복구
4. contract_notification 테이블에서 해당 회사 알림 이력 삭제 (초기화)

**필요 클래스:**
- DTO: `ContractExtendRequest`
- Service: `CompanyService.extendContract()`

---

## 3. 스케줄러 (계약 만료 알림 / 만료 처리)

### 3-1. 계약 만료 알림 스케줄러

```
클래스: ContractNotificationScheduler
@Scheduled(cron = "0 0 9 * * *")  ← 매일 오전 9시
```

**로직:**
```
D-day 목록 = [30, 14, 7, 1]

for each dDay in D-day 목록:
    targetDate = 오늘 + dDay일

    대상 회사 = companyRepository에서 조회:
        WHERE company_status = 'ACTIVE'
        AND contract_end_at = targetDate

    for each company in 대상 회사:
        // 중복 발송 방지
        이미발송 = notificationRepository.existsByCompanyAndDaysBefore(company, dDay)
        if (이미발송) continue

        // 이메일 발송 (내부 담당자 CC)
        emailService.sendExpiryNotification(company, dDay)

        // 알림 이력 저장
        notification = ContractNotification(company, now(), dDay)
        notificationRepository.save(notification)
```

**Repository 쿼리:**
```java
// CompanyRepository
@Query("SELECT c FROM Company c WHERE c.companyStatus = 'ACTIVE' AND c.contractEndAt = :targetDate")
List<Company> findActiveCompaniesByContractEndAt(@Param("targetDate") LocalDate targetDate);

// ContractNotificationRepository
boolean existsByCompany_CompanyIdAndDaysBefore(UUID companyId, Integer daysBefore);
```

---

### 3-2. 계약 만료 처리 스케줄러

```
클래스: ContractExpirationScheduler
@Scheduled(cron = "0 1 0 * * *")  ← 매일 자정 00:01
```

**로직:**
```
만료 대상 = companyRepository에서 조회:
    WHERE company_status = 'ACTIVE'
    AND contract_end_at <= 오늘

for each company in 만료 대상:
    company.changeStatus(EXPIRED)
    log.info("계약 만료 자동 전환: {}", company.companyId)
```

**Repository 쿼리:**
```java
@Query("SELECT c FROM Company c WHERE c.companyStatus = 'ACTIVE' AND c.contractEndAt <= :today")
List<Company> findExpiredCompanies(@Param("today") LocalDate today);
```

---

## 4. 전체 클래스 구조 요약

```
com.peoplecore
├── entity/
│   ├── Company.java
│   └── ContractNotification.java
├── enums/
│   ├── CompanyStatus.java          (PENDING, ACTIVE, SUSPENDED, EXPIRED)
│   └── ContractType.java           (MONTHLY, YEARLY)
├── dto/
│   ├── CompanyCreateRequest.java   (회사등록 요청)
│   ├── CompanyCreateResponse.java  (회사등록 응답 = company + admin)
│   ├── CompanyResponse.java        (회사 정보 응답)
│   ├── CompanyStatusUpdateRequest.java (상태변경 요청)
│   ├── ContractExtendRequest.java  (계약연장 요청)
│   └── AdminAccountResponse.java   (관리자계정 응답)
├── repository/
│   ├── CompanyRepository.java
│   └── ContractNotificationRepository.java
├── service/
│   ├── CompanyService.java         (핵심 비즈니스 로직)
│   └── AdminAccountService.java    (관리자 계정 + 임시비번)
├── controller/
│   └── InternalCompanyController.java
└── scheduler/
    ├── ContractNotificationScheduler.java  (D-30/14/7/1 알림)
    └── ContractExpirationScheduler.java    (자정 만료 처리)
```

---

## 5. 주의사항

1. **최고관리자는 Employee 테이블에** emp_role = 'ADMIN'으로 생성 (company 테이블 아님)
2. **상태별 로그인 차단**은 Controller가 아닌 Security Filter/Interceptor에서 처리
3. **알림 중복 방지**는 contract_notification 테이블의 (company_id, days_before) 조합으로 체크
4. **계약 연장 시** contract_notification 이력 삭제 → 새 주기로 다시 알림 가능
5. **company_ip UNIQUE** 제약 있음 → 등록 시 중복 체크 필요
6. **company_name UNIQUE** 제약 있음 → 등록 시 중복 체크 필요
