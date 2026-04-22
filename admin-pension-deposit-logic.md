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
| 상단 필터 | 연월 범위, 사원명 검색, 부서 필터, 상태(SCHEDULED/COMPLETED/CANCELED) |
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
| `depStatus` | String | `SCHEDULED` / `COMPLETED` / `CANCELED` |
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
| `deposits` | List<PensionDepositRes> | 월별 이력 (최신순) |

> **퇴직금 추계치는 이 응답에 포함하지 않음.**  
> 예상 퇴직금 · 예상 차액 · 근속연수 · 1일 평균임금 등은 별도 화면(`/payroll/severance-estimate`, 본 문서 범위 외)에서 제공. 프론트엔드 모달에서는 "상세 추계 보기 →" 링크로 그쪽으로 유도.

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

- 소프트 삭제 (`depStatus = CANCELED`로 전환 + `canceledAt`, `canceledBy`, `cancelReason` 저장 — 엔티티 확장 필요)
- 또는 하드 삭제: 감사 목적상 비권장 → **상태만 전이**시키는 방식 권장

### 3-6. 사원별 집계 조회 — `GET /pay/admin/pension-deposits/by-employee`

**화면용 집계 엔드포인트.** 같은 사원의 여러 달 적립을 **1명 1행으로 집계**해서 반환.

| 항목 | 값 |
|---|---|
| Header | `X-User-Company: {UUID}` |
| Query | `fromYm`, `toYm`, `search?` (사원명), `status?` (COMPLETED/CANCELED), `deptId?` |
| 응답 | `PensionDepositByEmployeeSummaryResDto` |

**응답 구조**
| 필드 | 타입 | 설명 |
|---|---|---|
| `totalEmployees` | Integer | 집계 사원 수 |
| `totalDepositAmount` | Long | 기간 내 적립 총액 (COMPLETED만) |
| `monthlyAverage` | Long | 월평균 적립액 |
| `grandTotalDeposited` | Long | 회사 전체 누적 (기간 무관) |
| `employees` | List<PensionDepositByEmployeeRes> | 사원별 행 |

**사원별 행 (`PensionDepositByEmployeeRes`)**
| 필드 | 타입 | 설명 |
|---|---|---|
| `empId` / `empName` / `deptName` | - | 사원 기본 정보 |
| `monthCount` | Integer | 기간 내 COMPLETED 건수 |
| `totalAmount` | Long | 기간 내 적립 합계 |
| `lastDepositDate` | LocalDateTime | 가장 최근 적립일시 |
| `hasManual` | Boolean | 수동 적립 포함 여부 |
| `hasCanceled` | Boolean | 취소 건 포함 여부 |

> 사원명 클릭 시 **`GET /pay/admin/pension-deposits/employee/{empId}`** (§3-2)로 월별 상세 조회.

---

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
   - depStatus → CANCELED
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
  │         └─ 월별 적립 이력 (추계치는 퇴직금추계액 화면으로 링크)
  │
  ├─ [+ 수동 적립 등록] 버튼 → 모달
  │    ├─ 사원 선택 (DC형만)
  │    ├─ 적립월 / 기준임금 / 적립금액 / 사유
  │    └─ POST /pay/admin/pension-deposits
  │
  └─ 행 [취소] 버튼 → 확인 다이얼로그
       └─ DELETE /pay/admin/pension-deposits/{depId}
            └─ status = CANCELED 로 전환 (감사 보존)
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

---

## 12. 구현 코드

### 12-0. DepStatus enum 확장

**`hr-service/.../pay/enums/DepStatus.java`**
```java
package com.peoplecore.pay.enums;

public enum DepStatus {
    SCHEDULED,   // 적립예정 — 확정됐으나 아직 지급처리 전 (예약 등록)
    COMPLETED,   // 적립완료 — 자동(지급처리) 또는 수동 등록 시
    CANCELED     // 취소 — 관리자가 오입력/재조정 사유로 취소 처리 (이력 보존)
}
```

### 상태 전이

```
SCHEDULED → COMPLETED   (지급처리 시점에 확정)
COMPLETED → CANCELED    (관리자 취소)
CANCELED  → (최종 상태, 재전이 불가)
```

> 퇴직금 DC 차액 계산 시 `COMPLETED`만 합산. `CANCELED` 건은 자연스럽게 제외됨.

---

### 12-1. 엔티티 확장

기존 `RetirementPensionDeposits`에:
- `empId` → **`Employee` `@ManyToOne`** 으로 변경 (FK 제약 + 조인 편의)
- `payrollRunId` → **`PayrollRuns` `@ManyToOne`** 으로 변경 (nullable, 수동은 null)
- 취소/수동 등록 관리 컬럼 추가

