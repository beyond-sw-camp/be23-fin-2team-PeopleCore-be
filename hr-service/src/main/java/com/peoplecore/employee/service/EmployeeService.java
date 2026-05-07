package com.peoplecore.employee.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.WorkGroupRepository;
import com.peoplecore.auth.service.FaceAuthService;
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
import com.peoplecore.pay.domain.EmpAccounts;
import com.peoplecore.pay.domain.EmpRetirementAccount;
import com.peoplecore.pay.domain.InsuranceJobTypes;
import com.peoplecore.pay.domain.RetirementSettings;
import com.peoplecore.pay.enums.PensionType;
import com.peoplecore.pay.enums.RetirementType;
import com.peoplecore.pay.repository.EmpAccountsRepository;
import com.peoplecore.pay.repository.EmpRetirementAccountRepository;
import com.peoplecore.pay.repository.InsuranceJobTypesRepository;
import com.peoplecore.pay.repository.RetirementSettingsRepository;
import com.peoplecore.pay.service.AccountVerifyService;
import com.peoplecore.title.domain.Title;
import com.peoplecore.title.repository.TitleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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
    private final FaceAuthService faceAuthService;
    private final InsuranceJobTypesRepository insuranceJobTypesRepository;
    private final AccountVerifyService accountVerifyService;
    private final EmpAccountsRepository empAccountsRepository;
    private final EmpRetirementAccountRepository empRetirementAccountRepository;
    private final RetirementSettingsRepository retirementSettingsRepository;
    private final ObjectMapper objectMapper;
    private final ProfileImageService profileImageService;

    public static final String DEFAULT_CODE = "DEFAULT";

    @Autowired
    public EmployeeService(EmployeeRepository employeeRepository, CompanyRepository companyRepository, DepartmentRepository departmentRepository, GradeRepository gradeRepository, TitleRepository titleRepository, PasswordEncoder passwordEncoder, MinioService minioService, EmployeeFileRepository employeeFileRepository, WorkGroupRepository workGroupRepository, FaceAuthService faceAuthService, InsuranceJobTypesRepository insuranceJobTypesRepository, AccountVerifyService accountVerifyService, EmpAccountsRepository empAccountsRepository, EmpRetirementAccountRepository empRetirementAccountRepository, RetirementSettingsRepository retirementSettingsRepository, ObjectMapper objectMapper, ProfileImageService profileImageService) {
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.departmentRepository = departmentRepository;
        this.gradeRepository = gradeRepository;
        this.titleRepository = titleRepository;
        this.passwordEncoder = passwordEncoder;
        this.minioService = minioService;
        this.employeeFileRepository = employeeFileRepository;
        this.workGroupRepository = workGroupRepository;
        this.faceAuthService = faceAuthService;
        this.insuranceJobTypesRepository = insuranceJobTypesRepository;
        this.accountVerifyService = accountVerifyService;
        this.empAccountsRepository = empAccountsRepository;
        this.empRetirementAccountRepository = empRetirementAccountRepository;
        this.retirementSettingsRepository = retirementSettingsRepository;
        this.objectMapper = objectMapper;
        this.profileImageService = profileImageService;
    }

    private static final String EMAIL_DOMAIN = "@peoplecore.com";

    //    1.사원조회 및 등록
    public Page<EmployeeListDto> getEmployee(UUID companyId, String keyword, Long deptId, EmpType empType, EmpStatus empStatus, EmployeeSortField employeeSortField, Sort.Direction sortDirection, Pageable pageable) {
        Page<Employee> employees = employeeRepository.findAllWithFilter(companyId, keyword, deptId, empType, empStatus, employeeSortField, sortDirection, pageable);
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
    public Long createEmployee(UUID companyId, EmployeeCreateRequestDto requestDto, List<MultipartFile> files, MultipartFile profileImage) {

//        연관 entity조회
        Company company = companyRepository.getReferenceById(companyId);

        // id + companyId로 테넌트 격리
        Department dept = departmentRepository.findByDeptIdAndCompany_CompanyId(requestDto.getDeptId(), companyId).orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND.getMessage(), HttpStatus.NOT_FOUND));

        Grade grade = gradeRepository.findByGradeIdAndCompanyId(requestDto.getGradeId(), companyId).orElseThrow(() -> new BusinessException(ErrorCode.GRADE_NOT_FOUND.getMessage(), HttpStatus.NOT_FOUND));

        Title title = titleRepository.findByTitleIdAndCompanyId(requestDto.getTitleId(), companyId).orElseThrow(() -> new BusinessException(ErrorCode.TITLE_NOT_FOUND.getMessage(), HttpStatus.NOT_FOUND));

        InsuranceJobTypes jobTypes = insuranceJobTypesRepository.findByCompany_CompanyIdAndJobTypeName(companyId, requestDto.getInsuranceJobTypeName()).orElseThrow(() -> new CustomException(ErrorCode.INSURANCE_JOB_TYPE_NOT_FOUND));

        String empNum = generateEmpNum(companyId, requestDto.getEmpHireDate());

        String fullEmail = requestDto.getEmpEmailLocal() + EMAIL_DOMAIN;

        if (employeeRepository.existsByCompany_CompanyIdAndEmpEmail(companyId, fullEmail)) {
            throw new BusinessException("이미 사용 중인 사내 이메일입니다.", HttpStatus.CONFLICT);
        }

        String rawPassword = resolvePassword(requestDto);


