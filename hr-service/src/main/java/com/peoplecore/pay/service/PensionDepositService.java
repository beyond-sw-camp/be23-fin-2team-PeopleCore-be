package com.peoplecore.pay.service;

import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.RetirementPensionDeposits;
import com.peoplecore.pay.dtos.PensionDepositCreateReqDto;
import com.peoplecore.pay.dtos.PensionDepositEmployeeResDto;
import com.peoplecore.pay.dtos.PensionDepositResDto;
import com.peoplecore.pay.dtos.PensionDepositSummaryResDto;
import com.peoplecore.pay.enums.DepStatus;
import com.peoplecore.pay.enums.RetirementType;
import com.peoplecore.pay.repository.RetirementPensionDepositsRepository;
import com.peoplecore.pay.repository.SeverancePaysRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@Slf4j
public class PensionDepositService {

    private final RetirementPensionDepositsRepository retirementPensionDepositsRepository;
    private final EmployeeRepository employeeRepository;
    private final SeverancePaysRepository severancePaysRepository;
    private final CompanyRepository companyRepository;
    private final SeveranceEstimateService severanceEstimateService;

    @Autowired
    public PensionDepositService(RetirementPensionDepositsRepository retirementPensionDepositsRepository, EmployeeRepository employeeRepository, SeverancePaysRepository severancePaysRepository, CompanyRepository companyRepository, SeveranceEstimateService severanceEstimateService) {
        this.retirementPensionDepositsRepository = retirementPensionDepositsRepository;
        this.employeeRepository = employeeRepository;
        this.severancePaysRepository = severancePaysRepository;
        this.companyRepository = companyRepository;
        this.severanceEstimateService = severanceEstimateService;
    }

//    1. 목록조회
    public PensionDepositSummaryResDto getDepositList(
            UUID companyId, String fromYm, String toYm, Long empId, Long deptId, DepStatus status, Pageable pageable){

        Page<PensionDepositResDto> deposits = retirementPensionDepositsRepository.search(companyId,fromYm,toYm,empId,deptId,status,pageable);

        Integer totalEmployees = retirementPensionDepositsRepository.countDistinctEmployees(companyId, fromYm, toYm, status);
        Long totalDepositAmount = retirementPensionDepositsRepository.sumDepositAmount(companyId, fromYm, toYm, status);
        Long grandTotalDeposited = retirementPensionDepositsRepository.grandTotalDeposited(companyId);

//        월 수, 평균적립액 계산
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

//    2. 사원별 이력조회
    public PensionDepositEmployeeResDto getEmployeeDeposits(UUID companyId, Long empId, String fromYm, String toYm){

        Employee emp = employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        List<PensionDepositResDto> deposits = retirementPensionDepositsRepository.findByEmpId(companyId, empId, fromYm, toYm);
        Long totalDeposited = severancePaysRepository.sumDcDepositedTotal(empId, companyId) ;

        return PensionDepositEmployeeResDto.builder()
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .retirementType(emp.getRetirementType() != null ? emp.getRetirementType().name() : null)
                .totalDeposited(totalDeposited)
                .deposits(deposits)
                .build();
    }


//  3. 수동 적립 등록
    @Transactional
    public PensionDepositResDto createManualDeposit(UUID companyId, Long adminEmpId, PensionDepositCreateReqDto req) {
        Employee emp = employeeRepository.findByEmpIdAndCompany_CompanyId(req.getEmpId(), companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // DC형 검증
        if (emp.getRetirementType() != RetirementType.DC) {
            throw new CustomException(ErrorCode.EMPLOYEE_NOT_DC);
        }

        // 중복 체크: 같은 사원·같은 월·COMPLETED 상태 이미 있으면 거부
        if (retirementPensionDepositsRepository.existsByEmployee_EmpIdAndPayYearMonthAndDepStatus(
                req.getEmpId(), req.getPayYearMonth(), DepStatus.COMPLETED)) {
            throw new CustomException(ErrorCode.DEPOSIT_ALREADY_EXISTS);
        }

        RetirementPensionDeposits deposit = RetirementPensionDeposits.builder()
                .employee(emp)
                .baseAmount(req.getBaseAmount())
                .depositAmount(req.getDepositAmount())
                .payYearMonth(req.getPayYearMonth())
                .depositDate(LocalDateTime.now())
                .depStatus(req.getDepStatus())
                .company(companyRepository.getReferenceById(companyId))
                .payrollRun(null)   // 수동적립은 null
                .isManual(true)
                .reason(req.getReason())
                .createdBy(adminEmpId)
                .build();

        RetirementPensionDeposits saved = retirementPensionDepositsRepository.save(deposit);


        log.info("[PensionDeposit 수동등록] depId={}, empId={}, payYearMonth={}, amount={}, by={}",
                saved.getDepId(), req.getEmpId(), req.getPayYearMonth(), req.getDepositAmount(), adminEmpId);

        return toRes(saved, emp);


    }



    private long calcMonthsBetween(String fromYm, String toYm) {
        if (fromYm == null || toYm == null) return 1;
        java.time.YearMonth from = java.time.YearMonth.parse(fromYm);
        java.time.YearMonth to = java.time.YearMonth.parse(toYm);
        return java.time.temporal.ChronoUnit.MONTHS.between(from, to) + 1;
    }

    private PensionDepositResDto toRes(RetirementPensionDeposits d, Employee emp) {
        return PensionDepositResDto.builder()
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
    }

