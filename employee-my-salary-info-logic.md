# 내 급여 조회 로직 명세 — 전직원 급여 탭

> **프로젝트**: PeopleCore HR
> **모듈**: mysalary-crud
> **대상 사용자**: 전 사원 (권한 제한 없음, 본인 정보만 조회)
> **관련 화면**: 급여 탭 > 내 급여 정보
> **작성일**: 2026-04-17

---

## 1. 개요

전 사원이 본인의 급여·계좌·명세서·퇴직연금 정보를 조회할 수 있는 API. 권한 애너테이션(@RoleRequired)은 적용하지 않으며, `X-User-Id` 헤더로 요청자 본인을 식별한다.

- 컨트롤러: `MySalaryController` (`/pay/my`)
- 서비스: `MySalaryService`
- 캐시: Redis (`MySalaryCacheService`) — 동일 사원 정보 반복 조회 최소화
- 쿼리: JPA Repository + QueryDSL (`MySalaryQueryRepository`)

---

## 2. API 스펙 — 내 급여 정보

| 항목 | 값 |
|---|---|
| Method | GET |
| Path | `/pay/my/info` |
| Header | `X-User-Company: {UUID}`, `X-User-Id: {empId}` |
| 응답 | `MySalaryInfoResDto` |

### 응답 필드 구성
- **사원 기본 정보**: empId, empName, empEmail, empNum, empPhone, empType, empHireDate, deptName, gradeName, titleName, profileImageUrl
- **급여 상세**: `annualSalary` (연봉), `monthlySalary` (월급), `fixedAllowances[]` (고정수당 목록)
- **계좌 정보**: `salaryAccount` (급여 계좌), `retirementAccount` (퇴직연금 계좌)

---

## 3. 처리 절차 (MySalaryService.getMySalaryInfo)

1. **캐시 확인**
   - `cacheService.getSalaryInfoCache(companyId, empId, MySalaryInfoResDto.class)`
   - Hit 시 즉시 반환 → DB 부하 감소
2. **사원 조회**
   - `employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId)`
   - 없으면 `EMPLOYEE_NOT_FOUND`
3. **급여 정보 조립 (`buildSalaryInfo`)**
   - 최신 연봉계약 조회: `salaryContractRepository.findByCompanyIdAndEmployee_EmpIdAndDeletedAtIsNullOrderByContractYearDesc(...)`
   - 계약 존재 시:
     - `annualSalary = 최신 계약의 totalAmount`
     - `monthlySalary = annualSalary / 12` (FLOOR)
     - `fixedAllowances = extractAllowances(계약 Detail, 0, companyId)` — 재귀 방식
   - 계약 없을 경우 `0 / 0 / 빈 리스트` 반환
4. **고정수당 필터 (`extractAllowances`)**
   - 각 `SalaryContractDetail.payItemId` 로 `PayItems` 조회
   - 조건: `payItemType == PAYMENT` AND `payItemCategory != SALARY`
   - 위 조건 만족 시 `FixedAllowanceDto(payItemId, payItemName, amount)` 로 매핑
   - 재귀 호출(`extractAllowances(details, index+1, ...)`)로 전체 리스트 구성
5. **급여 계좌 조회**
   - `empAccountsRepository.findByEmployee_EmpIdAndCompany_CompanyId` → `AccountDto`
   - 없으면 `null`
6. **퇴직연금 계좌 조회**
   - `empRetirementAccountRepository.findByEmpIdAndCompany_CompanyId` → `RetirementAccountDto`
   - 필드: retirementAccountId, retirementType, pensionProvider, accountNumber
7. **DTO 빌드 & 캐시 저장**
   - `MySalaryInfoResDto.builder()...build()`
   - `cacheService.cacheSalaryInfo(companyId, empId, result)`

---

## 4. 부가 API — 급여 탭에서 함께 사용하는 기능

| Method | Path | 서비스 메서드 | 설명 |
|---|---|---|---|
| GET | `/pay/my/stubs?year=2026` | `getPayStubList` | 연도별 급(상)여명세서 목록 (Redis 캐시) |
| GET | `/pay/my/stubs/{stubId}` | `getPayStubDetail` | 급여명세서 상세 (지급/공제 항목 분류) |
| GET | `/pay/my/pension` | `getPensionInfo` | DB/DC 퇴직연금 적립금 조회 |
| PUT | `/pay/my/account` | `updateSalaryAccount` | 급여 계좌 변경 (캐시 무효화) |

### 4-1. 급여명세서 목록
- `PayStubs` 테이블에서 `payYearMonth` 가 "YYYY-MM" 형식이므로 `startsWith(year)` 로 검색
- `findByEmpIdAndCompany_CompanyIdAndPayYearMonthStartingWithOrderByPayYearMonthDesc`
- 재귀 함수 `mapStubsToDto`로 엔티티 리스트 → DTO 리스트 변환

### 4-2. 급여명세서 상세
- `PayStubs` + `PayrollDetails` + `PayItems` JOIN (QueryDSL: `findPayStubItems`)
- 항목을 재귀 필터(`filterByType`)로 `PAYMENT` / `DEDUCTION` 두 리스트로 분류
- 응답에 사원/부서명 스냅샷 및 PDF URL 포함

### 4-3. 퇴직연금 적립금 조회
- `Employee.retirementType` 기준 제공 (없으면 `"severance"`)
- DC형이면: `monthlyDeposit = annualSalary / 12`
- 누적 적립금: `RetirementPensionDeposits` 에서 `status=COMPLETED` 합산
- 최근 적립일: `findTopBy...OrderByDepositDateDesc`

### 4-4. 급여 계좌 변경
- 기존 EmpAccounts에 update 메서드가 없어 builder로 새 엔티티를 구성해 `save` 호출
- 변경 후 `cacheService.evictSalaryInfoCache(companyId, empId)` 로 캐시 무효화
- 권한: 본인 empId 기준 → X-User-Id 헤더로 자동 필터링

---

## 5. 캐싱 전략