//        회사 근무 그룹 조회 - 미선택 시 기본 그룹(DEFAULT) 자동 배정
        WorkGroup workGroup;
        if (requestDto.getWorkGroupId() == null) {
            workGroup = workGroupRepository.findByCompany_CompanyIdAndGroupCodeAndGroupDeleteAtIsNull(companyId, "DEFAULT").orElseThrow(() -> new CustomException(ErrorCode.WORK_GROUP_NOT_FOUND));
        } else {
            workGroup = workGroupRepository.findByWorkGroupIdAndGroupDeleteAtIsNull(requestDto.getWorkGroupId()).orElseThrow(() -> new CustomException(ErrorCode.WORK_GROUP_NOT_FOUND));
        }


//        사원 저장
        Employee employee = Employee.builder()
                .company(company)
                .dept(dept)
                .grade(grade)
                .title(title)
                .jobTypes(jobTypes)
                .empName(requestDto.getEmpName())
                .empNameEn(requestDto.getEmpNameEn())
                .empBirthDate(requestDto.getEmpBirthDate())
                .empGender(requestDto.getEmpGender())
                .empPhone(requestDto.getEmpPhone())
                .empPersonalEmail(requestDto.getEmpPersonalEmail())
                .empZipCode(requestDto.getEmpZipCode())
                .empAddressBase(requestDto.getEmpAddressBase())
                .empAddressDetail(requestDto.getEmpAddressDetail())
                .empResidentNumber(requestDto.getEmpResidentNumber())
                .empHireDate(requestDto.getEmpHireDate())
                .empType(requestDto.getEmpType())
                .empNum(empNum)
                .empEmail(fullEmail)
                .empRole(requestDto.getEmpRole())
                .empPassword(passwordEncoder.encode(rawPassword))
                .empStatus(EmpStatus.ACTIVE)
                .workGroup(workGroup)
                .workGroupAssignedAt(LocalDateTime.now())
                .workGroup(workGroup)
                .workGroupAssignedAt(LocalDateTime.now())
                .dependentsCount(requestDto.getDependentsCount() != null ? requestDto.getDependentsCount() : 1)
                .build();

        Employee savedEmployee = employeeRepository.save(employee);


        if(profileImage != null && !profileImage.isEmpty()){
            try{
                String profileUrl = profileImageService.upload(savedEmployee.getEmpId(), profileImage);
                savedEmployee.updateProfileImage(profileUrl);
            } catch (Exception e) {
                throw new BusinessException("프로필 사진 업로드에 실패했습니다", HttpStatus.BAD_REQUEST);
            }
        }

