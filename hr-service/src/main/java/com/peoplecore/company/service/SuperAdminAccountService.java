package com.peoplecore.company.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.dtos.CompanyCreateReqDto;
import com.peoplecore.department.domain.Department;
import com.peoplecore.department.repository.DepartmentRepository;
import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.EmpType;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.grade.domain.Grade;
import com.peoplecore.grade.repository.GradeRepository;
import com.peoplecore.pay.domain.InsuranceJobTypes;
import com.peoplecore.pay.repository.InsuranceJobTypesRepository;
import com.peoplecore.title.domain.Title;
import com.peoplecore.title.repository.TitleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@Transactional(readOnly = true)
public class SuperAdminAccountService {

    @Value("${admin.temp-password}")
    private String tempPassword;


    private final DepartmentRepository departmentRepository;
    private final GradeRepository gradeRepository;
    private final TitleRepository titleRepository;
    private final InsuranceJobTypesRepository insuranceJobTypesRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeRepository employeeRepository;

    @Autowired
    public SuperAdminAccountService(DepartmentRepository departmentRepository, GradeRepository gradeRepository, TitleRepository titleRepository, InsuranceJobTypesRepository insuranceJobTypesRepository, PasswordEncoder passwordEncoder, EmployeeRepository employeeRepository) {
        this.departmentRepository = departmentRepository;
        this.gradeRepository = gradeRepository;
        this.titleRepository = titleRepository;
        this.insuranceJobTypesRepository = insuranceJobTypesRepository;
        this.passwordEncoder = passwordEncoder;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public void createSuperAdmin(Company company, CompanyCreateReqDto reqDto){

        Department defaultDepartment = departmentRepository.findByCompany_CompanyIdAndDeptName(company.getCompanyId(), "미배정").orElseThrow(()-> new CustomException(ErrorCode.DEPARTMENT_NOT_FOUND) );
        Grade defaultGrade = gradeRepository.findByCompanyIdAndGradeName(company.getCompanyId(), "미배정").orElseThrow(()-> new CustomException(ErrorCode.GRADE_NOT_FOUND));
        Title defaultTitle = titleRepository.findByCompanyIdAndTitleName(company.getCompanyId(), "미배정").orElseThrow(()-> new CustomException(ErrorCode.TITLE_NOT_FOUND));
        InsuranceJobTypes defaultJobType = insuranceJobTypesRepository.findByCompany_CompanyIdAndName(company.getCompanyId(),"기본업종").orElseThrow(()-> new CustomException(ErrorCode.INSURANCE_JOB_TYPE_NOT_FOUND));

        Employee superAdmin = Employee.builder()
                .company(company)
                .dept(defaultDepartment)
                .grade(defaultGrade)
                .title(defaultTitle)
                .jobTypes(defaultJobType)
                .empName(reqDto.getAdminName())
                .empEmail(reqDto.getAdminEmail())
                .empPhone("000-0000-0000")
                .empNum("SUPERADMIN-001")
                .empHireDate(LocalDate.now())
                .empType(EmpType.FULL)
                .empStatus(EmpStatus.ACTIVE)
                .empRole(EmpRole.HR_SUPER_ADMIN)
                .empPassword(passwordEncoder.encode(tempPassword))
                .workGroupId(1L)
                .mustChangePassword(true)
                .build();

        employeeRepository.save(superAdmin);

        log.info("SuperAdmin 계정생성 완료 - 회사 : {}, 이메일 : {}, 임시비밀번호 : {}", company.getCompanyName(), reqDto.getAdminEmail(), tempPassword);

//        회사id, 이메일,비밀번호 이메일 발송


    }

}