**`hr-service/.../pay/domain/RetirementPensionDeposits.java`**
```java
package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.pay.enums.DepStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "retirement_pension_deposits")
public class RetirementPensionDeposits {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long depId;

    // ✅ Employee @ManyToOne 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private Long baseAmount;

    @Column(nullable = false)
    private Long depositAmount;

    private LocalDateTime depositDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepStatus depStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // ✅ PayrollRuns @ManyToOne 관계 (nullable, 수동 등록은 null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id")
    private PayrollRuns payrollRun;

    /** 수동 등록 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isManual = false;

    /** 적립 기준 월 (YYYY-MM). 중복 체크용 */
    @Column(nullable = false, length = 7)
    private String payYearMonth;

    /** 수동 등록 시 사유 / 취소 시 사유 */
    @Column(length = 500)
    private String reason;

    /** 수동 등록한 관리자 empId */
    private Long createdBy;

    /** 취소 처리 시각 */
    private LocalDateTime canceledAt;

    /** 취소한 관리자 empId */
    private Long canceledBy;

    // ── 상태 전이 메서드 ──
    public void cancel(Long adminEmpId, String cancelReason) {
        if (this.depStatus == DepStatus.CANCELED) {
            throw new IllegalStateException("이미 취소된 적립입니다.");
        }
        this.depStatus = DepStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        this.canceledBy = adminEmpId;
        this.reason = cancelReason;
    }
}
```

> **DB 컬럼 이름 유지**: `@JoinColumn(name="emp_id")`, `@JoinColumn(name="payroll_run_id")` 덕분에 **기존 DB 테이블 스키마는 그대로**. 객체 필드만 `Long` → `Employee`/`PayrollRuns`로 바뀜. 마이그레이션 SQL도 수정 불필요 (FK 제약을 DB에 추가하려면 아래 SQL 참고).

> **(선택) FK 제약 DB에 추가**:
> ```sql
> ALTER TABLE retirement_pension_deposits
>   ADD CONSTRAINT fk_rpd_emp FOREIGN KEY (emp_id) REFERENCES employee(emp_id),
>   ADD CONSTRAINT fk_rpd_run FOREIGN KEY (payroll_run_id) REFERENCES payroll_runs(payroll_run_id);
> ```

> **마이그레이션 SQL**
> ```sql
> ALTER TABLE retirement_pension_deposits
>   ADD COLUMN is_manual BOOLEAN NOT NULL DEFAULT FALSE,
>   ADD COLUMN pay_year_month VARCHAR(7) NOT NULL DEFAULT '',
>   ADD COLUMN reason VARCHAR(500),
>   ADD COLUMN created_by BIGINT,
>   ADD COLUMN canceled_at DATETIME,
>   ADD COLUMN canceled_by BIGINT,
>   MODIFY COLUMN payroll_run_id BIGINT NULL;
> ```

---

### 12-2. Repository

**`hr-service/.../pay/repository/RetirementPensionDepositsRepository.java`**
```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.RetirementPensionDeposits;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RetirementPensionDepositsRepository
        extends JpaRepository<RetirementPensionDeposits, Long>,
                PensionDepositQueryRepository {

    // ✅ ManyToOne 경로로 변경
    boolean existsByPayrollRun_PayrollRunIdAndEmployee_EmpId(Long payrollRunId, Long empId);

    boolean existsByEmployee_EmpIdAndPayYearMonthAndDepStatus(
            Long empId, String payYearMonth, com.peoplecore.pay.enums.DepStatus depStatus);

    Optional<RetirementPensionDeposits> findByDepIdAndCompany_CompanyId(Long depId, UUID companyId);
}
```

**`hr-service/.../pay/repository/PensionDepositQueryRepository.java`** (QueryDSL 인터페이스)
```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.dtos.PensionDepositRes;
import com.peoplecore.pay.dtos.MonthlyDepositSummaryDto;
import com.peoplecore.pay.enums.DepStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PensionDepositQueryRepository {

    Page<PensionDepositRes> search(UUID companyId, String fromYm, String toYm,
                                    Long empId, Long deptId, DepStatus status,
                                    Pageable pageable);

    Long sumDepositAmount(UUID companyId, String fromYm, String toYm, DepStatus status);

    Integer countDistinctEmployees(UUID companyId, String fromYm, String toYm, DepStatus status);

    Long grandTotalDeposited(UUID companyId);

    List<PensionDepositRes> findByEmpId(UUID companyId, Long empId, String fromYm, String toYm);

    List<MonthlyDepositSummaryDto> monthlySummary(UUID companyId, Integer year);
}
```

