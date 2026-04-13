package com.peoplecore.employee.service;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.dto.*;
import com.peoplecore.employee.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional
public class HrStatusService {

    private final EmployeeRepository employeeRepository;

    @Autowired
    public HrStatusService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    //  1. 카드 합계
    public WorkforceSummaryDto getSummary(UUID companyId) {
        LocalDate now = LocalDate.now();

        int total = employeeRepository.countByCompany_CompanyIdAndEmpStatusNot(companyId, EmpStatus.RESIGNED);
        int hiredThisMonth = employeeRepository.countHiredThisMonth(companyId, now.getYear(), now.getMonthValue());
        int resignedThisMonth = employeeRepository.countResignedThisMonth(companyId, now.getYear(), now.getMonthValue());
//        계약만료 30일 이내의 인원
        int contractExpiring = employeeRepository.countExpiringContracts(companyId, now, now.plusDays(30));

        return WorkforceSummaryDto.builder()
                .total(total)
                .hiredThisMonth(hiredThisMonth)
                .resignedThisMonth(resignedThisMonth)
                .contractExpiring(contractExpiring)
                .build();

    }

    //    2.부서별 인원, 직급별 분포, 평균 재직연수
    public List<DeptWorkforceDto> getByDept(UUID companyId, Long deptId) {
        LocalDate now = LocalDate.now();
        List<Employee> activeEmployees;
        if (deptId != null) {
            activeEmployees = employeeRepository.findActiveByCompanyAndDept(companyId, deptId);
        } else {
            activeEmployees = employeeRepository.findActiveEmployeesWithDeptAndGrade(companyId);
        }

//    부서별 사원분류
        Map<String, List<Employee>> deptEmployees = new LinkedHashMap<>();
        for (Employee emp : activeEmployees) {
            String deptName = emp.getDept().getDeptName();
            if (!deptEmployees.containsKey(deptName)) {
                deptEmployees.put(deptName, new ArrayList<>());
            }
            deptEmployees.get(deptName).add(emp);
        }

//        부서별 결과 조립
        List<DeptWorkforceDto> result = new ArrayList<>();

        for (Map.Entry<String, List<Employee>> entry : deptEmployees.entrySet()) {
            String deptName = entry.getKey();
            List<Employee> employees = entry.getValue();

//        직급별 인원 집계
            Map<String, Integer> gradeCountMap = new LinkedHashMap<>();
            for (Employee emp : employees) {
                String gradeName = emp.getGrade().getGradeName();
                gradeCountMap.put(gradeName, gradeCountMap.getOrDefault(gradeName, 0) + 1);
            }
//      응답용
            List<GradeCountDto> gradeCounts = new ArrayList<>();
            for (Map.Entry<String, Integer> gc : gradeCountMap.entrySet()) {
                gradeCounts.add(new GradeCountDto(gc.getKey(), gc.getValue()));
            }

//        평균 재직년수 년/월
            long totalDays = 0;
            for (Employee emp : employees) {
                totalDays += ChronoUnit.DAYS.between(emp.getEmpHireDate(), now);
            }
            long avgDays = totalDays / employees.size();
            int aygYears = (int) (avgDays / 365);
            int avgMonths = (int) ((avgDays % 365) / 30);

            result.add(DeptWorkforceDto.builder()
                    .deptName(deptName)
                    .total(employees.size())
                    .gradeCounts(gradeCounts)
                    .avgYears(aygYears)
                    .avgMonths(avgMonths)
                    .build());
        }

        return result;
    }

    //    3.월별 입퇴사 (6개월)
    public List<MonthlyTrendDto> getTrend(UUID companyId) {
        LocalDate from = LocalDate.now().minusMonths(5).withDayOfMonth(1);

//       6개월 이내 입/퇴사자 전체 조회
        List<Employee> hiredList = employeeRepository.findHiredAfter(companyId, from);
        List<Employee> resignedList = employeeRepository.findResignedAfter(companyId, from);

        Map<YearMonth, Integer> hiredMap = new HashMap<>();
        for (Employee emp : hiredList) {
            YearMonth ym = YearMonth.from(emp.getEmpHireDate());
//            해당 년월 카운트 있으면 +1, 없으면 0
            hiredMap.put(ym, hiredMap.getOrDefault(ym, 0) + 1);
        }

        Map<YearMonth, Integer> resignedMap = new HashMap<>();
        for (Employee emp : resignedList) {
            YearMonth ym = YearMonth.from(emp.getEmpResignDate());
            resignedMap.put(ym, resignedMap.getOrDefault(ym, 0) + 1);
        }
//        6개월 구간 1개월씩 순회
        List<MonthlyTrendDto> result = new ArrayList<>();
//        순회 시작점
        YearMonth current = YearMonth.from(from);
//        순환 종료(현재)
        YearMonth end = YearMonth.now();

//        current가 end를 넘지 않는 동안 반복
        while (!current.isAfter(end)) {
            result.add(MonthlyTrendDto.builder()
                    .month(current)
                    .hired(hiredMap.getOrDefault(current, 0))
                    .resigned(resignedMap.getOrDefault(current, 0))
                    .build());
            current =current.plusMonths(1);
        }
        return result;
    }

//    4.계약 만료 예정자
    public List<ExpiringContractDto>getExpiring(UUID companyId){
        LocalDate now = LocalDate.now();

//        오늘부터 30일 이내 계약 만료 예정자 조회
        List<Employee>employees =employeeRepository.findExpiringContracts(companyId,now,now.plusDays(30));

        List<ExpiringContractDto>result =new ArrayList<>();
        for(Employee e : employees){
            result.add(ExpiringContractDto.builder()
                            .empNum(e.getEmpNum())
                            .empName(e.getEmpName())
                            .deptName(e.getDept().getDeptName())
                            .empType(e.getEmpType())
                            .expiryDate(e.getContractEndDate())
                            .daysLeft((int)ChronoUnit.DAYS.between(now,e.getContractEndDate()))
                    .build());
        }
        return result;


    }


}

