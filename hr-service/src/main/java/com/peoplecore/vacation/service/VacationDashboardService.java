package com.peoplecore.vacation.service;

import com.peoplecore.department.domain.Department;
import com.peoplecore.department.domain.UseStatus;
import com.peoplecore.department.repository.DepartmentRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.dto.BalanceExpiryQueryDto;
import com.peoplecore.vacation.dto.DepartmentMemberVacationResponseDto;
import com.peoplecore.vacation.dto.DepartmentVacationSummaryResponseDto;
import com.peoplecore.vacation.dto.EmployeeVacationAggregationQueryDto;
import com.peoplecore.vacation.dto.ManualGrantSumQueryDto;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationBalanceQueryRepository;
import com.peoplecore.vacation.repository.VacationBalanceRepository;
import com.peoplecore.vacation.repository.VacationLedgerRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/* 전사 휴가 관리 대시보드 서비스 - 부서 카드 / 부서별 사원 상세 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class VacationDashboardService {

    /* 기본 "소진율 낮음" 기준(%) - 프론트 토글 기본값과 맞춤 */
    private static final int DEFAULT_LOW_USAGE_THRESHOLD = 30;

    private final VacationBalanceQueryRepository balanceQueryRepository;
    private final VacationBalanceRepository balanceRepository;
    private final VacationLedgerRepository ledgerRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final VacationTypeRepository vacationTypeRepository;

    @Autowired
    public VacationDashboardService(VacationBalanceQueryRepository balanceQueryRepository,
                                    VacationBalanceRepository balanceRepository,
                                    VacationLedgerRepository ledgerRepository,
                                    EmployeeRepository employeeRepository,
                                    DepartmentRepository departmentRepository,
                                    VacationTypeRepository vacationTypeRepository) {
        this.balanceQueryRepository = balanceQueryRepository;
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.vacationTypeRepository = vacationTypeRepository;
    }

    /* year(연도) → 집계 쿼리에 넘길 기준일(LocalDate) 변환 - 올해면 오늘, 과거면 그해 말일 스냅샷 */
    /* HIRE 정책 기념일 미경과 사원도 직전 grant 잔여가 매칭되도록 today 기반 필터로 통일 */
    private LocalDate resolveReferenceDate(Integer year) {
        LocalDate today = LocalDate.now();
        if (year == null || year == today.getYear()) return today;
        return LocalDate.of(year, 12, 31);
    }

    /* 부서별 요약 카드 목록 - 부서명 가나다순 */
    public List<DepartmentVacationSummaryResponseDto> getDepartmentSummaries(
            UUID companyId, Integer year, Integer lowUsageThreshold) {
        int threshold = (lowUsageThreshold != null) ? lowUsageThreshold : DEFAULT_LOW_USAGE_THRESHOLD;

        // 전체 사원 집계 1회 - balance 없는 사원도 0 으로 포함
        // year 가 올해면 오늘, 과거면 그해 말일을 스냅샷으로 사용 (HIRE/FISCAL 무관 유효 grant 매칭)
        LocalDate referenceDate = resolveReferenceDate(year);
        List<EmployeeVacationAggregationQueryDto> aggs =
                balanceQueryRepository.aggregateAllForCompany(companyId, referenceDate);

        // 메모리 grouping
        Map<Long, List<EmployeeVacationAggregationQueryDto>> byDept = aggs.stream()
                .collect(Collectors.groupingBy(EmployeeVacationAggregationQueryDto::getDeptId));

        // 활성 부서 (is_use=Y) 가나다순 조회
        List<Department> depts = departmentRepository
                .findByCompany_CompanyIdAndIsUseOrderByDeptNameAsc(companyId, UseStatus.Y);

        return depts.stream()
                .map(d -> toDeptSummary(d, byDept.getOrDefault(d.getDeptId(), List.of()), threshold))
                .toList();
    }

    /* 부서 사원 상세 페이지 - 입사일 오름차순 */
    public Page<DepartmentMemberVacationResponseDto> getDepartmentMembers(
            UUID companyId, Long deptId, Integer year, String typeCode, Pageable pageable) {

        // 구분 유형 존재 검증 - 잘못된 typeCode 면 빠른 실패
        VacationType type = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, typeCode)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));

        // 페이지 단위 집계 (count + content)
        // year → 조회 기준일 (올해=오늘, 과거=그해 말일)
        LocalDate referenceDate = resolveReferenceDate(year);
        Page<EmployeeVacationAggregationQueryDto> page =
                balanceQueryRepository.aggregateByDeptPageable(companyId, referenceDate, deptId, pageable);
        if (page.isEmpty()) {
            return page.map(a -> null);
        }

        // 현재 페이지 empIds
        List<Long> empIds = page.getContent().stream()
                .map(EmployeeVacationAggregationQueryDto::getEmpId).toList();

        // 조정(MANUAL_GRANT) 합 - 이력 없는 사원은 map 조회 시 기본 0
        Map<Long, BigDecimal> adjustMap = ledgerRepository
                .sumManualGrantByEmpsAndYear(companyId, empIds, year).stream()
                .collect(Collectors.toMap(
                        ManualGrantSumQueryDto::getEmpId,
                        ManualGrantSumQueryDto::getAdjustedDays));

        // 사원 메타 일괄 조회 (empName/grade/dept/hireDate)
        Map<Long, Employee> empMap = employeeRepository.findAllById(empIds).stream()
                .collect(Collectors.toMap(Employee::getEmpId, Function.identity()));

        // 선택 유형 balance 의 grantedAt/expiresAt - 연차 사용기간 표시용
        Map<Long, BalanceExpiryQueryDto> expiryMap = balanceRepository
                .findExpiryByEmpsAndType(companyId, typeCode, year, empIds).stream()
                .collect(Collectors.toMap(BalanceExpiryQueryDto::getEmpId, Function.identity()));

        LocalDate today = LocalDate.now();
        return page.map(a -> toMemberDto(a, empMap, adjustMap, expiryMap, type, today));
    }

    /* 부서 요약 카드 매핑 - 사원 집계 리스트를 받아 합계·평균 계산 */
    private DepartmentVacationSummaryResponseDto toDeptSummary(
            Department dept, List<EmployeeVacationAggregationQueryDto> members, int threshold) {
        int count = members.size();
        BigDecimal total = sumBy(members, EmployeeVacationAggregationQueryDto::getTotalDays);
        BigDecimal used = sumBy(members, EmployeeVacationAggregationQueryDto::getUsedDays);
        BigDecimal stat = sumBy(members, EmployeeVacationAggregationQueryDto::getStatutoryAvailable);
        BigDecimal spec = sumBy(members, EmployeeVacationAggregationQueryDto::getSpecialAvailable);

        int avgRate = (count == 0) ? 0
                : members.stream().mapToInt(EmployeeVacationAggregationQueryDto::usageRatePercent).sum() / count;
        long lowCount = members.stream().filter(m -> m.usageRatePercent() < threshold).count();

        return DepartmentVacationSummaryResponseDto.builder()
                .deptId(dept.getDeptId())
                .deptName(dept.getDeptName())
                .memberCount(count)
                .totalDays(total)
                .usedDays(used)
                .availableDays(stat.add(spec))
                .avgUsageRate(avgRate)
                .lowUsageCount((int) lowCount)
                .build();
    }

    /* 사원 row 매핑 - 집계/조정/사원메타/balance 기간 결합 */
    /* 발생 = 총연차 - 조정. 근속연수 = 입사일 ~ 오늘 만년(0부터) */
    private DepartmentMemberVacationResponseDto toMemberDto(
            EmployeeVacationAggregationQueryDto agg,
            Map<Long, Employee> empMap,
            Map<Long, BigDecimal> adjustMap,
            Map<Long, BalanceExpiryQueryDto> expiryMap,
            VacationType type,
            LocalDate today) {
        Employee emp = empMap.get(agg.getEmpId());
        BigDecimal adjusted = adjustMap.getOrDefault(agg.getEmpId(), BigDecimal.ZERO);
        BigDecimal accrued = agg.getTotalDays().subtract(adjusted);

        int serviceYears = (emp != null && emp.getEmpHireDate() != null)
                ? (int) ChronoUnit.YEARS.between(emp.getEmpHireDate(), today) : 0;

        BalanceExpiryQueryDto expiry = expiryMap.get(agg.getEmpId());

        return DepartmentMemberVacationResponseDto.builder()
                .empId(agg.getEmpId())
                .empName(emp != null ? emp.getEmpName() : null)
                .empGrade(emp != null && emp.getGrade() != null ? emp.getGrade().getGradeName() : null)
                .deptName(emp != null && emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .empHireDate(emp != null ? emp.getEmpHireDate() : null)
                .serviceYears(serviceYears)
                .statutoryTypeCode(type.getTypeCode())
                .statutoryTypeName(type.getTypeName())
                .periodStart(expiry != null ? expiry.getGrantedAt() : null)
                .periodEnd(expiry != null ? expiry.getExpiresAt() : null)
                .statutoryAvailable(agg.getStatutoryAvailable())
                .specialAvailable(agg.getSpecialAvailable())
                .usedDays(agg.getUsedDays())
                .totalDays(agg.getTotalDays())
                .accruedDays(accrued)
                .adjustedDays(adjusted)
                .usageRate(agg.usageRatePercent())
                .build();
    }

    /* BigDecimal 필드 추출 + 합계 - 빈 리스트면 0 */
    private BigDecimal sumBy(List<EmployeeVacationAggregationQueryDto> list,
                             Function<EmployeeVacationAggregationQueryDto, BigDecimal> getter) {
        return list.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
