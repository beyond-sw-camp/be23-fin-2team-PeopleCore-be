package com.peoplecore.employee.service;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.EmpType;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.domain.EmployeeSortField;
import com.peoplecore.employee.dto.EmployeeKardResponseDto;
import com.peoplecore.employee.dto.EmployeeListDto;
import com.peoplecore.employee.repository.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

//    1.사원조회 및 등록
    public Page<EmployeeListDto>getEmployee(String keyword, Long deptId, EmpType empType, EmpStatus empStatus, EmployeeSortField employeeSortField, Pageable pageable){
        Page<Employee>employees = employeeRepository.findAllwithFilter(keyword,deptId,empType,empStatus,employeeSortField,pageable);
        return employees.map(EmployeeListDto::fromEntity);
    }


//    2.카드 조회 및 합계
    public EmployeeKardResponseDto getKard(UUID companyId){
//        현재 날짜(비교용)
        LocalDate now = LocalDate.now();

        long total = employeeRepository.countByCompany_CompanyIdAndEmpStatusNot(companyId, EmpStatus.RESIGNED); //재직자 수: 퇴직자 제외

        long active = employeeRepository.countByCompany_CompanyIdAndEmpStatus(companyId, EmpStatus.ACTIVE);

        long onLeave = employeeRepository.countByCompany_CompanyIdAndEmpStatus(companyId, EmpStatus.ON_LEAVE);

        long hiredThisMonth = employeeRepository.countHiredThisMonth(companyId, now.getYear(), now.getMonthValue());

        return EmployeeKardResponseDto.builder()
                .total(total)
                .active(active)
                .onLeave(onLeave)
                .hiredThisMonth(hiredThisMonth)
                .build();


                //재직자 수: 퇴직자 제외



    }


}