**`hr-service/.../pay/repository/PensionDepositQueryRepositoryImpl.java`**
```java
package com.peoplecore.pay.repository;

import com.peoplecore.employee.domain.QEmployee;
import com.peoplecore.pay.domain.QRetirementPensionDeposits;
import com.peoplecore.pay.dtos.MonthlyDepositSummaryDto;
import com.peoplecore.pay.dtos.PensionDepositRes;
import com.peoplecore.pay.enums.DepStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PensionDepositQueryRepositoryImpl implements PensionDepositQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final QRetirementPensionDeposits rpd = QRetirementPensionDeposits.retirementPensionDeposits;
    private final QEmployee qEmp = QEmployee.employee;

    @Override
    public Page<PensionDepositRes> search(UUID companyId, String fromYm, String toYm,
                                           Long empId, Long deptId, DepStatus status,
                                           Pageable pageable) {
        BooleanBuilder where = buildWhere(companyId, fromYm, toYm, empId, status);
        if (deptId != null) where.and(qEmp.dept.deptId.eq(deptId));

        List<PensionDepositRes> content = queryFactory
                .select(Projections.constructor(PensionDepositRes.class,
                        rpd.depId,
                        rpd.employee.empId,                    // ✅ ManyToOne 경로
                        rpd.employee.empName,                  // 조인으로 바로 가져옴
                        rpd.employee.dept.deptName,
                        rpd.payYearMonth, rpd.baseAmount, rpd.depositAmount,
                        rpd.depStatus.stringValue(), rpd.depositDate,
                        rpd.payrollRun.payrollRunId,           // ✅ ManyToOne 경로
                        rpd.isManual
                ))
                .from(rpd)
                .join(rpd.employee, qEmp)                       // 조인
                .where(where)
                .orderBy(rpd.depositDate.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(rpd.count())
                .from(rpd)
                .join(rpd.employee, qEmp)
                .where(where)
                .fetchOne();
        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    @Override
    public Long sumDepositAmount(UUID companyId, String fromYm, String toYm, DepStatus status) {
        Long result = queryFactory
                .select(rpd.depositAmount.sum())
                .from(rpd)
                .where(buildWhere(companyId, fromYm, toYm, null, status))
                .fetchOne();
        return result != null ? result : 0L;
    }

    @Override
    public Integer countDistinctEmployees(UUID companyId, String fromYm, String toYm, DepStatus status) {
        Long count = queryFactory
                .select(rpd.employee.empId.countDistinct())      // ✅
                .from(rpd)
                .where(buildWhere(companyId, fromYm, toYm, null, status))
                .fetchOne();
        return count != null ? count.intValue() : 0;
    }

    @Override
    public Long grandTotalDeposited(UUID companyId) {
        Long result = queryFactory
                .select(rpd.depositAmount.sum())
                .from(rpd)
                .where(rpd.company.companyId.eq(companyId)
                        .and(rpd.depStatus.eq(DepStatus.COMPLETED)))
                .fetchOne();
        return result != null ? result : 0L;
    }

    @Override
    public List<PensionDepositRes> findByEmpId(UUID companyId, Long empId, String fromYm, String toYm) {
        return queryFactory
                .select(Projections.constructor(PensionDepositRes.class,
                        rpd.depId,
                        rpd.employee.empId,
                        rpd.employee.empName,
                        rpd.employee.dept.deptName,
                        rpd.payYearMonth, rpd.baseAmount, rpd.depositAmount,
                        rpd.depStatus.stringValue(), rpd.depositDate,
                        rpd.payrollRun.payrollRunId,
                        rpd.isManual
                ))
                .from(rpd)
                .join(rpd.employee, qEmp)
                .where(buildWhere(companyId, fromYm, toYm, empId, null))
                .orderBy(rpd.payYearMonth.desc())
                .fetch();
    }

    @Override
    public List<MonthlyDepositSummaryDto> monthlySummary(UUID companyId, Integer year) {
        return queryFactory
                .select(Projections.constructor(MonthlyDepositSummaryDto.class,
                        rpd.payYearMonth,
                        rpd.employee.empId.countDistinct().intValue(),   // ✅
                        rpd.depositAmount.sum()
                ))
                .from(rpd)
                .where(
                        rpd.company.companyId.eq(companyId),
                        rpd.depStatus.eq(DepStatus.COMPLETED),
                        rpd.payYearMonth.startsWith(String.valueOf(year))
                )
                .groupBy(rpd.payYearMonth)
                .orderBy(rpd.payYearMonth.asc())
                .fetch();
    }

    private BooleanBuilder buildWhere(UUID companyId, String fromYm, String toYm,
                                       Long empId, DepStatus status) {
        BooleanBuilder b = new BooleanBuilder();
        b.and(rpd.company.companyId.eq(companyId));
        if (fromYm != null && !fromYm.isBlank()) b.and(rpd.payYearMonth.goe(fromYm));
        if (toYm != null && !toYm.isBlank()) b.and(rpd.payYearMonth.loe(toYm));
        if (empId != null) b.and(rpd.employee.empId.eq(empId));       // ✅
        if (status != null) b.and(rpd.depStatus.eq(status));
        return b;
    }
}
```

> `@ManyToOne(fetch = LAZY)` 덕분에 `rpd.employee.empName` 접근 시 자동 조인 생성되지만, **명시적 `.join(rpd.employee, qEmp)` 을 쓰는 게 실행계획 최적화/가독성에 유리**. 여러 필드를 참조할 때 조인이 중복 생성되는 것도 방지됨.
> .join(emp).on(emp.empId.eq(rpd.empId))
> // ...
> ```
> 실제 구현 시 `Employee`와의 조인을 추가해서 `empName`·`deptName`을 가져와야 한다.

---

### 12-3. DTO

**`pay/dtos/PensionDepositRes.java`**
```java
package com.peoplecore.pay.dtos;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PensionDepositRes {
    private Long depId;
    private Long empId;
    private String empName;
    private String deptName;
    private String payYearMonth;
    private Long baseAmount;
    private Long depositAmount;
    private String depStatus;
    private LocalDateTime depositDate;
    private Long payrollRunId;     // null이면 수동
    private Boolean isManual;
}
```

**`pay/dtos/PensionDepositSummaryResDto.java`**
```java
package com.peoplecore.pay.dtos;

