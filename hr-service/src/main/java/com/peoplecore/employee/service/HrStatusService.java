package com.peoplecore.employee.service;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.dto.DeptWorkforceDto;
import com.peoplecore.employee.dto.GradeCountDto;
import com.peoplecore.employee.dto.WorkforceSummaryDto;
import com.peoplecore.employee.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
        int contractExpiring = employeeRepository.findExpiringContracts(companyId, now, now.plusDays(30)).size();

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
        if(deptId !=null){
            activeEmployees =employeeRepository.findActiveByCompanyAndDept(companyId,deptId);
        }else{
            activeEmployees =employeeRepository.findActiveEmployeesWithDeptAndGrade(companyId);
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

}

