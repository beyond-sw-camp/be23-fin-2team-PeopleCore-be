# 퇴직연금 적립 내역 관리 로직 명세 — 어드민 / 슈퍼어드민 급여관리 탭

> **프로젝트**: PeopleCore HR
> **모듈**: hr-service
> **대상 사용자**: `HR_SUPER_ADMIN`, `HR_ADMIN`
> **관련 화면**: 급여관리 > 퇴직연금 적립 내역 (PensionDeposits)
> **작성일**: 2026-04-22

---

## 1. 개요

회사가 DC형 퇴직연금 대상 사원들에게 매월 적립한 기록을 **전사 단위로 조회·감사·수동 조정**하는 화면의 서버 로직. 퇴직금대장(작성)의 DC 차액 산정이 정확하려면 이 테이블(`retirement_pension_deposits`)에 데이터가 누락 없이 쌓여야 한다.

**이 문서의 범위**
- 적립 내역 목록/상세 조회 (재직자 포함 전사)
- 수동 적립 입력(소급/누락 보완)
- 적립 취소 (오입력 정정)
- 월별 대시보드용 요약

**이 문서가 다루지 않는 것**
- DC 적립 자동 INSERT (급여 지급처리 시점 자동 적립은 `PayrollService.processPayment()`에서 담당, 별도 로직)
- 퇴직금 산정 (별도 문서 `admin-severance-estimate-logic.md`)
- 사원 본인 조회 (별도 문서 `employee-severance-estimate-logic.md`)

### 자동 적립과의 관계

```
[급여 지급처리]
  PayrollService.processPayment()
    └─ createDcDeposits(run, company)
         └─ RetirementPensionDeposits INSERT (status=COMPLETED)
                ↑
                이 본 문서의 관리 대상 테이블
                (관리자가 이 화면에서 읽고, 필요시 수동 조정)
```

---

## 2. 화면 개요

| 섹션 | 내용 |
|---|---|
| 상단 필터 | 연월 범위, 사원명 검색, 부서 필터, 상태(COMPLETED/PENDING) |
| 요약 카드 | 기간 내 적립 대상자 수 · 적립 총액 · 월평균 적립액 · 누적 적립액 |
| 테이블 | 사원명 · 부서 · 적립월(payYearMonth) · 기준임금 · 적립금액 · 상태 · 적립일시 |
| 행 액션 | 상세 · 취소(소프트 삭제) |
| 별도 버튼 | 수동 적립 등록 (누락/소급용) |

---

## 3. API 스펙

### 3-1. 목록 조회 — `GET /pay/admin/pension-deposits`

| 항목 | 값 |
|---|---|
| Header | `X-User-Company: {UUID}` |
| Query | `fromYm`, `toYm`, `empId?`, `deptId?`, `status?`, `page`, `size` |
| 응답 | `PensionDepositSummaryResDto` |

**응답 필드 (`PensionDepositSummaryResDto`)**
| 필드 | 타입 | 설명 |
|---|---|---|
| `totalEmployees` | Integer | 기간 내 적립된 고유 사원 수 |
| `totalDepositAmount` | Long | 기간 내 적립 총액 |
| `monthlyAverage` | Long | 월평균 적립액 |
| `grandTotalDeposited` | Long | 회사 전체 누적 적립 (기간 무관) |
| `deposits` | Page<PensionDepositRes> | 페이징된 리스트 |

**행 DTO (`PensionDepositRes`)**
| 필드 | 타입 | 설명 |
|---|---|---|
| `depId` | Long | PK |
| `empId` / `empName` | Long / String | 사원 |
| `deptName` | String | 부서 (스냅샷이 아닌 현재) |
| `payYearMonth` | String | 적립 기준 월 |
| `baseAmount` | Long | 적립기준임금 |
| `depositAmount` | Long | 적립 금액 |
| `depStatus` | String | `COMPLETED` / `PENDING` |
| `depositDate` | LocalDateTime | 적립 일시 |
| `payrollRunId` | Long | 자동 적립일 경우 급여대장 ID (수동은 `null`) |
| `isManual` | Boolean | 수동 등록 여부 (`payrollRunId == null` 판단) |

### 3-2. 사원별 이력 — `GET /pay/admin/pension-deposits/employee/{empId}`

| 항목 | 값 |
|---|---|
| Header | `X-User-Company: {UUID}` |
| Path | `empId` |
| Query | `fromYm?`, `toYm?` |
| 응답 | `PensionDepositEmployeeResDto` |