| 대상 | 키 | 적용 API | 무효화 시점 |
|---|---|---|---|
| 급여 정보 | `(companyId, empId)` | `/pay/my/info` | 계좌 변경 시 evict |
| 명세서 리스트 | `(companyId, empId, year)` | `/pay/my/stubs` | 신규 급여확정/지급 완료 이벤트 (외부) |
| 명세서 상세 | `(companyId, empId, stubId)` | `/pay/my/stubs/{id}` | 동일 상기 이벤트 |
| 퇴직연금 정보 | `(companyId, empId)` | `/pay/my/pension` | 적립 완료 이벤트 |

- Redis Miss 시 DB 조회 후 결과를 캐시 저장
- 본인 전용 API이므로 Key에 `empId` 포함

---

## 6. 예외 처리

| ErrorCode | 발생 조건 |
|---|---|
| `EMPLOYEE_NOT_FOUND` | 본인 사원 정보가 없는 경우 |
| `NOT_FOUND` | 명세서 상세 조회 시 stub 없음 |
| `EMP_ACCOUNT_NOT_FOUND` | 계좌 변경 시 기존 계좌 없음 |

---

## 7. 보안 / 접근 제어

- `@RoleRequired` 미부착 → 전 사원 호출 가능
- 모든 쿼리에 `empId` + `companyId` 동반 → **본인·자기 회사 데이터**만 조회 가능
- `X-User-Id` 헤더는 게이트웨이에서 JWT 파싱 후 주입된 값(위조 방지)

---

## 8. 파일 위치 요약

| 유형 | 경로 |
|---|---|
| Controller | `mysalary-crud/controller/MySalaryController.java` |
| Service | `mysalary-crud/service/MySalaryService.java` |
| DTO | `mysalary-crud/dto/MySalaryInfoResDto.java`, `PayStubListResDto.java`, `PayStubDetailResDto.java`, `PayStubItemResDto.java`, `PensionInfoResDto.java`, `AccountUpdateReqDto.java` |
| Cache | `mysalary-crud/cache/MySalaryCacheService` |
| QueryDSL | `mysalary-crud/repository/MySalaryQueryRepository` |

---

## 9. 흐름 요약 다이어그램

```
[사원] 급여 탭 진입
  └─ GET /pay/my/info
        ├─ Redis 캐시 조회 (Hit → 즉시 반환)
        └─ Miss → Employee 조회
              ├─ 최신 SalaryContract → 연봉/월급/고정수당
              ├─ EmpAccounts → 급여 계좌
              └─ EmpRetirementAccount → 퇴직연금 계좌
        └─ MySalaryInfoResDto 빌드 → Redis 저장 → 반환
```

---

## 10. 서비스 구현 전체 코드 (`MySalaryService`)

> 명세서의 5개 API 에 대응하는 서비스 메서드 구현.
> 의존: `EmployeeRepository`, `SalaryContractRepository`, `SalaryContractDetailRepository`,
> `PayItemsRepository`, `EmpAccountsRepository`, `EmpRetirementAccountRepository`,
> `PayStubsRepository` (신규), `MySalaryQueryRepository` (QueryDSL 신규),
> `MySalaryCacheService` (Redis)

### 10-1. 클래스 선언 + 의존 주입

```java
package com.peoplecore.pay.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.service.MySalaryCacheService;
import com.peoplecore.pay.domain.*;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.dtos.MySalaryInfoResDto.*;
import com.peoplecore.pay.enums.DepStatus;
import com.peoplecore.pay.enums.PayItemCategory;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.repository.*;
import com.peoplecore.salarycontract.domain.SalaryContract;
import com.peoplecore.salarycontract.domain.SalaryContractDetail;
import com.peoplecore.salarycontract.repository.SalaryContractDetailRepository;
import com.peoplecore.salarycontract.repository.SalaryContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MySalaryService {

    private final EmployeeRepository employeeRepository;
    private final SalaryContractRepository salaryContractRepository;
    private final SalaryContractDetailRepository salaryContractDetailRepository;
    private final PayItemsRepository payItemsRepository;
    private final EmpAccountsRepository empAccountsRepository;
    private final EmpRetirementAccountRepository empRetirementAccountRepository;
    private final PayStubsRepository payStubsRepository;
    private final RetirementPensionDepositsRepository pensionDepositsRepository;
    private final MySalaryQueryRepository mySalaryQueryRepository;  // QueryDSL
    private final MySalaryCacheService cacheService;
```

### 10-2. `getMySalaryInfo` — 내 급여 정보 조회