import lombok.*;
import org.springframework.data.domain.Page;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PensionDepositSummaryResDto {
    private Integer totalEmployees;
    private Long totalDepositAmount;
    private Long monthlyAverage;
    private Long grandTotalDeposited;
    private Page<PensionDepositRes> deposits;
}
```

**`pay/dtos/PensionDepositEmployeeResDto.java`**
```java
package com.peoplecore.pay.dtos;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PensionDepositEmployeeResDto {
    private Long empId;
    private String empName;
    private String deptName;
    private String retirementType;
    private Long totalDeposited;
    private List<PensionDepositRes> deposits;
}
```

**`pay/dtos/MonthlyDepositSummaryDto.java`**
```java
package com.peoplecore.pay.dtos;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyDepositSummaryDto {
    private String yearMonth;
    private Integer count;
    private Long totalAmount;
}
```

**`pay/dtos/PensionDepositCreateReqDto.java`**
```java
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.enums.DepStatus;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PensionDepositCreateReqDto {

    @NotNull(message = "사원 ID는 필수입니다")
    private Long empId;

    @NotBlank(message = "적립 기준월은 필수입니다")
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "YYYY-MM 형식이어야 합니다")
    private String payYearMonth;

    @NotNull @Positive(message = "기준임금은 0보다 커야 합니다")
    private Long baseAmount;

    @NotNull @Positive(message = "적립금액은 0보다 커야 합니다")
    private Long depositAmount;

    @NotNull(message = "상태는 필수입니다")
    private DepStatus depStatus;

    @NotBlank(message = "사유는 필수입니다")
    private String reason;
}
```

---

### 12-4. Service

**`pay/service/PensionDepositService.java`**
```java
package com.peoplecore.pay.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.RetirementPensionDeposits;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.enums.DepStatus;
import com.peoplecore.pay.enums.RetirementType;
import com.peoplecore.pay.repository.RetirementPensionDepositsRepository;
import com.peoplecore.pay.repository.SeverancePaysRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PensionDepositService {

    private final RetirementPensionDepositsRepository repository;
    private final EmployeeRepository employeeRepository;
    private final SeverancePaysRepository severancePaysRepository;
    private final com.peoplecore.company.repository.CompanyRepository companyRepository;

    // ── 1. 목록 조회 ──
    public PensionDepositSummaryResDto getDepositList(
            UUID companyId, String fromYm, String toYm,
            Long empId, Long deptId, DepStatus status, Pageable pageable) {

        Page<PensionDepositRes> deposits = repository.search(companyId, fromYm, toYm, empId, deptId, status, pageable);

        Integer totalEmployees = repository.countDistinctEmployees(companyId, fromYm, toYm, status);
        Long totalDepositAmount = repository.sumDepositAmount(companyId, fromYm, toYm, status);
        Long grandTotalDeposited = repository.grandTotalDeposited(companyId);

        // 월 수 계산
        long months = calcMonthsBetween(fromYm, toYm);
        Long monthlyAverage = months > 0 ? totalDepositAmount / months : 0L;

        return PensionDepositSummaryResDto.builder()
                .totalEmployees(totalEmployees)
                .totalDepositAmount(totalDepositAmount)
                .monthlyAverage(monthlyAverage)
                .grandTotalDeposited(grandTotalDeposited)
                .deposits(deposits)
                .build();
    }

    // ── 2. 사원별 이력 ──
    public PensionDepositEmployeeResDto getEmployeeDeposits(
            UUID companyId, Long empId, String fromYm, String toYm) {

        Employee emp = employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        List<PensionDepositRes> deposits = repository.findByEmpId(companyId, empId, fromYm, toYm);
        Long totalDeposited = severancePaysRepository.sumDcDepositedTotal(empId, companyId);

        // 퇴직금 추계 · DC 차액 등은 이 엔드포인트에서 산정하지 않음.
        // 프론트에서 필요 시 퇴직금추계액 화면(GET /pay/admin/severance/estimate)으로 이동.

        return PensionDepositEmployeeResDto.builder()
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .retirementType(emp.getRetirementType() != null ? emp.getRetirementType().name() : null)
                .totalDeposited(totalDeposited)
                .deposits(deposits)
                .build();
    }

    // ── 3. 수동 적립 등록 ──
    @Transactional
    public PensionDepositRes createManualDeposit(UUID companyId, Long adminEmpId, PensionDepositCreateReqDto req) {
        Employee emp = employeeRepository.findByEmpIdAndCompany_CompanyId(req.getEmpId(), companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // DC형 검증
        if (emp.getRetirementType() != RetirementType.DC) {
            throw new CustomException(ErrorCode.EMPLOYEE_NOT_DC);
        }

        // 중복 체크: 같은 사원·같은 월·COMPLETED 상태 이미 있으면 거부
        if (repository.existsByEmployee_EmpIdAndPayYearMonthAndDepStatus(
                req.getEmpId(), req.getPayYearMonth(), DepStatus.COMPLETED)) {
            throw new CustomException(ErrorCode.DEPOSIT_ALREADY_EXISTS);
        }

        RetirementPensionDeposits deposit = RetirementPensionDeposits.builder()
                .employee(emp)                       // ✅ Employee 객체 전달
                .baseAmount(req.getBaseAmount())
                .depositAmount(req.getDepositAmount())
                .payYearMonth(req.getPayYearMonth())
                .depositDate(LocalDateTime.now())
                .depStatus(req.getDepStatus())
                .company(companyRepository.getReferenceById(companyId))
                .payrollRun(null)                    // ✅ 수동 적립은 null
                .isManual(true)
                .reason(req.getReason())
                .createdBy(adminEmpId)
                .build();

        RetirementPensionDeposits saved = repository.save(deposit);

        log.info("[PensionDeposit 수동등록] depId={}, empId={}, payYearMonth={}, amount={}, by={}",
                saved.getDepId(), req.getEmpId(), req.getPayYearMonth(), req.getDepositAmount(), adminEmpId);

        return toRes(saved, emp);
    }

    // ── 4. 적립 취소 ──
    @Transactional
    public void cancelDeposit(UUID companyId, Long adminEmpId, Long depId, String reason) {
        RetirementPensionDeposits deposit = repository.findByDepIdAndCompany_CompanyId(depId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEPOSIT_NOT_FOUND));

        if (deposit.getDepStatus() == DepStatus.CANCELED) {
            throw new CustomException(ErrorCode.DEPOSIT_ALREADY_CANCELED);
        }

        deposit.cancel(adminEmpId, reason);

        log.warn("[PensionDeposit 취소] depId={}, empId={}, by={}, reason={}",
                depId, deposit.getEmployee().getEmpId(), adminEmpId, reason);   // ✅
    }

    // ── 5. 월별 요약 ──
    public List<MonthlyDepositSummaryDto> getMonthlySummary(UUID companyId, Integer year) {
        int targetYear = year != null ? year : java.time.LocalDate.now().getYear();
        return repository.monthlySummary(companyId, targetYear);
    }

    // ── 유틸 ──
    private long calcMonthsBetween(String fromYm, String toYm) {
        if (fromYm == null || toYm == null) return 1;
        java.time.YearMonth from = java.time.YearMonth.parse(fromYm);
        java.time.YearMonth to = java.time.YearMonth.parse(toYm);
        return java.time.temporal.ChronoUnit.MONTHS.between(from, to) + 1;
    }

    private PensionDepositRes toRes(RetirementPensionDeposits d, Employee emp) {
        return PensionDepositRes.builder()
                .depId(d.getDepId())
                .empId(d.getEmployee().getEmpId())                                            // ✅
                .empName(emp.getEmpName())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .payYearMonth(d.getPayYearMonth())
                .baseAmount(d.getBaseAmount())
                .depositAmount(d.getDepositAmount())
                .depStatus(d.getDepStatus().name())
                .depositDate(d.getDepositDate())
                .payrollRunId(d.getPayrollRun() != null ? d.getPayrollRun().getPayrollRunId() : null)   // ✅
                .isManual(d.getIsManual())
                .build();
    }

    // ── 6. 사원별 집계 조회 ──
    public PensionDepositByEmployeeSummaryResDto getDepositByEmployee(
            UUID companyId, String fromYm, String toYm,
            String search, Long deptId, DepStatus status) {

        List<PensionDepositByEmployeeRes> rows = repository.searchByEmployee(companyId, fromYm, toYm, search, deptId, status);

        // 요약 카드 (기존 목록 API와 동일)
        Integer totalEmployees = rows.size();
        Long totalDepositAmount = rows.stream().mapToLong(PensionDepositByEmployeeRes::getTotalAmount).sum();
        Long grandTotalDeposited = repository.grandTotalDeposited(companyId);
        long months = calcMonthsBetween(fromYm, toYm);
        Long monthlyAverage = months > 0 ? totalDepositAmount / months : 0L;

        return PensionDepositByEmployeeSummaryResDto.builder()
                .totalEmployees(totalEmployees)
                .totalDepositAmount(totalDepositAmount)
                .monthlyAverage(monthlyAverage)
                .grandTotalDeposited(grandTotalDeposited)
                .employees(rows)
                .build();
    }
}
```

---

### 12-4b. 사원별 집계 DTO + Repository 메서드 추가

**`pay/dtos/PensionDepositByEmployeeRes.java`** (신규)
```java
package com.peoplecore.pay.dtos;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PensionDepositByEmployeeRes {
    private Long empId;
    private String empName;
    private String deptName;

    private Integer monthCount;           // 기간 내 COMPLETED 건수
    private Long totalAmount;             // 기간 내 적립 합계
    private LocalDateTime lastDepositDate;
    private Boolean hasManual;
    private Boolean hasCanceled;

    // QueryDSL Projections.constructor 호환용 풀-아규먼트 생성자는 @AllArgsConstructor가 제공
}
```

**`pay/dtos/PensionDepositByEmployeeSummaryResDto.java`** (신규)
```java
package com.peoplecore.pay.dtos;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PensionDepositByEmployeeSummaryResDto {
    private Integer totalEmployees;
    private Long totalDepositAmount;
    private Long monthlyAverage;
    private Long grandTotalDeposited;
    private List<PensionDepositByEmployeeRes> employees;
}
```

**`PensionDepositQueryRepository`** (인터페이스에 메서드 추가)
```java
List<PensionDepositByEmployeeRes> searchByEmployee(
        UUID companyId, String fromYm, String toYm,
        String search, Long deptId, DepStatus status);
