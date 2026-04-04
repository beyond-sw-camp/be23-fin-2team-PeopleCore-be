package com.peoplecore.employee.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.department.domain.Department;
import com.peoplecore.department.repository.DepartmentRepository;
import com.peoplecore.employee.domain.*;
import com.peoplecore.employee.dto.EmployeeCreateRequestDto;
import com.peoplecore.employee.dto.EmployeeKardResponseDto;
import com.peoplecore.employee.dto.EmployeeListDto;
import com.peoplecore.employee.repository.EmployeeFileRepository;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.BusinessException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.grade.domain.Grade;
import com.peoplecore.grade.repository.GradeRepository;
import com.peoplecore.minio.service.MinioService;
import com.peoplecore.permission.domain.Permission;
import com.peoplecore.title.domain.Title;
import com.peoplecore.title.repository.TitleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final GradeRepository gradeRepository;
    private final TitleRepository titleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MinioService minioService;
    private final EmployeeFileRepository employeeFileRepository;


    public EmployeeService(EmployeeRepository employeeRepository, CompanyRepository companyRepository, DepartmentRepository departmentRepository, GradeRepository gradeRepository, TitleRepository titleRepository, PasswordEncoder passwordEncoder, MinioService minioService, EmployeeFileRepository employeeFileRepository) {
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.departmentRepository = departmentRepository;
        this.gradeRepository = gradeRepository;
        this.titleRepository = titleRepository;
        this.passwordEncoder = passwordEncoder;
        this.minioService = minioService;
        this.employeeFileRepository = employeeFileRepository;
    }

    private static final String EMAIL_DOMAIN = "@peoplecore.com";

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

//    사원등록
    public Long createEmployee(UUID companyId, EmployeeCreateRequestDto responseDto, List<MultipartFile> files){

//        연관 entity조회
        Company company = companyRepository.getReferenceById(companyId);

        Department dept = departmentRepository.findByDeptName(responseDto.getDeptname()).orElseThrow(()->new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND.getMessage(),HttpStatus.NOT_FOUND));

        Grade grade = gradeRepository.findByGradeName(responseDto.getGradename()).orElseThrow(()-> new BusinessException(ErrorCode.GRADE_NOT_FOUND.getMessage(),HttpStatus.NOT_FOUND));

        Title title = titleRepository.findByTitleName(responseDto.getTitleName()).orElseThrow(()->new BusinessException(ErrorCode.TITLE_NOT_FOUND.getMessage(),HttpStatus.NOT_FOUND));

        String empNum = generateEmpNum(companyId, responseDto.getEmpHireDate());

        String fullEmail =empNum + EMAIL_DOMAIN;

        String rawPassword = resolvePassword(responseDto);

//        사원 저장
        Employee employee = Employee.builder()
                .company(company)
                .dept(dept)
                .grade(grade)
                .title(title)
                .empName(responseDto.getEmpName())
                .empNameEn(responseDto.getEmpNameEn())
                .empBirthDate(responseDto.getEmpBirthDate())
                .empGender(responseDto.getEmpGender())
                .empPhone(responseDto.getEmpPhone())
                .empPersonalEmail(responseDto.getEmpPersonalEmail())
                .empZipCode(responseDto.getEmpZipCode())
                .empAddressBase(responseDto.getEmpAddressBase())
                .empAddressDetail(responseDto.getEmpAddressDetail())
                .empHireDate(responseDto.getEmpHireDate())
                .empType(responseDto.getEmpType())
                .empNum(empNum)
                .empEmail(fullEmail)
                .empRole(responseDto.getEmpRole())
                .empPassword(passwordEncoder.encode(rawPassword))
                .empMailboxSize(responseDto.getEmpMailboxSize()) //사용. 5gb고정 하드 코딩// 커스텀 고려
                .empStatus(EmpStatus.ACTIVE)
                .build();

        Employee savedEmployee = employeeRepository.save(employee);

//        파일 minio업로드
        if(files != null && !files.isEmpty()){
            for(MultipartFile file : files){
                try{
                    String storedFilePath = minioService.uploadFile(file, "employee-docs");
                    employeeFileRepository.save(EmployeeFile.builder()
                            .employee(savedEmployee)
                            .originalFileName(file.getOriginalFilename())
                            .storedFilePath(storedFilePath)
                            .contentType(file.getContentType())
                            .fileSize(file.getSize())
                            .build());

                }catch (Exception e){
                    throw new BusinessException("파일 업로들에 실패했습니다",HttpStatus.BAD_REQUEST);
                }
            }
        }


        return savedEmployee.getEmpId();
        }

//        사번생성: ex. YYYYMM-0001
        private String generateEmpNum(UUID commpanyId, LocalDate hireDate){
        String prefix =hireDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
        long count = employeeRepository.countByCompanyIdAndEmpNumStartingWith(commpanyId,prefix);
        String empNum = String.format("%s-%04d",prefix,count+1);
        if(employeeRepository.existsByCompany_CompanyIdAndEmpNum(commpanyId,empNum)){
            empNum = String.format("%s-%04d",prefix,count+2);
        }
        return empNum;
    }

//    비밀번호 생성 / 분기 처리
    private String resolvePassword(EmployeeCreateRequestDto requestDto){
        if(requestDto.getPasswordIssueType()== PasswordIssueType.MANUAL){
            if(requestDto.getInitialPassword()==null || requestDto.getInitialPassword().isBlank()){
                throw new BusinessException(ErrorCode.MANUAL_PASSWORD_REQUIRED.getMessage(),HttpStatus.BAD_REQUEST);
            }
            validatePassword(requestDto.getInitialPassword());
            return requestDto.getInitialPassword();
        }
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i<12; i++) sb.append(chars.charAt((int)(Math.random() * chars.length())));
        return sb.toString();
    }

//    비밀번호 직접 생성  //직접생성 굳이?? ->사원 재설정이 더 나은거 같은데

    public void validatePassword(String password){
        if(password.length()<8){
            throw new BusinessException("비밀번호는 최소 8자리 이상이어야 합니다",HttpStatus.BAD_REQUEST);
        }
        if(!password.matches(".*[A-Z].*")){
            throw new BusinessException("비밀번호는 영문대문자를 포함해야합니다",HttpStatus.BAD_REQUEST);
        }
        if (!password.matches(".*[a-z].*")) {
            throw new BusinessException("비밀번호는 영문 소문자를 포함해야 합니다.", HttpStatus.BAD_REQUEST);
        }
        if (!password.matches(".*[0-9].*")) {
            throw new BusinessException("비밀번호는 숫자를 포함해야 합니다.", HttpStatus.BAD_REQUEST);
        }
        if (!password.matches(".*[!@#$%^&*()].*")) {
            throw new BusinessException("비밀번호는 특수문자를 포함해야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

}