```java
    public MySalaryInfoResDto getMySalaryInfo(UUID companyId, Long empId) {
        // 1. 캐시 확인
        MySalaryInfoResDto cached = cacheService.getSalaryInfoCache(
                companyId, empId, MySalaryInfoResDto.class);
        if (cached != null) return cached;

        // 2. 사원 조회
        Employee emp = employeeRepository
                .findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // 3. 급여 정보 조립
        SalaryInfoDto salaryInfo = buildSalaryInfo(companyId, empId);

        // 4. 급여 계좌
        AccountDto salaryAccount = empAccountsRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId)
                .map(this::toAccountDto)
                .orElse(null);

        // 5. 퇴직연금 계좌
        RetirementAccountDto retirementAccount = empRetirementAccountRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId)
                .map(this::toRetirementAccountDto)
                .orElse(null);

        // 6. DTO 빌드
        MySalaryInfoResDto result = MySalaryInfoResDto.builder()
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .empEmail(emp.getEmpEmail())
                .empNum(emp.getEmpNum())
                .empPhone(emp.getEmpPhone())
                .empType(emp.getEmpType() != null ? emp.getEmpType().name() : null)
                .empHireDate(emp.getEmpHireDate())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .gradeName(emp.getGrade() != null ? emp.getGrade().getGradeName() : null)
                .titleName(emp.getTitle() != null ? emp.getTitle().getTitleName() : null)
                .profileImageUrl(emp.getProfileImageUrl())
                .salaryInfo(salaryInfo)
                .salaryAccount(salaryAccount)
                .retirementAccount(retirementAccount)
                .build();

        // 7. 캐시 저장
        cacheService.cacheSalaryInfo(companyId, empId, result);
        return result;
    }

    private SalaryInfoDto buildSalaryInfo(UUID companyId, Long empId) {
        List<SalaryContract> contracts = salaryContractRepository
                .findByCompanyIdAndEmployee_EmpIdAndDeletedAtIsNullOrderByContractYearDesc(
                        companyId, empId);

        if (contracts.isEmpty()) {
            return SalaryInfoDto.builder()
                    .annualSalary(0L).monthlySalary(0L)
                    .fixedAllowances(Collections.emptyList())
                    .build();
        }

        SalaryContract latest = contracts.get(0);
        long annual = latest.getTotalAmount() != null ? latest.getTotalAmount() : 0L;
        long monthly = annual / 12;

        List<SalaryContractDetail> details = salaryContractDetailRepository
                .findByContract_ContractId(latest.getContractId());

        List<FixedAllowanceDto> allowances = extractAllowances(
                details, 0, companyId, new ArrayList<>());

        return SalaryInfoDto.builder()
                .annualSalary(annual)
                .monthlySalary(monthly)
                .fixedAllowances(allowances)
                .build();
    }

    /**
     * SalaryContractDetail 리스트를 재귀로 순회하며
     * payItemType=PAYMENT AND payItemCategory != SALARY 인 항목만 추출.
     */
    private List<FixedAllowanceDto> extractAllowances(
            List<SalaryContractDetail> details, int index,
            UUID companyId, List<FixedAllowanceDto> acc) {
        if (index >= details.size()) return acc;

        SalaryContractDetail detail = details.get(index);
        PayItems payItem = payItemsRepository
                .findByPayItemIdAndCompany_CompanyId(detail.getPayItemId(), companyId)
                .orElse(null);

        if (payItem != null
                && payItem.getPayItemType() == PayItemType.PAYMENT
                && payItem.getPayItemCategory() != PayItemCategory.SALARY) {
            acc.add(FixedAllowanceDto.builder()
                    .payItemId(payItem.getPayItemId())
                    .payItemName(payItem.getPayItemName())
                    .amount(detail.getAmount() != null ? detail.getAmount().longValue() : 0L)
                    .build());
        }
        return extractAllowances(details, index + 1, companyId, acc);
    }

    private AccountDto toAccountDto(EmpAccounts acc) {
        return AccountDto.builder()
                .empAccountId(acc.getEmpAccountId())
                .bankName(acc.getBankName())
                .accountNumber(acc.getAccountNumber())
                .accountHolder(acc.getAccountHolder())
                .build();
    }

    private RetirementAccountDto toRetirementAccountDto(EmpRetirementAccount acc) {
        return RetirementAccountDto.builder()
                .retirementAccountId(acc.getRetirementAccountId())
                .retirementType(acc.getRetirementType() != null ? acc.getRetirementType().name() : null)
                .pensionProvider(acc.getPensionProvider())
                .accountNumber(acc.getAccountNumber())
                .build();
    }
```

### 10-3. `getPayStubList` — 연도별 급여명세서 목록

```java
    public List<PayStubListResDto> getPayStubList(UUID companyId, Long empId, String year) {
        // 1. 캐시
        List<PayStubListResDto> cached = cacheService.getStubListCache(companyId, empId, year);
        if (cached != null) return cached;

        // 2. DB 조회 — payYearMonth 는 "YYYY-MM" 이므로 startsWith(year) 로 필터
        List<PayStubs> stubs = payStubsRepository
                .findByEmpIdAndCompany_CompanyIdAndPayYearMonthStartingWithOrderByPayYearMonthDesc(
                        empId, companyId, year);

        // 3. 재귀 변환
        List<PayStubListResDto> result = mapStubsToDto(stubs, 0, new ArrayList<>());

        // 4. 캐시
        cacheService.cacheStubList(companyId, empId, year, result);
        return result;
    }

    private List<PayStubListResDto> mapStubsToDto(
            List<PayStubs> stubs, int index, List<PayStubListResDto> acc) {
        if (index >= stubs.size()) return acc;
        PayStubs s = stubs.get(index);
        acc.add(PayStubListResDto.builder()
                .stubId(s.getStubId())
                .payYearMonth(s.getPayYearMonth())
                .payDate(s.getPayDate())
                .totalPay(s.getTotalPay())
                .totalDeduction(s.getTotalDeduction())
                .netPay(s.getNetPay())
                .stubType(s.getStubType() != null ? s.getStubType().name() : null)
                .build());
        return mapStubsToDto(stubs, index + 1, acc);
    }
```

### 10-4. `getPayStubDetail` — 급여명세서 상세 (지급/공제 분류)

```java
    public PayStubDetailResDto getPayStubDetail(UUID companyId, Long empId, Long stubId) {
        // 캐시
        PayStubDetailResDto cached = cacheService.getStubDetailCache(companyId, empId, stubId);
        if (cached != null) return cached;

        // 본인 소유 검증
        PayStubs stub = payStubsRepository
                .findByStubIdAndEmpIdAndCompany_CompanyId(stubId, empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        // QueryDSL: PayStubs + PayrollDetails + PayItems 조인 결과
        List<PayStubItemResDto> items = mySalaryQueryRepository.findPayStubItems(stubId);

        // 재귀 분류
        List<PayStubItemResDto> paymentItems = filterByType(
                items, 0, PayItemType.PAYMENT, new ArrayList<>());
        List<PayStubItemResDto> deductionItems = filterByType(
                items, 0, PayItemType.DEDUCTION, new ArrayList<>());

        Employee emp = employeeRepository
                .findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        PayStubDetailResDto result = PayStubDetailResDto.builder()
                .stubId(stub.getStubId())
                .payYearMonth(stub.getPayYearMonth())
                .payDate(stub.getPayDate())
                .empName(emp.getEmpName())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .totalPay(stub.getTotalPay())
                .totalDeduction(stub.getTotalDeduction())
                .netPay(stub.getNetPay())
                .paymentItems(paymentItems)
                .deductionItems(deductionItems)
                .pdfUrl(stub.getPdfUrl())
                .build();

        cacheService.cacheStubDetail(companyId, empId, stubId, result);
        return result;
    }

    /**
     * PayStubItem 리스트에서 특정 PayItemType 만 재귀 필터링.
     */
    private List<PayStubItemResDto> filterByType(
            List<PayStubItemResDto> items, int index,
            PayItemType type, List<PayStubItemResDto> acc) {
        if (index >= items.size()) return acc;
        PayStubItemResDto item = items.get(index);
        if (type.name().equals(item.getPayItemType())) {
            acc.add(item);
        }
        return filterByType(items, index + 1, type, acc);
    }
```