```

**`PensionDepositQueryRepositoryImpl`** (Impl에 구현 추가)
```java
@Override
public List<PensionDepositByEmployeeRes> searchByEmployee(
        UUID companyId, String fromYm, String toYm,
        String search, Long deptId, DepStatus status) {

    // status == COMPLETED만 집계 (취소 포함 여부는 hasCanceled로 별도 표기)
    // monthCount, totalAmount는 COMPLETED만 집계됨
    NumberExpression<Integer> monthCountExpr =
            new CaseBuilder()
                    .when(rpd.depStatus.eq(DepStatus.COMPLETED)).then(1)
                    .otherwise(0)
                    .sum();

    NumberExpression<Long> totalAmountExpr =
            new CaseBuilder()
                    .when(rpd.depStatus.eq(DepStatus.COMPLETED))
                    .then(rpd.depositAmount)
                    .otherwise(0L)
                    .sum();

    BooleanExpression hasManualExpr = rpd.isManual.isTrue().sum().gt(0);  // 대안: rpd.isManual.max()
    BooleanExpression hasCanceledExpr = rpd.depStatus.eq(DepStatus.CANCELED).sum().gt(0);

    BooleanBuilder where = new BooleanBuilder();
    where.and(rpd.company.companyId.eq(companyId));
    if (fromYm != null && !fromYm.isBlank()) where.and(rpd.payYearMonth.goe(fromYm));
    if (toYm != null && !toYm.isBlank()) where.and(rpd.payYearMonth.loe(toYm));
    if (search != null && !search.isBlank()) where.and(rpd.employee.empName.containsIgnoreCase(search));
    if (deptId != null) where.and(qEmp.dept.deptId.eq(deptId));
    // status 필터는 상태별 뷰가 필요할 때만 적용. 기본은 "전체를 집계해서 보여주되 상태 구성만 표시"
    if (status != null) where.and(rpd.depStatus.eq(status));

    return queryFactory
            .select(Projections.constructor(PensionDepositByEmployeeRes.class,
                    rpd.employee.empId,
                    rpd.employee.empName,
                    rpd.employee.dept.deptName,
                    monthCountExpr.intValue(),
                    totalAmountExpr,
                    rpd.depositDate.max(),
                    // boolean 집계 표현
                    new CaseBuilder()
                            .when(rpd.isManual.isTrue().sum().gt(0)).then(true)
                            .otherwise(false),
                    new CaseBuilder()
                            .when(rpd.depStatus.eq(DepStatus.CANCELED).sum().gt(0)).then(true)
                            .otherwise(false)
            ))
            .from(rpd)
            .join(rpd.employee, qEmp)
            .where(where)
            .groupBy(rpd.employee.empId, rpd.employee.empName, rpd.employee.dept.deptName)
            .orderBy(rpd.employee.empName.asc())
            .fetch();
}
```

> **`hasManual` / `hasCanceled` 집계 주의**: QueryDSL에서 boolean 직접 집계가 어려우므로, Service에서 집계 결과 후처리하는 게 더 깔끔할 수 있음. 위 코드는 CaseBuilder로 한 번에 처리한 예시이며, 구현 취향에 따라 다음과 같이 **Service에서 후처리**해도 됨:
>
> ```java
> // Repository는 COMPLETED 집계 + 취소/수동 여부는 별도 쿼리
> List<Long> empIdsWithManual = queryFactory.select(rpd.employee.empId)
>         .from(rpd).where(companyEq, rpd.isManual.isTrue(), monthRange).distinct().fetch();
> List<Long> empIdsWithCanceled = ...
> // Service에서 rows를 돌며 위 리스트 포함 여부로 Boolean 세팅
> ```

---

### 12-5. Controller

**`pay/controller/PensionDepositController.java`**
```java
package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.enums.DepStatus;
import com.peoplecore.pay.service.PensionDepositService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/admin/pension-deposits")
@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
@RequiredArgsConstructor
public class PensionDepositController {