//        커스텀 필드 저장 (HR이 폼 설정에서 추가한 동적 fieldKey 들의 값)
        if (requestDto.getCustomFieldsJson() != null && !requestDto.getCustomFieldsJson().isBlank()) {
            try {
                Map<String, String> customFields = objectMapper.readValue(
                        requestDto.getCustomFieldsJson(),
                        new TypeReference<Map<String, String>>() {}
                );
                savedEmployee.updateCustomFields(customFields);
            } catch (JsonProcessingException e) {
                throw new BusinessException("커스텀 필드 파싱 실패", HttpStatus.BAD_REQUEST);
            }
        }


//  급여계좌 (토큰 검증 + 저장)
        if (hasSalaryAccountInput(requestDto)) {
            EmpAccounts salaryAccount = EmpAccounts.builder()
                    .employee(savedEmployee)
                    .company(company)
                    .bankCode(requestDto.getSalaryBankCode())
                    .bankName(requestDto.getSalaryBankName())
                    .accountNumber(requestDto.getSalaryAccountNumber())
                    .accountHolder(requestDto.getSalaryAccountHolder())
                    .build();
            empAccountsRepository.save(salaryAccount);
        }

// 퇴직연금계좌 (회사 pensionType 분기)
        if (requestDto.getRetirementType() != null) {
            RetirementSettings settings = retirementSettingsRepository
                    .findByCompany_CompanyId(companyId)
                    .orElseThrow(() -> new CustomException(ErrorCode.RETIREMENT_SETTINGS_NOT_FOUND));

            PensionType companyType = settings.getPensionType();

            // 회사가 severance 또는 DB면 사원 계좌 입력 자체가 잘못됨
            if (companyType == PensionType.severance || companyType == PensionType.DB) {
                throw new CustomException(ErrorCode.RETIREMENT_TYPE_NOT_ALLOWED);
            }

            // DB_DC 병행이면 사원 retirementType이 DB/DC 둘 다 가능, 단일 DC면 DC만 가능
            if (companyType == PensionType.DC && requestDto.getRetirementType() != RetirementType.DC) {
                throw new CustomException(ErrorCode.RETIREMENT_TYPE_NOT_ALLOWED);
            }

            // DC 선택 시 계좌번호 필수
            if (requestDto.getRetirementType() == RetirementType.DC
                    && (requestDto.getRetirementAccountNumber() == null
                    || requestDto.getRetirementAccountNumber().isBlank())) {
                throw new CustomException(ErrorCode.RETIREMENT_ACCOUNT_REQUIRED);
            }

            EmpRetirementAccount retirementAccount = EmpRetirementAccount.builder()
                    .employee(savedEmployee)
                    .company(company)
                    .retirementType(requestDto.getRetirementType())
                    .pensionProvider(settings.getPensionProvider())   // 회사 운용사 사용
                    .accountNumber(
                            requestDto.getRetirementType() == RetirementType.DC
                                    ? requestDto.getRetirementAccountNumber()
                                    : ""    // DB는 회사 운용이라 계좌 번호 미사용 — 빈 문자열
                    )
                    .build();
            empRetirementAccountRepository.save(retirementAccount);
        }

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

    //        사번 미리보기: ex. 26040001 (yyMM + 4자리 순번) — 락 없이 다음 사번 계산
    @Transactional(readOnly = true)
    public String previewEmpNum(UUID companyId, LocalDate hireDate) {
        String prefix = hireDate.format(DateTimeFormatter.ofPattern("yyMM"));
        Optional<String> maxEmpNum = employeeRepository.findMaxEmpNum(companyId, prefix);
        long nextSeq;
        if (maxEmpNum.isPresent()) {
            nextSeq = Long.parseLong(maxEmpNum.get().substring(prefix.length())) + 1;
        } else {
            nextSeq = 1L;
        }
        return String.format("%s%04d", prefix, nextSeq);
    }

    //        사번생성: ex. 26040001 (yyMM + 4자리 순번)
    private String generateEmpNum(UUID companyId, LocalDate hireDate) {
        String prefix = hireDate.format(DateTimeFormatter.ofPattern("yyMM"));
//        비관적 락, 가장 큰 사번 조회
        Optional<String>maxEmpNum = employeeRepository.findMaxEmpNumWithLock(companyId,prefix);
//        다음 순번 계산
        long nextSeq;
        if(maxEmpNum.isPresent()){
            nextSeq = Long.parseLong(maxEmpNum.get().substring(prefix.length()))+1;
        }else{
            nextSeq = 1L;
        }
        return String.format("%s%04d", prefix, nextSeq);
    }

    //    비밀번호 검증 후 반환
    private String resolvePassword(EmployeeCreateRequestDto requestDto) {
        String pwd = requestDto.getInitialPassword();
        validatePassword(pwd);
        return pwd;
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
    public EmpDetailResponseDto updateEmployee(UUID companyId, Long empId,
                                               EmployeeUpdateRequestDto requestDto,
                                               MultipartFile profileImage) {

        Employee employee = employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId).orElseThrow(() -> new EntityNotFoundException("사원을 찾을 수 없습니다"));

        // id + companyId 로 단건 보장 + 테넌트 격리
        Department dept = departmentRepository.findByDeptIdAndCompany_CompanyId(requestDto.getDeptId(), companyId).orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND.getMessage(), HttpStatus.NOT_FOUND));

        Grade grade = gradeRepository.findByGradeIdAndCompanyId(requestDto.getGradeId(), companyId).orElseThrow(() -> new BusinessException(ErrorCode.GRADE_NOT_FOUND.getMessage(), HttpStatus.NOT_FOUND));

        Title title = titleRepository.findByTitleIdAndCompanyId(requestDto.getTitleId(), companyId).orElseThrow(() -> new BusinessException(ErrorCode.TITLE_NOT_FOUND.getMessage(), HttpStatus.NOT_FOUND));

        InsuranceJobTypes jobTypes = insuranceJobTypesRepository.findByCompany_CompanyIdAndJobTypeName(companyId, requestDto.getInsuranceJobTypeName()).orElseThrow(() -> new CustomException(ErrorCode.INSURANCE_JOB_TYPE_NOT_FOUND));

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
                requestDto.getEmpResidentNumber(),
                requestDto.getEmpHireDate(),
                requestDto.getEmpType(),
                dept,
                grade,
                title,
                requestDto.getEmpRole()
        );
        employee.updateInsuranceJobType(jobTypes);

        // 프로필 사진 업로드 (새 파일 들어왔을 때만 갱신, 미전송이면 기존 URL 유지)
        if (profileImage != null && !profileImage.isEmpty()) {
            try {
                profileImageService.deleteByUrl(employee.getEmpProfileImageUrl());
                String profileUrl = profileImageService.upload(employee.getEmpId(), profileImage);
                employee.updateProfileImage(profileUrl);
            } catch (Exception e) {
                throw new BusinessException("프로필 사진 업로드에 실패했습니다", HttpStatus.BAD_REQUEST);
            }
        }

        // 커스텀 필드 갱신 (폼 설정에서 추가된 동적 fieldKey 들의 값)
        if (requestDto.getCustomFieldsJson() != null && !requestDto.getCustomFieldsJson().isBlank()) {
            try {
                Map<String, String> customFields = objectMapper.readValue(
                        requestDto.getCustomFieldsJson(),
                        new TypeReference<Map<String, String>>() {}
                );
                employee.updateCustomFields(customFields);
            } catch (JsonProcessingException e) {
                throw new BusinessException("커스텀 필드 파싱 실패", HttpStatus.BAD_REQUEST);
            }
        }

        return EmpDetailResponseDto.from(employee);
    }

    //    6.사원 삭제
    public void deleteEmployee(UUID companyId, Long empId) {
        Employee employee = employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId).orElseThrow(() -> new EntityNotFoundException("사원을 찾을 수 없습니다"));
        faceAuthService.cascadeUnregisterFace(empId, companyId);
        employee.softDelete();
    }



    private boolean hasSalaryAccountInput(EmployeeCreateRequestDto dto) {
        return dto.getSalaryAccountNumber() != null && !dto.getSalaryAccountNumber().isBlank();
    }

}