### 10-5. `getPensionInfo` — 퇴직연금 적립금 조회

```java
    public PensionInfoResDto getPensionInfo(UUID companyId, Long empId) {
        // 캐시
        PensionInfoResDto cached = cacheService.getPensionCache(companyId, empId);
        if (cached != null) return cached;

        Employee emp = employeeRepository
                .findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        String retirementType = emp.getRetirementType() != null
                ? emp.getRetirementType().name()
                : "severance";

        // 최신 연봉 기반 월 적립금 (DC 형만 의미 있음)
        long annual = salaryContractRepository
                .findByCompanyIdAndEmployee_EmpIdAndDeletedAtIsNullOrderByContractYearDesc(
                        companyId, empId)
                .stream().findFirst()
                .map(c -> c.getTotalAmount() != null ? c.getTotalAmount() : 0L)
                .orElse(0L);
        long monthlyDeposit = "DC".equals(retirementType) ? annual / 12 : 0L;

        // 누적 적립금 (COMPLETED 합산)
        long totalDeposited = pensionDepositsRepository
                .findByEmployee_EmpIdAndCompany_CompanyIdAndDepStatus(
                        empId, companyId, DepStatus.COMPLETED)
                .stream()
                .mapToLong(d -> d.getDepositAmount() != null ? d.getDepositAmount() : 0L)
                .sum();

        // 최근 적립일
        LocalDateTime lastDepositDate = pensionDepositsRepository
                .findTopByEmployee_EmpIdAndCompany_CompanyIdAndDepStatusOrderByDepositDateDesc(
                        empId, companyId, DepStatus.COMPLETED)
                .map(RetirementPensionDeposits::getDepositDate)
                .orElse(null);

        PensionInfoResDto result = PensionInfoResDto.builder()
                .retirementType(retirementType)
                .monthlyDeposit(monthlyDeposit)
                .totalDeposited(totalDeposited)
                .lastDepositDate(lastDepositDate)
                .build();

        cacheService.cachePensionInfo(companyId, empId, result);
        return result;
    }
```

### 10-6. `updateSalaryAccount` — 급여 계좌 변경

```java
    @Transactional
    public void updateSalaryAccount(UUID companyId, Long empId, AccountUpdateReqDto req) {
        EmpAccounts existing = empAccountsRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMP_ACCOUNT_NOT_FOUND));

        // EmpAccounts 는 update 메서드가 없으므로 builder 로 재구성 후 save
        EmpAccounts updated = EmpAccounts.builder()
                .empAccountId(existing.getEmpAccountId())       // 동일 PK → save 시 update
                .employee(existing.getEmployee())
                .company(existing.getCompany())
                .bankName(req.getBankName())
                .accountNumber(req.getAccountNumber())
                .accountHolder(req.getAccountHolder())
                .build();
        empAccountsRepository.save(updated);

        // 캐시 무효화
        cacheService.evictSalaryInfoCache(companyId, empId);
        log.info("[MySalary] 급여 계좌 변경 완료 - empId={}, bank={}", empId, req.getBankName());
    }
}
```

### 10-7. 추가로 준비해야 할 파일

| 항목 | 상태 |
|------|------|
| `PayStubsRepository` | **신규 생성 필요** — `findByEmpIdAndCompany_CompanyIdAndPayYearMonthStartingWithOrderByPayYearMonthDesc`, `findByStubIdAndEmpIdAndCompany_CompanyId` |
| `PayItemsRepository#findByPayItemIdAndCompany_CompanyId` | 없으면 메서드 한 줄 추가 |
| `RetirementPensionDepositsRepository#findTopByEmployee_EmpIdAndCompany_CompanyIdAndDepStatusOrderByDepositDateDesc` | 없으면 한 줄 추가 |
| `MySalaryQueryRepository` (QueryDSL) | **신규** — `findPayStubItems(stubId)` 메서드 하나. PayStubs × PayrollDetails × PayItems JOIN |
| `MySalaryCacheService` | **신규** — Redis 연동 메서드들 (`getSalaryInfoCache`, `cacheSalaryInfo`, `evictSalaryInfoCache`, `getStubListCache`, ...) 스텁 먼저 만들고 나중에 Redis 연결 |
| `MySalaryInfoResDto` / `PayStubListResDto` / `PayStubDetailResDto` / `PayStubItemResDto` / `PensionInfoResDto` / `AccountUpdateReqDto` | 명세서 §8 경로에 맞춰 생성 |

### 10-8. 재귀 패턴에 대한 주의

명세서 §3, §4 는 `extractAllowances`, `mapStubsToDto`, `filterByType` 을 **재귀**로 작성하도록 기술돼 있음. 스트림(`stream().filter().map().toList()`) 으로 바꾸면 더 간결하지만 명세 일관성 유지를 위해 그대로 재귀로 작성.

추후 리팩터링 여지 메모:
```java
// 예: extractAllowances 를 스트림 버전으로 바꿨을 때
private List<FixedAllowanceDto> extractAllowances(
        List<SalaryContractDetail> details, UUID companyId) {
    return details.stream()
            .map(d -> {
                PayItems p = payItemsRepository
                        .findByPayItemIdAndCompany_CompanyId(d.getPayItemId(), companyId)
                        .orElse(null);
                return (p != null
                        && p.getPayItemType() == PayItemType.PAYMENT
                        && p.getPayItemCategory() != PayItemCategory.SALARY)
                        ? FixedAllowanceDto.builder()
                                .payItemId(p.getPayItemId())
                                .payItemName(p.getPayItemName())
                                .amount(d.getAmount() != null ? d.getAmount().longValue() : 0L)
                                .build()
                        : null;
            })
            .filter(java.util.Objects::nonNull)
            .toList();
}
```

