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
