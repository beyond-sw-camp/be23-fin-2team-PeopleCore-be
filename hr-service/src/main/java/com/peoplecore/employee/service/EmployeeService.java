package com.peoplecore.employee.service;

import com.peoplecore.attendence.entity.WorkGroup;
import com.peoplecore.attendence.repository.WorkGroupRepository;
import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.department.domain.Department;
import com.peoplecore.department.repository.DepartmentRepository;
import com.peoplecore.employee.domain.*;
import com.peoplecore.employee.dto.EmpDetailResponseDto;
import com.peoplecore.employee.dto.EmployeeCreateRequestDto;
import com.peoplecore.employee.dto.EmployeeCardResponseDto;
import com.peoplecore.employee.dto.EmployeeListDto;
import com.peoplecore.employee.dto.EmployeeUpdateRequestDto;
import com.peoplecore.employee.repository.EmployeeFileRepository;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.BusinessException;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.grade.domain.Grade;
import com.peoplecore.grade.repository.GradeRepository;
import com.peoplecore.minio.service.MinioService;
import com.peoplecore.title.domain.Title;
import com.peoplecore.title.repository.TitleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Optional;
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
    private final WorkGroupRepository workGroupRepository;

    public static final String DEFAULT_CODE = "DEFAULT";

    @Autowired
    public EmployeeService(EmployeeRepository employeeRepository, CompanyRepository companyRepository, DepartmentRepository departmentRepository, GradeRepository gradeRepository, TitleRepository titleRepository, PasswordEncoder passwordEncoder, MinioService minioService, EmployeeFileRepository employeeFileRepository, WorkGroupRepository workGroupRepository) {
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.departmentRepository = departmentRepository;
        this.gradeRepository = gradeRepository;
        this.titleRepository = titleRepository;
        this.passwordEncoder = passwordEncoder;
        this.minioService = minioService;
        this.employeeFileRepository = employeeFileRepository;
        this.workGroupRepository = workGroupRepository;
    }

    private static final String EMAIL_DOMAIN = "@peoplecore.com";

    //    1.사원조회 및 등록
    public Page<EmployeeListDto> getEmployee(UUID companyId, String keyword, Long deptId, EmpType empType, EmpStatus empStatus, EmployeeSortField employeeSortField, Pageable pageable) {
        Page<Employee> employees = employeeRepository.findAllWithFilter(companyId, keyword, deptId, empType, empStatus, employeeSortField, pageable);
        return employees.map(EmployeeListDto::fromEntity);
    }


    //    2.카드 조회 및 합계
    public EmployeeCardResponseDto getCard(UUID companyId) {
//        현재 날짜(비교용)
        LocalDate now = LocalDate.now();

        int total = employeeRepository.countByCompany_CompanyIdAndEmpStatusNot(companyId, EmpStatus.RESIGNED); //재직자 수: 퇴직자 제외

        int active = employeeRepository.countByCompany_CompanyIdAndEmpStatus(companyId, EmpStatus.ACTIVE);

        int onLeave = employeeRepository.countByCompany_CompanyIdAndEmpStatus(companyId, EmpStatus.ON_LEAVE);

        int hiredThisMonth = employeeRepository.countHiredThisMonth(companyId, now.getYear(), now.getMonthValue());

        return EmployeeCardResponseDto.builder()
                .total(total)
                .active(active)
                .onLeave(onLeave)
                .hiredThisMonth(hiredThisMonth)
                .build();


        //재직자 수: 퇴직자 제외


    }

    //    사원등록
    public Long createEmployee(UUID companyId, EmployeeCreateRequestDto requestDto, List<MultipartFile> files) {

//        연관 entity조회
        Company company = companyRepository.getReferenceById(companyId);

        Department dept = departmentRepository.findByDeptName(requestDto.getDeptName()).orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND.getMessage(), HttpStatus.NOT_FOUND));

        Grade grade = gradeRepository.findByGradeName(requestDto.getGradeName()).orElseThrow(() -> new BusinessException(ErrorCode.GRADE_NOT_FOUND.getMessage(), HttpStatus.NOT_FOUND));

        Title title = titleRepository.findByTitleName(requestDto.getTitleName()).orElseThrow(() -> new BusinessException(ErrorCode.TITLE_NOT_FOUND.getMessage(), HttpStatus.NOT_FOUND));

        String empNum = generateEmpNum(companyId, requestDto.getEmpHireDate());

        String fullEmail = empNum + EMAIL_DOMAIN;

        String rawPassword = resolvePassword(requestDto);


        /* 회사 기본 근무 그룹 조회 */
        WorkGroup workGroup = workGroupRepository
                .findByWorkGroupIdAndGroupDeleteAtIsNull(requestDto.getWorkGroupId())
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_GROUP_NOT_FOUND));