---

## 11. Controller / DTO / Repository / Cache / QueryDSL 전체 코드

### 11-1. `MySalaryController` — 엔드포인트 진입점

**경로**: `hr-service/src/main/java/com/peoplecore/pay/controller/MySalaryController.java`

```java
package com.peoplecore.pay.controller;

import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.service.MySalaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/my")
@RequiredArgsConstructor
public class MySalaryController {

    private final MySalaryService mySalaryService;

    /** 내 급여 정보 조회 */
    @GetMapping("/info")
    public ResponseEntity<MySalaryInfoResDto> getMyInfo(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(mySalaryService.getMySalaryInfo(companyId, empId));
    }

    /** 연도별 급여명세서 목록 */
    @GetMapping("/stubs")
    public ResponseEntity<List<PayStubListResDto>> getStubList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam String year) {
        return ResponseEntity.ok(mySalaryService.getPayStubList(companyId, empId, year));
    }

    /** 급여명세서 상세 */
    @GetMapping("/stubs/{stubId}")
    public ResponseEntity<PayStubDetailResDto> getStubDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long stubId) {
        return ResponseEntity.ok(mySalaryService.getPayStubDetail(companyId, empId, stubId));
    }

    /** 퇴직연금 정보 */
    @GetMapping("/pension")
    public ResponseEntity<PensionInfoResDto> getPension(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(mySalaryService.getPensionInfo(companyId, empId));
    }

    /** 급여 계좌 변경 */
    @PutMapping("/account")
    public ResponseEntity<Void> updateAccount(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody @Valid AccountUpdateReqDto req) {
        mySalaryService.updateSalaryAccount(companyId, empId, req);
        return ResponseEntity.noContent().build();
    }
}
```

> **권한 처리**: `@RoleRequired` 미부착. `X-User-Id` / `X-User-Company` 헤더가 게이트웨이 JWT 에서 주입되므로 본인 데이터만 접근 가능.

### 11-2. `MySalaryInfoResDto` — 내 급여 정보 응답

