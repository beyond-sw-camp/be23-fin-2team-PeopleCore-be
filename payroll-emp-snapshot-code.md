# PayrollEmpSnapshot 설계문서

> 급여 산정 시점의 사원 인사정보를 스냅샷으로 보관하여,  
> 이후 부서이동·직급변경·근무그룹 변경이 발생해도 당시 기록을 정확히 조회할 수 있도록 한다.

---

## 1. 스냅샷이 필요한 이유

| 항목 | Employee 현재값 | 변경 가능성 | 스냅샷 필요 |
|---|---|---|---|
| `empName` | `employee.empName` | 개명 시 변경 | ✅ |
| `deptName` | `employee.dept.deptName` | 부서이동 시 변경 | ✅ |
| `gradeName` | `employee.grade.gradeName` | 승진/강등 시 변경 | ✅ |
| `workGroupName` | `employee.workGroup.groupName` | 근무그룹 변경 시 변경 | ✅ |
| `empHireDate` | `employee.empHireDate` | 변경 불가 (고정) | ❌ (조인으로 충분) |

→ `empName`, `deptName`, `gradeName`, `workGroupName` 4가지를 스냅샷

---

## 2. 급여 파트 내 스냅샷 적용 범위

### 2-1. PayrollEmpSnapshot (신규) — 급여대장용

| 대상 | 현재 상태 | 조치 |
|---|---|---|
| **급여대장** (`PayrollRuns` + `PayrollDetails`) | 사원 인사정보 스냅샷 없음. Employee 조인만 사용 | **PayrollEmpSnapshot 테이블 신규 생성** |

### 2-2. 기존 엔티티 스냅샷 현황

| 대상 | 현재 상태 | 조치 |
|---|---|---|
| **퇴직금대장** (`SeverancePays`) | severance-ledger-code.md에서 `empName`, `deptName`, `gradeName` 스냅샷 설계 완료 | `workGroupName` 추가 |
| **연차수당** (`LeaveAllowance`) | 스냅샷 없음. Employee 조인만 사용 | 연차수당은 연 1회 산정 → 조회 시 PayrollEmpSnapshot 참조 가능 (appliedPayrollRunId로 연결) |
| **정산보험료** (`InsuranceSettlement`) | 스냅샷 없음. payrollRunId 보유 | PayrollEmpSnapshot 참조 가능 (payrollRuns.payrollRunId로 연결) |

> **결론**: PayrollEmpSnapshot 하나만 만들면 급여대장·연차수당·정산보험료 모두 커버 가능.  
> 퇴직금대장은 독립 증빙이므로 자체 스냅샷 유지.

---

## 3. Entity

```java
package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "payroll_emp_snapshot",
    indexes = {
        @Index(name = "idx_snapshot_run_emp", columnList = "payroll_run_id, emp_id"),
        @Index(name = "idx_snapshot_emp", columnList = "emp_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_snapshot_run_emp", columnNames = {"payroll_run_id", "emp_id"})
    })
public class PayrollEmpSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long snapshotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRuns payrollRuns;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /* ── 스냅샷 필드 ── */
    @Column(nullable = false, length = 50)
    private String empName;             // 사원명

    @Column(nullable = false, length = 100)
    private String deptName;            // 부서명

    @Column(nullable = false, length = 50)
    private String gradeName;           // 직급명

    @Column(length = 100)
    private String workGroupName;       // 근무그룹명 (nullable: 미배정 사원 가능)


    /* ── 정적 팩토리 ── */
    public static PayrollEmpSnapshot capture(PayrollRuns payrollRuns, Employee emp, Company company) {
        return PayrollEmpSnapshot.builder()
                .payrollRuns(payrollRuns)
                .employee(emp)
                .company(company)
                .empName(emp.getEmpName())
                .deptName(emp.getDept().getDeptName())
                .gradeName(emp.getGrade().getGradeName())
                .workGroupName(emp.getWorkGroup() != null ? emp.getWorkGroup().getGroupName() : null)
                .build();
    }
}
```

---

## 4. Repository

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayrollEmpSnapshot;
import com.peoplecore.pay.domain.PayrollRuns;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollEmpSnapshotRepository extends JpaRepository<PayrollEmpSnapshot, Long> {

    // 급여회차의 전체 스냅샷
    List<PayrollEmpSnapshot> findByPayrollRuns(PayrollRuns payrollRuns);

    // 급여회차 + 특정 사원
    Optional<PayrollEmpSnapshot> findByPayrollRuns_PayrollRunIdAndEmployee_EmpId(Long payrollRunId, Long empId);

    // 급여회차에 스냅샷 존재 여부
    boolean existsByPayrollRuns_PayrollRunId(Long payrollRunId);

    // 급여회차 삭제 시 일괄 삭제
    void deleteByPayrollRuns_PayrollRunId(Long payrollRunId);
}
```

---

## 5. DTO (조회용)

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.PayrollEmpSnapshot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollEmpSnapshotResDto {

    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private String workGroupName;

    public static PayrollEmpSnapshotResDto fromEntity(PayrollEmpSnapshot s) {
        return PayrollEmpSnapshotResDto.builder()
                .empId(s.getEmployee().getEmpId())
                .empName(s.getEmpName())
                .deptName(s.getDeptName())
                .gradeName(s.getGradeName())
                .workGroupName(s.getWorkGroupName())
                .build();
    }
}
```

---

## 6. Service 연동 — 스냅샷 생성 시점

급여 산정(calculate) 시 PayrollDetails를 생성하면서 동시에 스냅샷도 생성한다.