    private final PensionDepositService pensionDepositService;

    // 1. 목록
    @GetMapping
    public ResponseEntity<PensionDepositSummaryResDto> getList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false) String fromYm,
            @RequestParam(required = false) String toYm,
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) DepStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                pensionDepositService.getDepositList(companyId, fromYm, toYm, empId, deptId, status, pageable));
    }

    // 2. 사원별 이력
    @GetMapping("/employee/{empId}")
    public ResponseEntity<PensionDepositEmployeeResDto> getEmployeeDeposits(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId,
            @RequestParam(required = false) String fromYm,
            @RequestParam(required = false) String toYm) {
        return ResponseEntity.ok(
                pensionDepositService.getEmployeeDeposits(companyId, empId, fromYm, toYm));
    }

    // 3. 수동 적립 등록
    @PostMapping
    public ResponseEntity<PensionDepositRes> createManual(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long adminEmpId,
            @RequestBody @Valid PensionDepositCreateReqDto reqDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                pensionDepositService.createManualDeposit(companyId, adminEmpId, reqDto));
    }

    // 4. 적립 취소
    @DeleteMapping("/{depId}")
    public ResponseEntity<Void> cancel(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long adminEmpId,
            @PathVariable Long depId,
            @RequestParam(required = false) String reason) {
        pensionDepositService.cancelDeposit(companyId, adminEmpId, depId, reason);
        return ResponseEntity.noContent().build();
    }

    // 5. 월별 요약
    @GetMapping("/monthly-summary")
    public ResponseEntity<List<MonthlyDepositSummaryDto>> monthlySummary(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(pensionDepositService.getMonthlySummary(companyId, year));
    }

    // 6. 사원별 집계 (화면 메인 테이블용)
    @GetMapping("/by-employee")
    public ResponseEntity<PensionDepositByEmployeeSummaryResDto> getByEmployee(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false) String fromYm,
            @RequestParam(required = false) String toYm,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) DepStatus status) {
        return ResponseEntity.ok(
                pensionDepositService.getDepositByEmployee(companyId, fromYm, toYm, search, deptId, status));
    }
}
```

---

### 12-6. ErrorCode 추가

**`common/.../exception/ErrorCode.java`** (기존 enum에 추가)
```java
EMPLOYEE_NOT_DC(400, "DC형 사원만 수동 적립 등록이 가능합니다."),
DEPOSIT_ALREADY_EXISTS(409, "동일 사원·동일 월에 이미 적립된 건이 있습니다."),
DEPOSIT_NOT_FOUND(404, "적립 내역을 찾을 수 없습니다."),
DEPOSIT_ALREADY_CANCELED(400, "이미 취소된 적립입니다."),
```

---

### 12-7. 자동 적립(복습)

지급처리 시점의 자동 적립은 `PayrollService.processPayment()`에 이미 있다고 가정. 기존 `createDcDeposits()` 메서드를 **`@ManyToOne` 필드**에 맞춰 수정:

```java
// PayrollService.createDcDeposits() 안에서 build 시
// emp: 이미 조회된 Employee 인스턴스 (for loop 변수)
// run: PayrollRuns 인스턴스