**경로**: `hr-service/src/main/java/com/peoplecore/pay/dtos/MySalaryInfoResDto.java`

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class MySalaryInfoResDto {

    // 사원 기본
    private Long empId;
    private String empName;
    private String empEmail;
    private String empNum;
    private String empPhone;
    private String empType;
    private LocalDate empHireDate;
    private String deptName;
    private String gradeName;
    private String titleName;
    private String profileImageUrl;

    // 급여·계좌
    private SalaryInfoDto salaryInfo;
    private AccountDto salaryAccount;
    private RetirementAccountDto retirementAccount;

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class SalaryInfoDto {
        private Long annualSalary;
        private Long monthlySalary;
        private List<FixedAllowanceDto> fixedAllowances;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class FixedAllowanceDto {
        private Long payItemId;
        private String payItemName;
        private Long amount;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class AccountDto {
        private Long empAccountId;
        private String bankName;
        private String accountNumber;
        private String accountHolder;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class RetirementAccountDto {
        private Long retirementAccountId;
        private String retirementType;
        private String pensionProvider;
        private String accountNumber;
    }
}
```

### 11-3. `PayStubListResDto` — 연도별 명세서 목록 아이템

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class PayStubListResDto {
    private Long stubId;                // entity: payStubsId
    private String payYearMonth;        // "YYYY-MM"
    private LocalDateTime issuedAt;     // entity: issuedAT
    private Long totalPay;
    private Long totalDeduction;
    private Long netPay;
    private String sendStatus;          // entity: sendStatus.name()
}
```

### 11-4. `PayStubDetailResDto` — 명세서 상세

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class PayStubDetailResDto {
    private Long stubId;
    private String payYearMonth;
    private LocalDateTime issuedAt;

    // 사원·부서 스냅샷
    private String empName;
    private String deptName;

    // 합계
    private Long totalPay;
    private Long totalDeduction;
    private Long netPay;

    // 항목 분류 (지급/공제)
    private List<PayStubItemResDto> paymentItems;
    private List<PayStubItemResDto> deductionItems;

    // 파일
    private String pdfUrl;
}
```

### 11-5. `PayStubItemResDto` — 명세서 개별 항목

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class PayStubItemResDto {
    private Long payItemId;
    private String payItemName;
    private String payItemType;   // "PAYMENT" / "DEDUCTION"
    private String payItemCategory;
    private Long amount;
    private Boolean isTaxable;
}
```

### 11-6. `PensionInfoResDto` — 퇴직연금 적립금

```java
package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class PensionInfoResDto {
    private String retirementType;       // "DB" / "DC" / "severance"
    private Long monthlyDeposit;         // DC 형만 값 존재
    private Long totalDeposited;         // 누적 적립금 (COMPLETED 합산)
    private LocalDateTime lastDepositDate;
}
```

### 11-7. `AccountUpdateReqDto` — 급여 계좌 변경 요청

```java
package com.peoplecore.pay.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class AccountUpdateReqDto {

    @NotBlank(message = "은행명은 필수입니다")
    private String bankName;

    @NotBlank(message = "계좌번호는 필수입니다")
    private String accountNumber;

    @NotBlank(message = "예금주명은 필수입니다")
    private String accountHolder;
}
```

### 11-8. `PayStubsRepository` — 신규 작성

**경로**: `hr-service/src/main/java/com/peoplecore/pay/repository/PayStubsRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayStubs;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayStubsRepository extends JpaRepository<PayStubs, Long> {

    /**
     * 특정 사원의 연도별 명세서 목록.
     * payYearMonth 가 "YYYY-MM" 형식이라 startsWith(year) 로 필터.
     */
    List<PayStubs>
    findByEmpIdAndCompany_CompanyIdAndPayYearMonthStartingWithOrderByPayYearMonthDesc(
            Long empId, UUID companyId, String payYearMonthPrefix);

    /** 본인 소유 검증 포함 단건 조회 */
    Optional<PayStubs> findByPayStubsIdAndEmpIdAndCompany_CompanyId(
            Long payStubsId, Long empId, UUID companyId);
}
```

> 서비스에서 호출하는 메서드명은 스펙상 `findByStubId...` 지만 실제 엔티티 PK 가 `payStubsId` 이므로 메서드명도 그에 맞춘 것. 서비스 호출부는 `payStubsRepository.findByPayStubsIdAndEmpIdAndCompany_CompanyId(...)` 로 맞춰 쓰면 됨.

### 11-9. `PayItemsRepository` — 메서드 추가

기존 파일에 메서드 한 줄만 추가:

```java
Optional<PayItems> findByPayItemIdAndCompany_CompanyId(Long payItemId, UUID companyId);
```

### 11-10. `RetirementPensionDepositsRepository` — 메서드 추가

기존 파일에 두 줄 추가:

```java
Optional<RetirementPensionDeposits>
findTopByEmployee_EmpIdAndCompany_CompanyIdAndDepStatusOrderByDepositDateDesc(
        Long empId, UUID companyId, DepStatus depStatus);
```

### 11-11. `MySalaryQueryRepository` — QueryDSL 신규

**경로**: `hr-service/src/main/java/com/peoplecore/pay/repository/MySalaryQueryRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.QPayItems;
import com.peoplecore.pay.domain.QPayrollDetails;
import com.peoplecore.pay.domain.QPayStubs;
import com.peoplecore.pay.dtos.PayStubItemResDto;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MySalaryQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 특정 PayStub 의 개별 항목 조회.
     * PayStubs.payrollRunId + PayStubs.empId 로 PayrollDetails 매칭 후 PayItems 조인.
     */
    public List<PayStubItemResDto> findPayStubItems(Long payStubsId) {
        QPayStubs stub = QPayStubs.payStubs;
        QPayrollDetails detail = QPayrollDetails.payrollDetails;
        QPayItems item = QPayItems.payItems;

        return queryFactory
                .select(Projections.constructor(
                        PayStubItemResDto.class,
                        item.payItemId,
                        item.payItemName,
                        item.payItemType.stringValue(),
                        item.payItemCategory.stringValue(),
                        detail.amount,
                        item.isTaxable
                ))
                .from(stub)
                .join(detail).on(
                        detail.payrollRuns.payrollRunId.eq(stub.payrollRunId),
                        detail.employee.empId.eq(stub.empId)
                )
                .join(item).on(detail.payItems.payItemId.eq(item.payItemId))
                .where(stub.payStubsId.eq(payStubsId))
                .orderBy(item.sortOrder.asc())
                .fetch();
    }
}
```

> Q클래스(`QPayStubs`, `QPayrollDetails`, `QPayItems`) 는 gradle 빌드 시 자동 생성. 빌드 전이면 클래스 이름이 안 나올 수 있음 — `./gradlew :hr-service:compileJava` 한 번 돌리면 생성됨.

### 11-12. `MySalaryCacheService` — Redis 실제 연동

**경로**: `hr-service/src/main/java/com/peoplecore/pay/cache/MySalaryCacheService.java`

프로젝트의 기존 Redis 패턴(`EmailRedisConfig`, `SmsRedisConfig`) 을 따라:
- 전용 `RedisConnectionFactory` + `RedisTemplate` 빈
- 고유 db 인덱스 배정 (다른 캐시와 충돌 방지)
- JSON 직렬화(Jackson)로 POJO 저장
- 각 캐시 타입별 TTL 설정

#### (1) `MySalaryRedisConfig` — Redis 빈 설정

**경로**: `hr-service/src/main/java/com/peoplecore/pay/cache/MySalaryRedisConfig.java`

```java
package com.peoplecore.pay.cache;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class MySalaryRedisConfig {

    @Value("${spring.data.redis.host1}")
    private String redisHost;

    @Value("${spring.data.redis.port1}")
    private int redisPort;

    /** 기존 EmailRedisConfig(db=2), SmsRedisConfig 등과 충돌 안 나게 db=5 사용 */
    private static final int MY_SALARY_DB_INDEX = 5;

    @Bean
    @Qualifier("mySalaryRedisConnectionFactory")
    public RedisConnectionFactory mySalaryRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(MY_SALARY_DB_INDEX);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    @Qualifier("mySalaryRedisTemplate")
    public RedisTemplate<String, Object> mySalaryRedisTemplate(
            @Qualifier("mySalaryRedisConnectionFactory") RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key: String, Value: JSON
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        ObjectMapper objectMapper = buildObjectMapper();
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /** LocalDate/LocalDateTime 지원 + 다형성 타입 정보 포함 */
    private ObjectMapper buildObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());                     // Java 8 time
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 다형성 타입 복원 — MySalaryInfoResDto 같은 DTO 재역직렬화 위해
        om.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.peoplecore.pay.dtos")
                        .allowIfSubType("java.util")
                        .allowIfSubType("java.time")
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL);
        return om;
    }
}
```

#### (2) `MySalaryCacheService` — 실제 Redis 동작

```java
package com.peoplecore.pay.cache;

import com.peoplecore.pay.dtos.MySalaryInfoResDto;
import com.peoplecore.pay.dtos.MySeveranceEstimateResDto;
import com.peoplecore.pay.dtos.PayStubDetailResDto;
import com.peoplecore.pay.dtos.PayStubListResDto;
import com.peoplecore.pay.dtos.PensionInfoResDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 내 급여·명세서·퇴직연금·추계액 Redis 캐시.
 * - key 네이밍: mysalary:{type}:{companyId}:{empId}[:{extra}]
 * - TTL: 캐시 타입별 상이 (이벤트 기반 invalidate 와 병행)
 */
@Slf4j
@Service
public class MySalaryCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    // ── TTL (이벤트 무효화를 안전망 TTL 이 보완) ──
    private static final Duration TTL_SALARY_INFO      = Duration.ofHours(1);
    private static final Duration TTL_STUB_LIST        = Duration.ofHours(24);
    private static final Duration TTL_STUB_DETAIL      = Duration.ofDays(7);
    private static final Duration TTL_PENSION          = Duration.ofHours(1);
    private static final Duration TTL_SEVERANCE_EST    = Duration.ofHours(1);

    // ── Key prefix ──
    private static final String PREFIX_SALARY_INFO    = "mysalary:info";
    private static final String PREFIX_STUB_LIST      = "mysalary:stubs";
    private static final String PREFIX_STUB_DETAIL    = "mysalary:stub-detail";
    private static final String PREFIX_PENSION        = "mysalary:pension";
    private static final String PREFIX_SEVERANCE_EST  = "mysalary:severance-estimate";

    public MySalaryCacheService(
            @Qualifier("mySalaryRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ───────── 내 급여 정보 ─────────

    public <T> T getSalaryInfoCache(UUID companyId, Long empId, Class<T> clazz) {
        return get(salaryInfoKey(companyId, empId), clazz);
    }

    public void cacheSalaryInfo(UUID companyId, Long empId, Object value) {
        set(salaryInfoKey(companyId, empId), value, TTL_SALARY_INFO);
    }

    public void evictSalaryInfoCache(UUID companyId, Long empId) {
        delete(salaryInfoKey(companyId, empId));
    }

    private String salaryInfoKey(UUID companyId, Long empId) {
        return String.format("%s:%s:%d", PREFIX_SALARY_INFO, companyId, empId);
    }

    // ───────── 명세서 목록 ─────────

    @SuppressWarnings("unchecked")
    public List<PayStubListResDto> getStubListCache(UUID companyId, Long empId, String year) {
        return (List<PayStubListResDto>) get(stubListKey(companyId, empId, year), List.class);
    }

    public void cacheStubList(UUID companyId, Long empId, String year,
                              List<PayStubListResDto> value) {
        set(stubListKey(companyId, empId, year), value, TTL_STUB_LIST);
    }

    public void evictStubListCache(UUID companyId, Long empId) {
        // year 가 여럿일 수 있으니 패턴으로 삭제
        deleteByPattern(PREFIX_STUB_LIST + ":" + companyId + ":" + empId + ":*");
    }

    private String stubListKey(UUID companyId, Long empId, String year) {
        return String.format("%s:%s:%d:%s", PREFIX_STUB_LIST, companyId, empId, year);
    }

    // ───────── 명세서 상세 ─────────

    public PayStubDetailResDto getStubDetailCache(UUID companyId, Long empId, Long stubId) {
        return get(stubDetailKey(companyId, empId, stubId), PayStubDetailResDto.class);
    }

    public void cacheStubDetail(UUID companyId, Long empId, Long stubId,
                                PayStubDetailResDto value) {
        set(stubDetailKey(companyId, empId, stubId), value, TTL_STUB_DETAIL);
    }

    private String stubDetailKey(UUID companyId, Long empId, Long stubId) {
        return String.format("%s:%s:%d:%d", PREFIX_STUB_DETAIL, companyId, empId, stubId);
    }

    // ───────── 퇴직연금 ─────────

    public PensionInfoResDto getPensionCache(UUID companyId, Long empId) {
        return get(pensionKey(companyId, empId), PensionInfoResDto.class);
    }

    public void cachePensionInfo(UUID companyId, Long empId, PensionInfoResDto value) {
        set(pensionKey(companyId, empId), value, TTL_PENSION);
    }

    public void evictPensionCache(UUID companyId, Long empId) {
        delete(pensionKey(companyId, empId));
    }

    private String pensionKey(UUID companyId, Long empId) {
        return String.format("%s:%s:%d", PREFIX_PENSION, companyId, empId);
    }

    // ───────── 퇴직금 예상(추계액) ─────────

    public <T> T getSeveranceEstimateCache(
            UUID companyId, Long empId, LocalDate baseDate, Class<T> clazz) {
        return get(severanceEstKey(companyId, empId, baseDate), clazz);
    }

    public void cacheSeveranceEstimate(
            UUID companyId, Long empId, LocalDate baseDate, MySeveranceEstimateResDto value) {
        set(severanceEstKey(companyId, empId, baseDate), value, TTL_SEVERANCE_EST);
    }

    public void evictSeveranceEstimateCache(UUID companyId, Long empId) {
        deleteByPattern(PREFIX_SEVERANCE_EST + ":" + companyId + ":" + empId + ":*");
    }

    private String severanceEstKey(UUID companyId, Long empId, LocalDate baseDate) {
        return String.format("%s:%s:%d:%s", PREFIX_SEVERANCE_EST, companyId, empId, baseDate);
    }

    // ───────── 저수준 헬퍼 ─────────

    @SuppressWarnings("unchecked")
    private <T> T get(String key, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) return null;
            if (clazz.isInstance(value)) return (T) value;
            log.warn("[MySalaryCache] 타입 불일치 - key={}, expect={}, actual={}",
                    key, clazz.getSimpleName(), value.getClass().getSimpleName());
            return null;
        } catch (Exception e) {
            // Redis 장애가 서비스 중단으로 이어지지 않도록 swallow
            log.warn("[MySalaryCache] get 실패 - key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    private void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("[MySalaryCache] set 실패 - key={}, err={}", key, e.getMessage());
        }
    }

    private void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[MySalaryCache] delete 실패 - key={}, err={}", key, e.getMessage());
        }
    }

    private void deleteByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("[MySalaryCache] deleteByPattern 실패 - pattern={}, err={}",
                    pattern, e.getMessage());
        }
    }
}
```

#### (3) 캐시 키 / TTL 정리

| 캐시 | 키 형식 | TTL | 무효화 방법 |
|------|---------|-----|-----------|
| 내 급여 정보 | `mysalary:info:{companyId}:{empId}` | 1시간 | 계좌 변경 시 evict |
| 명세서 목록 | `mysalary:stubs:{companyId}:{empId}:{year}` | 24시간 | 급여지급 완료 이벤트 시 패턴 evict |
| 명세서 상세 | `mysalary:stub-detail:{companyId}:{empId}:{stubId}` | 7일 | 명세서는 사실상 불변이라 긴 TTL OK |
| 퇴직연금 | `mysalary:pension:{companyId}:{empId}` | 1시간 | DC 적립 완료 이벤트 시 evict |
| 퇴직금 추계 | `mysalary:severance-estimate:{companyId}:{empId}:{baseDate}` | 1시간 | 급여확정/연봉계약 변경 시 패턴 evict |

#### (4) 장애 내성 원칙

모든 Redis 호출은 try-catch 로 감싸서 **Redis 다운 시에도 서비스는 계속 동작**. 캐시 실패 시:
- `get` → `null` 반환 → 서비스가 miss 로 간주하고 DB 조회
- `set`/`delete` → swallow → DB 데이터는 정상 저장·조회됨

이는 "캐시는 성능 최적화 수단일 뿐, 데이터 진실의 원천이 아니다" 원칙.

#### (5) 이벤트 기반 invalidate 훅 (선택 — 추후 구현)

급여확정/계좌변경/DC적립 등의 이벤트 발생 시 관련 캐시를 즉시 비우는 Kafka consumer.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class MySalaryCacheInvalidateConsumer {

    private final MySalaryCacheService cache;
    private final ObjectMapper om;

    /** 급여 지급 확정 → 해당 사원들 명세서·급여·추계 캐시 invalidate */
    @KafkaListener(topics = "payroll-paid", groupId = "hr-mysalary-cache")
    public void onPayrollPaid(String message) throws Exception {
        PayrollPaidEvent event = om.readValue(message, PayrollPaidEvent.class);
        for (Long empId : event.getAffectedEmpIds()) {
            cache.evictSalaryInfoCache(event.getCompanyId(), empId);
            cache.evictStubListCache(event.getCompanyId(), empId);
            cache.evictSeveranceEstimateCache(event.getCompanyId(), empId);
        }
    }

    /** DC 적립 완료 → 퇴직연금·추계 캐시 invalidate */
    @KafkaListener(topics = "dc-deposit-completed", groupId = "hr-mysalary-cache")
    public void onDcDeposit(String message) throws Exception {
        DcDepositCompletedEvent event = om.readValue(message, DcDepositCompletedEvent.class);
        cache.evictPensionCache(event.getCompanyId(), event.getEmpId());
        cache.evictSeveranceEstimateCache(event.getCompanyId(), event.getEmpId());
    }
}
```

> 이벤트 DTO(`PayrollPaidEvent`, `DcDepositCompletedEvent`) 가 없으면 common 모듈에 추가 필요. 이 consumer 는 **캐시가 실패해도 비즈니스에 영향 없어서** `@RetryableTopic` 없이 가도 무방.

#### (6) Redis 연결 장애 감지 — Actuator health 체크 (권장)

운영 환경에서 Redis 가 장기간 다운되면 DB 부하가 급증. Spring Boot Actuator 의 Redis 헬스체크를 켜두고 모니터링에 연동:

```yaml
# application.yml
management:
  endpoint:
    health:
      show-details: when-authorized
  health:
    redis:
      enabled: true
```

### 11-13. 서비스 내 참조 보정 (PayStubs PK 이름 차이)

명세서는 `stubId` 로 쓰지만 실제 엔티티 PK 는 `payStubsId`. 11-8 의 Repository 메서드명을 `findByPayStubsIdAndEmpIdAndCompany_CompanyId` 로 쓴 것에 맞춰, §10-4 의 서비스 코드 호출부는 다음과 같이 조정:

```java
// §10-4 내 해당 부분
PayStubs stub = payStubsRepository
        .findByPayStubsIdAndEmpIdAndCompany_CompanyId(stubId, empId, companyId)   // ← 수정
        .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
```

### 11-14. 필요한 ErrorCode

`ErrorCode` enum 에 다음이 없으면 추가:

```java
EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND, "사원 정보를 찾을 수 없습니다."),
EMP_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "급여 계좌를 찾을 수 없습니다."),
NOT_FOUND(HttpStatus.NOT_FOUND, "요청하신 리소스를 찾을 수 없습니다."),
```

### 11-15. 파일 작성 체크리스트

```
[ ] hr-service/pay/controller/MySalaryController.java           — 11-1
[ ] hr-service/pay/service/MySalaryService.java                 — §10 전체
[ ] hr-service/pay/dtos/MySalaryInfoResDto.java                 — 11-2
[ ] hr-service/pay/dtos/PayStubListResDto.java                  — 11-3
[ ] hr-service/pay/dtos/PayStubDetailResDto.java                — 11-4
[ ] hr-service/pay/dtos/PayStubItemResDto.java                  — 11-5
[ ] hr-service/pay/dtos/PensionInfoResDto.java                  — 11-6
[ ] hr-service/pay/dtos/AccountUpdateReqDto.java                — 11-7
[ ] hr-service/pay/repository/PayStubsRepository.java           — 11-8 (신규)
[ ] hr-service/pay/repository/PayItemsRepository.java           — 11-9 (메서드 추가)
[ ] hr-service/pay/repository/RetirementPensionDepositsRepository.java — 11-10 (메서드 추가)
[ ] hr-service/pay/repository/MySalaryQueryRepository.java      — 11-11 (신규)
[ ] hr-service/pay/cache/MySalaryCacheService.java              — 11-12 (신규)
[ ] common/exception/ErrorCode.java                             — 11-14 (필요 시 추가)
```

### 11-16. 빌드 시 주의

- `QPayStubs` 등 Q클래스가 자동 생성 안 돼있으면 `./gradlew :hr-service:compileJava` 또는 IntelliJ 의 "Rebuild Project"
- `Employee`/`SalaryContract`/`PayItems` 등 엔티티 필드 이름이 실제와 다르면 getter 호출부 수정 필요 (§10 의 `emp.getXxx()` 라인들)