//        사원 저장
        Employee employee = Employee.builder()
                .company(company)
                .dept(dept)
                .grade(grade)
                .title(title)
                .empName(requestDto.getEmpName())
                .empNameEn(requestDto.getEmpNameEn())
                .empBirthDate(requestDto.getEmpBirthDate())
                .empGender(requestDto.getEmpGender())
                .empPhone(requestDto.getEmpPhone())
                .empPersonalEmail(requestDto.getEmpPersonalEmail())
                .empZipCode(requestDto.getEmpZipCode())
                .empAddressBase(requestDto.getEmpAddressBase())
                .empAddressDetail(requestDto.getEmpAddressDetail())
                .empHireDate(requestDto.getEmpHireDate())
                .empType(requestDto.getEmpType())
                .empNum(empNum)
                .empEmail(fullEmail)
                .empRole(requestDto.getEmpRole())
                .empPassword(passwordEncoder.encode(rawPassword))
                .empMailboxSize(requestDto.getEmpMailboxSize()) //사용. 5gb고정 하드 코딩// 커스��� 고려
                .empStatus(EmpStatus.ACTIVE)
                .workGroup(workGroup)
                .build();

        Employee savedEmployee = employeeRepository.save(employee);

//        파일 minio업로드
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                try {
                    String storedFilePath = minioService.uploadFile(file, "employee-docs");
                    employeeFileRepository.save(EmployeeFile.builder()
                            .employee(savedEmployee)
                            .originalFileName(file.getOriginalFilename())
                            .storedFilePath(storedFilePath)
                            .contentType(file.getContentType())
                            .fileSize(file.getSize())
                            .build());

                } catch (Exception e) {
                    throw new BusinessException("파일 업로드에 실패했습니다", HttpStatus.BAD_REQUEST);
                }
            }
        }


        return savedEmployee.getEmpId();
    }

    //        사번생성: ex. YYYYMM-0001
    private String generateEmpNum(UUID companyId, LocalDate hireDate) {
        String prefix = hireDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
//        비관적 락, 가장 큰 사번 조회
        Optional<String>maxEmpNum = employeeRepository.findMaxEmpNumWithLock(companyId,prefix);
//        다음 순번 계산
        long nextSeq;
        if(maxEmpNum.isPresent()){
            nextSeq = Long.parseLong(maxEmpNum.get().split("-")[1])+1;
        }else{
            nextSeq = 1L;
        }
        return String.format("%s-%04d", prefix, nextSeq);
    }

    //    비밀번호 생성 / 분기 처리
    private String resolvePassword(EmployeeCreateRequestDto requestDto) {
        if (requestDto.getPasswordIssueType() == PasswordIssueType.MANUAL) {
            if (requestDto.getInitialPassword() == null || requestDto.getInitialPassword().isBlank()) {
                throw new BusinessException(ErrorCode.MANUAL_PASSWORD_REQUIRED.getMessage(), HttpStatus.BAD_REQUEST);
            }
            validatePassword(requestDto.getInitialPassword());
            return requestDto.getInitialPassword();
        }
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(chars.charAt((int) (Math.random() * chars.length())));
        return sb.toString();
    }

//    비밀번호 직접 생성  //직접생성 굳이?? ->사원 재설정이 더 나은거 같은데

    public void validatePassword(String password) {
        if (password.length() < 8) {
            throw new BusinessException("비밀번호는 최소 8자리 이상이어야 합니다", HttpStatus.BAD_REQUEST);
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new BusinessException("비밀번호는 영문대문자를 포함해야합니다", HttpStatus.BAD_REQUEST);
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


    //    4. 사원 상세조회
    @Transactional(readOnly = true)
    public EmpDetailResponseDto getEmployeeDetail(UUID companyId, Long empId) {

        Employee employee = employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId).orElseThrow(() -> new EntityNotFoundException("사원을 찾을 수 없습니다"));
        return EmpDetailResponseDto.from(employee);

    }

    //    5. 사원 정보 수정
    public EmpDetailResponseDto updateEmployee(UUID companyId, Long empId, EmployeeUpdateRequestDto requestDto) {

        Employee employee = employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new EntityNotFoundException("사원을 찾을 수 없습니다"));

        Department dept = departmentRepository.findByDeptName(requestDto.getDeptName())
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND.getMessage(), HttpStatus.NOT_FOUND));

        Grade grade = gradeRepository.findByGradeName(requestDto.getGradeName())
                .orElseThrow(() -> new BusinessException(ErrorCode.GRADE_NOT_FOUND.getMessage(), HttpStatus.NOT_FOUND));

        Title title = titleRepository.findByTitleName(requestDto.getTitleName())
                .orElseThrow(() -> new BusinessException(ErrorCode.TITLE_NOT_FOUND.getMessage(), HttpStatus.NOT_FOUND));

        employee.updateInfo(
                requestDto.getEmpName(),
                requestDto.getEmpNameEn(),
                requestDto.getEmpBirthDate(),
                requestDto.getEmpGender(),
                requestDto.getEmpPhone(),
                requestDto.getEmpPersonalEmail(),
                requestDto.getEmpZipCode(),
                requestDto.getEmpAddressBase(),
                requestDto.getEmpAddressDetail(),
                requestDto.getEmpHireDate(),
                requestDto.getEmpType(),
                dept,
                grade,
                title,
                requestDto.getEmpRole(),
                requestDto.getEmpMailboxSize()
        );

        return EmpDetailResponseDto.from(employee);
    }

    //    6.사원 삭제
    public void deleteEmployee(UUID companyId, Long empId) {
        Employee employee = employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId).orElseThrow(() -> new EntityNotFoundException("사원을 찾을 수 없습니다"));
        employee.softDelete();
    }

}