RetirementPensionDeposits deposit = RetirementPensionDeposits.builder()
        .employee(emp)                          // ✅ Employee 객체 전달
        .baseAmount(baseAmount)
        .depositAmount(depositAmount)
        .payYearMonth(run.getPayYearMonth())    // 필수
        .depositDate(LocalDateTime.now())
        .depStatus(DepStatus.COMPLETED)
        .company(company)
        .payrollRun(run)                        // ✅ PayrollRuns 객체 전달 (자동은 반드시 non-null)
        .isManual(false)                        // 자동 표식
        .build();
```

> **중복 체크**도 메서드 이름이 바뀜:
> ```java
> // Before
> depositRepository.existsByPayrollRunIdAndEmpId(run.getPayrollRunId(), empId)
> // After
> depositRepository.existsByPayrollRun_PayrollRunIdAndEmployee_EmpId(run.getPayrollRunId(), empId)
> ```

---

### 12-8. 기존 `SeverancePaysRepository` 영향 총정리

`RetirementPensionDeposits`에서 `empId` → `employee`(ManyToOne)로 바뀌면서, **`RetirementPensionDeposits`를 참조하는 기존 쿼리만 경로 변경이 필요**합니다. `PayrollDetails` 기반 메서드(`sumLast3MonthPay`, `sumLastYearBonus`, `sumOrdinaryMonthlyPay`)는 변경 없음.

#### 영향 받는 메서드: `sumDcDepositedTotal` 단 1개

**`SeverancePaysRepositoryCustom.java`** (인터페이스 — 변경 없음)
```java
public interface SeverancePaysRepositoryCustom {
    Long sumLast3MonthPay(Long empId, UUID companyId, List<String> months);    // 변경 없음
    Long sumLastYearBonus(Long empId, UUID companyId, List<String> months);    // 변경 없음
    Long sumDcDepositedTotal(Long empId, UUID companyId);                      // 변경 없음 (시그니처)
    Long sumOrdinaryMonthlyPay(Long empId, UUID companyId);                    // 변경 없음
}
```

**`SeverancePaysRepositoryImpl.java`** — `sumDcDepositedTotal`만 내부 경로 변경

```java
@Repository
public class SeverancePaysRepositoryImpl implements SeverancePaysRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QPayrollDetails pd = QPayrollDetails.payrollDetails;
    private final QPayrollRuns pr = QPayrollRuns.payrollRuns;
    private final QLeaveAllowance la = QLeaveAllowance.leaveAllowance;
    private final QRetirementPensionDeposits rpd = QRetirementPensionDeposits.retirementPensionDeposits;

    @Autowired
    public SeverancePaysRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    // ── 변경 없음: 최근 3개월 급여 총액 ──
    @Override
    public Long sumLast3MonthPay(Long empId, UUID companyId, List<String> months) {
        Long result = queryFactory
                .select(pd.amount.sum())
                .from(pd)
                .join(pd.payrollRuns, pr)
                .where(
                        pd.employee.empId.eq(empId),
                        pd.company.companyId.eq(companyId),
                        pr.payYearMonth.in(months),
                        pd.payItemType.eq(PayItemType.PAYMENT)
                )
                .fetchOne();
        return result != null ? result : 0L;
    }

    // ── 변경 없음: 직전 1년 상여금 총액 ──
    @Override
    public Long sumLastYearBonus(Long empId, UUID companyId, List<String> months) {
        Long result = queryFactory
                .select(pd.amount.sum())
                .from(pd)
                .join(pd.payrollRuns, pr)
                .where(
                        pd.employee.empId.eq(empId),
                        pd.company.companyId.eq(companyId),
                        pr.payYearMonth.in(months),
                        pd.payItemType.eq(PayItemType.PAYMENT),
                        pd.payItems.payItemCategory.eq(PayItemCategory.BONUS)
                )
                .fetchOne();
        return result != null ? result : 0L;
    }

    // ── 변경 없음: 통상임금(월) ──
    @Override
    public Long sumOrdinaryMonthlyPay(Long empId, UUID companyId){
        String latestMonth = queryFactory
                .select(pr.payYearMonth.max())
                .from(pr)
                .where(pr.company.companyId.eq(companyId),
                        pr.payrollStatus.eq(PayrollStatus.CONFIRMED))
                .fetchOne();
        if (latestMonth == null) return 0L;

        Long result = queryFactory
                .select(pd.amount.sum())
                .from(pd)
                .join(pd.payrollRuns, pr)
                .join(pd.payItems)
                .where(
                        pd.employee.empId.eq(empId),
                        pd.company.companyId.eq(companyId),
                        pr.payYearMonth.eq(latestMonth),
                        pd.payItemType.eq(PayItemType.PAYMENT),
                        pd.payItems.isFixed.isTrue(),
                        pd.payItems.payItemCategory.in(
                                PayItemCategory.SALARY,
                                PayItemCategory.ALLOWANCE
                        )
                )
                .fetchOne();
        return result != null ? result : 0L;
    }

    // ✅ 경로 변경: rpd.empId → rpd.employee.empId
    @Override
    public Long sumDcDepositedTotal(Long empId, UUID companyId) {
        Long result = queryFactory
                .select(rpd.depositAmount.sum())
                .from(rpd)
                .where(
                        rpd.employee.empId.eq(empId),           // ⚠️ 변경 지점
                        rpd.company.companyId.eq(companyId),
                        rpd.depStatus.eq(DepStatus.COMPLETED)
                )
                .fetchOne();
        return result != null ? result : 0L;
    }
}
```

#### (선택) 일괄 버전 추가

`SeveranceEstimateService`에서 N+1 방지용으로 이미 제안했던 메서드들. `rpd` 참조만 `@ManyToOne` 경로로 맞춰서 추가하면 됩니다.

```java
// SeverancePaysRepositoryCustom.java 에 추가 (선택)
Map<Long, Long> sumLast3MonthPayByEmpIds(UUID companyId, List<Long> empIds, List<String> months);
Map<Long, Long> sumLastYearBonusByEmpIds(UUID companyId, List<Long> empIds, List<String> months);
Map<Long, Long> sumDcDepositedTotalByEmpIds(UUID companyId, List<Long> empIds);
```

```java
// SeverancePaysRepositoryImpl.java 구현부 (선택)
@Override
public Map<Long, Long> sumDcDepositedTotalByEmpIds(UUID companyId, List<Long> empIds) {
    List<Tuple> rows = queryFactory
            .select(rpd.employee.empId, rpd.depositAmount.sum())   // ✅ 경로
            .from(rpd)
            .where(
                    rpd.employee.empId.in(empIds),                  // ✅ 경로
                    rpd.company.companyId.eq(companyId),
                    rpd.depStatus.eq(DepStatus.COMPLETED)
            )
            .groupBy(rpd.employee.empId)                            // ✅ 경로
            .fetch();

    return rows.stream().collect(java.util.stream.Collectors.toMap(
            t -> t.get(rpd.employee.empId),
            t -> {
                Long v = t.get(rpd.depositAmount.sum());
                return v != null ? v : 0L;
            }
    ));
}