**응답 필드**
| 필드 | 타입 | 설명 |
|---|---|---|
| `empId` / `empName` / `deptName` / `retirementType` | - | 사원 정보 |
| `totalDeposited` | Long | 누적 적립액 |
| `estimatedSeverance` | Long | 오늘 퇴직 시 예상 퇴직금 (법정 산식) |
| `expectedDiffAmount` | Long | 오늘 퇴직 시 회사가 추가 지급할 차액 |
| `deposits` | List<PensionDepositRes> | 월별 이력 (최신순) |

### 3-3. 수동 적립 등록 — `POST /pay/admin/pension-deposits`

- **용도**: 자동 적립이 누락된 월 또는 소급 반영이 필요한 경우
- **Body** (`PensionDepositCreateReqDto`):
  ```json
  {
    "empId": 123,
    "payYearMonth": "2026-03",
    "baseAmount": 3500000,
    "depositAmount": 291666,
    "depStatus": "COMPLETED",
    "reason": "2026년 3월 급여 지급처리 누락분 소급"
  }
  ```
- **검증**:
  - 사원이 DC형인지 (아니면 `EMPLOYEE_NOT_DC`)
  - 동일 사원·동일 월 `COMPLETED` 이미 존재 시 `DEPOSIT_ALREADY_EXISTS` (감사 로그 남기고 거부)
- **저장 시 `payrollRunId = null`** (수동 표식)
- **응답**: 생성된 `PensionDepositRes`

### 3-4. 적립 취소 — `DELETE /pay/admin/pension-deposits/{depId}`

- 소프트 삭제 (`depStatus = PENDING`로 전환 + `canceledAt`, `canceledBy`, `cancelReason` 저장 — 엔티티 확장 필요)
- 또는 하드 삭제: 감사 목적상 비권장 → **상태만 전이**시키는 방식 권장

### 3-5. 월별 요약 — `GET /pay/admin/pension-deposits/monthly-summary`

| Query | `year` (기본 현재 연도) |
| 응답 | `List<MonthlyDepositSummaryDto>` — `{ yearMonth, count, totalAmount }` |

차트 등에 사용.

---

## 4. 처리 절차

### 4-1. 목록 조회 (`getDepositList`)

```
1) Company 검증
2) QueryDSL 빌더:
   - companyId 필수
   - payYearMonth BETWEEN fromYm AND toYm
   - empId / deptId / status 동적 조건
3) 집계 쿼리: totalEmployees, totalDepositAmount, monthlyAverage
4) 페이징 쿼리: deposits
5) grandTotalDeposited 별도 쿼리 (기간 무관)
6) PensionDepositSummaryResDto 반환
```

### 4-2. 수동 등록 (`createManualDeposit`)

```
1) Employee 조회 + DC 검증
   - retirementType != DC → EMPLOYEE_NOT_DC
2) 중복 체크
   - existsByEmpIdAndPayYearMonthAndDepStatus(empId, payYearMonth, COMPLETED)
   - 존재하면 DEPOSIT_ALREADY_EXISTS
3) RetirementPensionDeposits 엔티티 저장
   - payrollRunId = null (수동)
   - depositDate = LocalDateTime.now()
   - depStatus = reqDto.depStatus (기본 COMPLETED)
   - reason은 별도 audit log 테이블에 남기거나 엔티티 컬럼 확장
4) 감사 로그 INFO:
   [PensionDeposit 수동 등록] empId=?, payYearMonth=?, amount=?, by=adminEmpId
5) 응답 DTO 반환
```

### 4-3. 적립 취소 (`cancelDeposit`)

```
1) depId로 조회 + companyId 일치 검증
2) 현재 상태가 COMPLETED인지 확인 (이미 CANCELED면 예외)
3) 엔티티 메서드 cancel(adminEmpId, reason) 호출
   - depStatus → PENDING
   - canceledBy / canceledAt / cancelReason 세팅
4) 감사 로그 WARN:
   [PensionDeposit 취소] depId=?, empId=?, by=?
```

---

## 5. 엔티티 확장 필요 사항

현재 `RetirementPensionDeposits`에 추가 권장 컬럼:

| 필드 | 타입 | 용도 |
|---|---|---|
| `isManual` | Boolean | 수동 등록 여부 (편의, `payrollRunId IS NULL` 대체 가능) |
| `canceledAt` | LocalDateTime? | 취소 일시 |
| `canceledBy` | Long? | 취소한 관리자 empId |
| `cancelReason` | String? | 취소 사유 |
| `createdBy` | Long? | 수동 등록한 관리자 empId |
| `createReason` | String? | 수동 등록 사유 (소급 등) |