```java
// PayrollService.java — 급여 산정 로직 내부

/**
 * 급여 산정 시 사원별 인사정보 스냅샷 일괄 생성
 * PayrollDetails 생성 직후 호출
 */
private void captureEmpSnapshots(PayrollRuns payrollRuns, List<Employee> targetEmployees, Company company) {
    // 이미 스냅샷이 있으면 스킵 (재산정 시 기존 스냅샷 유지)
    if (snapshotRepository.existsByPayrollRuns_PayrollRunId(payrollRuns.getPayrollRunId())) {
        return;
    }

    List<PayrollEmpSnapshot> snapshots = targetEmployees.stream()
            .map(emp -> PayrollEmpSnapshot.capture(payrollRuns, emp, company))
            .toList();

    snapshotRepository.saveAll(snapshots);
}
```

### 재산정(recalculate) 시 스냅샷 정책

| 시나리오 | 동작 |
|---|---|
| 최초 산정 | 스냅샷 생성 |
| 재산정 (CALCULATING 상태) | 기존 스냅샷 유지 (최초 산정 시점 기준 보존) |
| 급여회차 삭제 | 스냅샷도 함께 삭제 (`deleteByPayrollRuns_PayrollRunId`) |

> 재산정 시 스냅샷을 갱신하고 싶으면 `captureEmpSnapshots`에서 existsBy 체크를 제거하고  
> delete → saveAll 로 교체하면 된다. 정책에 따라 선택.

---

## 7. 조회 시 활용 예시

### 7-1. 급여대장 목록 조회 (QueryDSL)

```java
// PayrollSearchRepository.java (QueryDSL)

QPayrollDetails pd = QPayrollDetails.payrollDetails;
QPayrollEmpSnapshot snap = QPayrollEmpSnapshot.payrollEmpSnapshot;

// PayrollDetails와 스냅샷 조인
List<PayrollLedgerRow> rows = queryFactory
        .select(Projections.constructor(PayrollLedgerRow.class,
                pd.employee.empId,
                snap.empName,           // ← 스냅샷에서 가져옴
                snap.deptName,          // ← 스냅샷에서 가져옴
                snap.gradeName,         // ← 스냅샷에서 가져옴
                snap.workGroupName,     // ← 스냅샷에서 가져옴
                pd.payItemName,
                pd.amount
        ))
        .from(pd)
        .leftJoin(snap)
            .on(snap.payrollRuns.eq(pd.payrollRuns)
                .and(snap.employee.eq(pd.employee)))
        .where(pd.payrollRuns.payrollRunId.eq(payrollRunId))
        .fetch();
```

### 7-2. 연차수당 조회 시 활용

```java
// LeaveAllowance → appliedPayrollRunId로 스냅샷 조회
Optional<PayrollEmpSnapshot> snapshot = snapshotRepository
        .findByPayrollRuns_PayrollRunIdAndEmployee_EmpId(
                leaveAllowance.getAppliedPayrollRunId(),
                leaveAllowance.getEmployee().getEmpId()
        );
// snapshot이 있으면 당시 부서/직급 사용, 없으면 Employee 현재값 fallback
```

### 7-3. 정산보험료 조회 시 활용

```java
// InsuranceSettlement → payrollRuns.payrollRunId로 스냅샷 조회
Optional<PayrollEmpSnapshot> snapshot = snapshotRepository
        .findByPayrollRuns_PayrollRunIdAndEmployee_EmpId(
                settlement.getPayrollRuns().getPayrollRunId(),
                settlement.getEmployee().getEmpId()
        );
```

---

## 8. DDL

```sql
CREATE TABLE payroll_emp_snapshot (
    snapshot_id         BIGINT          AUTO_INCREMENT PRIMARY KEY,
    payroll_run_id      BIGINT          NOT NULL,
    emp_id              BIGINT          NOT NULL,
    company_id          BIGINT          NOT NULL,
    emp_name            VARCHAR(50)     NOT NULL,
    dept_name           VARCHAR(100)    NOT NULL,
    grade_name          VARCHAR(50)     NOT NULL,
    work_group_name     VARCHAR(100)    NULL,

    CONSTRAINT uk_snapshot_run_emp UNIQUE (payroll_run_id, emp_id),
    INDEX idx_snapshot_run_emp (payroll_run_id, emp_id),
    INDEX idx_snapshot_emp (emp_id),

    CONSTRAINT fk_snapshot_payroll_run FOREIGN KEY (payroll_run_id) REFERENCES payroll_runs (payroll_run_id),
    CONSTRAINT fk_snapshot_employee    FOREIGN KEY (emp_id) REFERENCES employee (emp_id),
    CONSTRAINT fk_snapshot_company     FOREIGN KEY (company_id) REFERENCES company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 9. severance-ledger-code.md 수정사항

퇴직금대장(`SeverancePays`)에 `workGroupName` 필드 추가 필요:

```java
// SeverancePays 엔티티에 추가
@Column(length = 100)
private String workGroupName;       // 근무그룹명 (스냅샷)
```

빌더에서 세팅:
```java
.workGroupName(emp.getWorkGroup() != null ? emp.getWorkGroup().getGroupName() : null)
```

SeveranceResDto, SeveranceDetailResDto에도 `workGroupName` 필드 추가.

---

## 10. 체크리스트

- [ ] `PayrollEmpSnapshot` 엔티티 생성
- [ ] `PayrollEmpSnapshotRepository` 생성
- [ ] `PayrollEmpSnapshotResDto` 생성
- [ ] `PayrollService` 급여 산정 로직에 `captureEmpSnapshots()` 호출 추가
- [ ] 급여대장 조회 QueryDSL에 스냅샷 조인 적용
- [ ] 연차수당 조회 시 스냅샷 참조 로직 추가
- [ ] 정산보험료 조회 시 스냅샷 참조 로직 추가
- [ ] `SeverancePays`에 `workGroupName` 필드 추가
- [ ] severance-ledger-code.md 내 DTO에 `workGroupName` 반영