// sumLast3MonthPayByEmpIds / sumLastYearBonusByEmpIds 는 pd 기반이라 기존 쿼리와 동일
// (사원 단건 → IN 절로 바꾸고 GROUP BY empId 추가만 하면 됨, pd.employee.empId 그대로)
```

### 체크리스트 (백엔드 구현 순서)

- [ ] `RetirementPensionDeposits` 엔티티 `@ManyToOne` 전환 (Employee, PayrollRuns) + 필드 확장
- [ ] DB 마이그레이션 SQL 실행 (컬럼 추가 + payroll_run_id NULL 허용)
- [ ] `PensionDepositQueryRepository` 인터페이스 + Impl (Employee 조인 포함)
- [ ] `RetirementPensionDepositsRepository` 인터페이스 (Custom 상속, 메서드명 `Employee_EmpId` / `PayrollRun_PayrollRunId` 경로로)
- [ ] DTO 7종 생성 (`PensionDepositRes`, `PensionDepositSummaryResDto`, `PensionDepositEmployeeResDto`, `MonthlyDepositSummaryDto`, `PensionDepositCreateReqDto`, **`PensionDepositByEmployeeRes`**, **`PensionDepositByEmployeeSummaryResDto`**)
- [ ] `PensionDepositService` 생성 (엔티티는 `employee()`, `payrollRun()` 빌더)
- [ ] `PensionDepositController` 생성
- [ ] `ErrorCode` enum에 4개 추가
- [ ] `PayrollService.createDcDeposits()` 에서 `.employee(emp)`, `.payrollRun(run)`, `payYearMonth`, `isManual` 세팅 확인
- [ ] 기존 `SeverancePaysRepositoryImpl.sumDcDepositedTotal()` 등 `rpd.empId` → `rpd.employee.empId` 로 교체
- [ ] 빌드 + 통합 테스트
- [ ] 프론트 [PensionDeposits.tsx](src/pages/payroll/PensionDeposits.tsx)에서 MOCK 제거 후 실제 API로 교체