`payrollRunId`의 NOT NULL 제약을 **nullable**로 완화해야 수동 등록이 가능함.

---

## 6. 예외 처리

| ErrorCode | 발생 조건 |
|---|---|
| `EMPLOYEE_NOT_FOUND` | empId 존재하지 않음 |
| `EMPLOYEE_NOT_DC` | 대상 사원이 DC형이 아님 |
| `DEPOSIT_ALREADY_EXISTS` | 동일 사원·동일 월 `COMPLETED` 이미 존재 |
| `DEPOSIT_NOT_FOUND` | depId로 조회 실패 |
| `DEPOSIT_ALREADY_CANCELED` | 이미 취소된 건 재취소 시도 |
| `COMPANY_NOT_FOUND` | companyId 불일치 |

---

## 7. 권한 / 감사

- `@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})` — 두 권한 모두 조회/수동등록/취소 가능
- 모든 **쓰기 작업**(수동 등록/취소)은 감사 로그 필수:
  - Logger INFO/WARN
  - 장기적으로는 별도 `audit_log` 테이블에 (action, targetDepId, adminEmpId, before, after, at) 저장 권장

---

## 8. 파일 위치

| 유형 | 경로 (예정) |
|---|---|
| Controller | `hr-service/.../pay/controller/PensionDepositController.java` |
| Service | `hr-service/.../pay/service/PensionDepositService.java` |
| Repository | `hr-service/.../pay/repository/RetirementPensionDepositsRepository.java` |
| QueryDSL Impl | `hr-service/.../pay/repository/PensionDepositQueryRepositoryImpl.java` |
| Entity | `hr-service/.../pay/domain/RetirementPensionDeposits.java` (확장) |
| 목록 응답 | `hr-service/.../pay/dtos/PensionDepositRes.java` |
| 요약 응답 | `hr-service/.../pay/dtos/PensionDepositSummaryResDto.java` |
| 사원별 응답 | `hr-service/.../pay/dtos/PensionDepositEmployeeResDto.java` |
| 월별 요약 응답 | `hr-service/.../pay/dtos/MonthlyDepositSummaryDto.java` |
| 수동 등록 요청 | `hr-service/.../pay/dtos/PensionDepositCreateReqDto.java` |

---

## 9. 화면 관점 흐름 요약

```
[급여관리 > 퇴직연금 적립 내역]
  ├─ 기간/사원/부서/상태 필터 → 조회
  │    └─ GET /pay/admin/pension-deposits
  │         └─ 목록 + 요약 카드 렌더링
  │
  ├─ 사원명 클릭 → 사원별 이력 모달
  │    └─ GET /pay/admin/pension-deposits/employee/{empId}
  │         └─ 월별 적립 이력 + 예상 퇴직금 + 예상 차액
  │
  ├─ [+ 수동 적립 등록] 버튼 → 모달
  │    ├─ 사원 선택 (DC형만)
  │    ├─ 적립월 / 기준임금 / 적립금액 / 사유
  │    └─ POST /pay/admin/pension-deposits
  │
  └─ 행 [취소] 버튼 → 확인 다이얼로그
       └─ DELETE /pay/admin/pension-deposits/{depId}
            └─ status = PENDING 로 전환 (감사 보존)
```

---

## 10. 자동 적립과의 통합

- 자동 적립(`PayrollService.createDcDeposits`)이 실패하거나 누락된 경우 이 화면에서 수동으로 보완
- 자동 적립 건과 수동 적립 건은 `isManual`/`payrollRunId` 여부로 구분
- 테이블 행에 표기:
  - `payrollRunId`가 있으면 "자동 - {runId}" 표시
  - 없으면 "수동" 뱃지

---

## 11. 향후 개선 포인트

- **DB형 납입 이력 연동**: 현재는 DC만 대상. DB형의 외부 금융기관 납입 이력도 유사 구조로 관리 가능
- **대시보드 차트**: 월별 적립 총액 추이 그래프, 부서별 적립 비중 파이
- **연말 정산 보고서**: 연도 선택 시 사원별 연간 적립 합계 Excel 다운로드
- **이상치 알림**: 특정 사원의 연속 월 누락 감지 시 관리자에게 경고
- **소급 일괄 입력**: 과거 데이터 마이그레이션용 벌크 업로드 API
