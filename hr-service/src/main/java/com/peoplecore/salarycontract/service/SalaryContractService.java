package com.peoplecore.salarycontract.service;


import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.formsetup.domain.FormType;
import com.peoplecore.formsetup.dto.FormFieldSetupResponse;
import com.peoplecore.formsetup.service.FormFieldSetupService;
import com.peoplecore.minio.service.MinioService;
import com.peoplecore.pay.domain.PayItems;
import com.peoplecore.pay.repository.PayItemsRepository;
import com.peoplecore.pay.service.EmpSalaryCacheService;
import com.peoplecore.salarycontract.domain.SalaryContract;
import com.peoplecore.salarycontract.domain.SalaryContractDetail;
import com.peoplecore.salarycontract.domain.SalaryContractSortField;
import com.peoplecore.salarycontract.dto.SalaryContractCreateReqDto;
import com.peoplecore.salarycontract.dto.SalaryContractDetailResDto;
import com.peoplecore.salarycontract.dto.SalaryContractHisToryResDto;
import com.peoplecore.salarycontract.dto.SalaryContractListResDto;
import com.peoplecore.salarycontract.repository.SalaryContractRepository;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static io.lettuce.core.KillArgs.Builder.id;


@Service
@Transactional
public class SalaryContractService {

    private final SalaryContractRepository salaryContractRepository;
    private final EmployeeRepository employeeRepository;
    private final FormFieldSetupService formFieldSetupService;
    private final ObjectMapper objectMapper;
    private final MinioService minioService;
    private final PayItemsRepository payItemsRepository;
    private final EmpSalaryCacheService empSalaryCacheService;


    public SalaryContractService(SalaryContractRepository salaryContractRepository, EmployeeRepository employeeRepository, FormFieldSetupService formFieldSetupService, ObjectMapper objectMapper, MinioService minioService, PayItemsRepository payItemsRepository,EmpSalaryCacheService empSalaryCacheService) {
        this.salaryContractRepository = salaryContractRepository;
        this.employeeRepository = employeeRepository;
        this.formFieldSetupService = formFieldSetupService;
        this.objectMapper = objectMapper;
        this.minioService = minioService;
        this.empSalaryCacheService = empSalaryCacheService;
        this.payItemsRepository = payItemsRepository;
    }

    //    1. 목록 조회
    @Transactional(readOnly = true)
    public Page<SalaryContractListResDto> list(UUID companyId, String search, String year, SalaryContractSortField sortField, Pageable pageable) {
        return salaryContractRepository.findAllWithFilter(companyId, search, year, sortField, pageable);
    }

//    2. 계약서 생성

    public SalaryContractDetailResDto create(UUID companyId, Long userId, SalaryContractCreateReqDto req, MultipartFile file) {

//        사원조회
        Employee emp = employeeRepository.findById(req.getEmpId()).orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

//      프론트 필드값
        Map<String, String> fieldMap = new LinkedHashMap<>();
        for (SalaryContractCreateReqDto.FieldValue fv : req.getFields()) {
            fieldMap.put(fv.getFieldKey(), fv.getValue());
        }

//        contractYear, applyFrom, applyTo 추출
        String contractYearStr = fieldMap.get("contractYear");
        Integer contractYear = (contractYearStr != null && !contractYearStr.isBlank()) ? Integer.parseInt(contractYearStr):null;

        String applyFromStr = fieldMap.get("contractStart");
        LocalDate applyFrom = (applyFromStr != null && !applyFromStr.isBlank()) ? LocalDate.parse(applyFromStr):null;

        String applyToStr = fieldMap.get("contractEnd");
        LocalDate applyTo = (applyToStr != null &&!applyToStr.isBlank()) ? LocalDate.parse(applyToStr) : null;



//       급여항목 분리 + totalAmount계산
        List<SalaryContractDetail> details = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        Map<Long, Integer> payItemAmountMap = new LinkedHashMap<>();

        Iterator<Map.Entry<String, String>> it = fieldMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            if (entry.getKey().startsWith("payItem_")) { //payItem으로 시작하는 항목
                int amount = entry.getValue() != null && !entry.getValue().isBlank() ? Integer.parseInt(entry.getValue()) : 0;
//               key에서 "payItem_"제거 -> 급여항목 id추출
                Long payItemId = Long.parseLong(entry.getKey().replace("payItem_", ""));
                details.add(SalaryContractDetail.builder()
                        .payItemId(payItemId)
                        .amount(amount)
                        .build());
                payItemAmountMap.put(payItemId, amount);
//                총액 누적
                totalAmount = totalAmount.add(BigDecimal.valueOf(amount));
//                처리항목 fieldMap에서 제거 //나머지값 toJson(fieldValue에 저장)
                it.remove();
            }
        }

//        연봉 하한선 검증 — 고정수당(isFixed=true) 합 × 12 ≤ annualSalary
        String annualSalaryStr = fieldMap.get("annualSalary");
        if (annualSalaryStr != null && !annualSalaryStr.isBlank() && !payItemAmountMap.isEmpty()) {
            List<PayItems> payItemEntities = payItemsRepository.findByPayItemIdInAndCompany_CompanyId(
                    new ArrayList<>(payItemAmountMap.keySet()), companyId);
            long fixedMonthlySum = 0L;
            for (PayItems pi : payItemEntities) {
                if (Boolean.TRUE.equals(pi.getIsFixed())) {
                    Integer amt = payItemAmountMap.get(pi.getPayItemId());
                    if (amt != null) fixedMonthlySum += amt;
                }
            }
            long minAnnual = fixedMonthlySum * 12L;
            long annualSalary = Long.parseLong(annualSalaryStr);
            if (annualSalary < minAnnual) {
                throw new CustomException(ErrorCode.ANNUAL_SALARY_BELOW_MINIMUM);
            }
        }

//        현재 폼 설정 스냅샷(증적)
        List<FormFieldSetupResponse> currentForm = formFieldSetupService.getSetup(companyId, FormType.SALARY_CONTRACT);
        String formSnapshot = toJson(currentForm); //문자열로 반환 && 저장
        long formVersion = System.currentTimeMillis(); //타임스템프 시간기반 고유 값 생성(폼 생성 시점 식별)
//        계약서 저장


//        첨부파일 처리 — 계약서 build 전에 업로드해서 메타 함께 세팅
        String fileName = null;
        String originalFileName = null;
        String contentType = null;
        Long fileSize = null;
        if (file != null && !file.isEmpty()) {
            try {
                fileName = minioService.uploadFile(file, "salary-contract");
                originalFileName = file.getOriginalFilename();
                contentType = file.getContentType();
                fileSize = file.getSize();
            } catch (Exception e) {
                throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED, e);
            }
        }

        String annualSalary = fieldMap.get("annualSalary");
        BigDecimal annualSalaryBD = (annualSalaryStr != null && !annualSalaryStr.isBlank()) ? new BigDecimal(annualSalaryStr) : totalAmount;

//      계약서 저장 — details 도 builder 에 포함시켜야 CascadeType.ALL 로 자식 insert 됨
        SalaryContract contract = SalaryContract.builder()
                .companyId(companyId)
                .employee(emp)
                .createBy(userId)
                .totalAmount(annualSalaryBD)
                .formValues(toJson(fieldMap))
                .formSnapshot(formSnapshot)
                .formVersion(formVersion)
                .contractYear(contractYear)
                .applyFrom(applyFrom)
                .applyTo(applyTo)
                .fileName(fileName)
                .originalFileName(originalFileName)
                .contentType(contentType)
                .fileSize(fileSize)
                .details(details)
                .build();

//        급여 상세 양방향 연결 — 자식의 contract 참조 세팅
        for (SalaryContractDetail d : details) {
            d.assignContract(contract);
        }
        salaryContractRepository.save(contract);


        // 캐시 무효화 (사원 급여조회시 redis캐싱값으로 저장하여 보고있었던걸 무효화 시킴 by 진희) //TODO
        empSalaryCacheService.evictByEmpId(companyId, emp.getEmpId());
        empSalaryCacheService.evictExpected(companyId);

        return toDetailRes(contract);
    }

    //    3. 상세조회
    @Transactional(readOnly = true)
    public SalaryContractDetailResDto detail(UUID companyId, Long contractId) {

//        계약서 조회
        SalaryContract contract = salaryContractRepository.findById(contractId).orElseThrow(() -> new CustomException(ErrorCode.SALARY_CONTRACT_NOT_FOUND));

//        타회사 계약서 접근 방시
        if (!contract.getCompanyId().equals(companyId)) {
            throw new CustomException(ErrorCode.SALARY_CONTRACT_NOT_FOUND);
        }
        return toDetailRes(contract);
    }

    //    3-1. 첨부 파일 다운로드 — MinIO 객체를 가져와 원본 파일명/Content-Type/Length 헤더와 함께 스트리밍
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadFile(UUID companyId, Long contractId) {
        SalaryContract contract = salaryContractRepository.findById(contractId)
                .orElseThrow(() -> new CustomException(ErrorCode.SALARY_CONTRACT_NOT_FOUND));

        if (!contract.getCompanyId().equals(companyId)) {
            throw new CustomException(ErrorCode.SALARY_CONTRACT_NOT_FOUND);
        }
        if (contract.getFileName() == null || contract.getFileName().isBlank()) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }

        InputStream in;
        try {
            in = minioService.downloadFile(contract.getFileName());
        } catch (Exception e) {
            throw new CustomException(ErrorCode.FILE_DOWNLOAD_FAILED, e);
        }

        String original = contract.getOriginalFileName() != null
                ? contract.getOriginalFileName()
                : "salary-contract";
        // 한글 파일명 RFC 5987 인코딩
        String encodedName = URLEncoder.encode(original, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        String ct = contract.getContentType() != null
                ? contract.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.parseMediaType(ct));
        if (contract.getFileSize() != null) {
            builder = builder.contentLength(contract.getFileSize());
        }
        return builder.body(new InputStreamResource(in));
    }

    //    domain -> dto
    private SalaryContractDetailResDto toDetailRes(SalaryContract contract) {
        Employee emp = contract.getEmployee();

//        formSnapshot에서 등록 당시 폼 구성 복원 (일반필드먼저)
        List<FormFieldSetupResponse> snapshot = fromJson(contract.getFormSnapshot(), new TypeReference<List<FormFieldSetupResponse>>() {
        });
//        formValues에서 저장된 값 복원
        Map<String, String> values = fromJson(contract.getFormValues(), new TypeReference<Map<String, String>>() {
        });
        if (values == null) values = new HashMap<>();

//        급여상세 필드로 합치기
        if (contract.getDetails() != null) {
            for (SalaryContractDetail d : contract.getDetails()) {
                values.put("payItem_" + d.getPayItemId(), String.valueOf(d.getAmount()));
            }
        }

//        snapshot기반으로 필드 목록 생성(조립)
        List<SalaryContractDetailResDto.FieldDetail> fields = new ArrayList<>();
        if (snapshot != null) {
            for (FormFieldSetupResponse f : snapshot) {
                fields.add(SalaryContractDetailResDto.FieldDetail.builder()
                        .fieldKey(f.getFieldKey())
                        .label(f.getLabel())
                        .section(f.getSection())
                        .fieldType(f.getFieldType())
                        .value(values.getOrDefault(f.getFieldKey(), ""))
                        .build());
            }
        }
//        입력값 리턴
        return SalaryContractDetailResDto.builder()
                .id(contract.getContractId())
                .empId(emp.getEmpId())
                .empNum(emp.getEmpNum())
                .empName(emp.getEmpName())
                .fields(fields)
                .fileName(contract.getFileName())
                .originalFileName(contract.getOriginalFileName())
                .registeredDate(contract.getCreatedAt() != null ? contract.getCreatedAt().toLocalDate() : null)
                .build();

    }

    //  4. 사원별 계약 이력
    @Transactional
    public List<SalaryContractHisToryResDto> historysnap(UUID companyId, Long empId) {

//        사원조회
        Employee emp = employeeRepository.findById(empId).orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

//        해당 사원 목록 조회(최신계약먼저: 내림차순)
        List<SalaryContract> contracts = salaryContractRepository.findByCompanyIdAndEmployee_EmpIdAndDeletedAtIsNullOrderByContractYearDesc(companyId, empId);

//        계약서-> dto변환
        List<SalaryContractHisToryResDto> result = new ArrayList<>();

        for (SalaryContract c : contracts) {
            result.add(SalaryContractHisToryResDto.builder()
                    .id(c.getContractId())
                    .empNum(emp.getEmpNum())
                    .empName(emp.getEmpName())
                    .department(emp.getDept().getDeptName())
                    .rank(emp.getGrade().getGradeName())
                    .year(c.getContractYear())
                    .annualSalary(c.getTotalAmount())
                    .contractStart(c.getApplyFrom())
                    .contractEnd(c.getApplyTo())
                    .build());
        }

//        전년대비 연봉변동 계산
        for(int i = 0; i<result.size() -1; i++){
            BigDecimal current = result.get(i).getAnnualSalary();
            BigDecimal prev = result.get(i+1).getAnnualSalary();

            if(current != null && prev != null && prev.compareTo(BigDecimal.ZERO) != 0){ // /0방지
//                차이 = 올해-전년(양수-인상, 음수-삭감)
                BigDecimal diff = current.subtract(prev);
//                변동률 = 차이/전년 *100, 소수점 1자리 반올림
                BigDecimal rate = diff.multiply(BigDecimal.valueOf(100)).divide(prev,1, RoundingMode.HALF_UP);

                result.get(i).setSalaryDiff(diff);
                result.get(i).setSalaryDiffRate(rate);

            }
        }
        return result;
    }

//    domain


    //    자바객체 json문자열 변환(수동변환 컨버터x)
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    //json문자열을 자바 객체로 복원(수동변환 컨버터x)
    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
//    List<FieldValue> → Map으로 변환 → JSON 문자열로 변환 → domain 저장, 조회는 entity의 JSON 문자열 → Map으로 복원 → FieldDetail 리스트로 변환 → 응답 DTO


//    5. 계약서 삭제(soft delete)
//    재직상태=퇴직 인 사원의 계약서만 삭제가능
    public void delete(UUID companyId, Long contractId){

//        계약서 조회
        SalaryContract contract = salaryContractRepository.findById(contractId).orElseThrow(()->new CustomException(ErrorCode.SALARY_CONTRACT_NOT_FOUND));

//        본인 회사만
        if(!contract.getCompanyId().equals(companyId)){
            throw new CustomException(ErrorCode.SALARY_CONTRACT_NOT_FOUND);
        }

//        삭제된 계약서인지 확인
        if(contract.isDeleted()){
            throw new CustomException(ErrorCode.SALARY_CONTRACT_ALREADY_DELETED);
        }

//        퇴직 상태의 사원 계약서만 삭제가능
        if(contract.getEmployee().getEmpStatus() != EmpStatus.RESIGNED){
            throw new CustomException(ErrorCode.EMPLOYEE_NOT_RESIGNED);
        }

//        soft delete처리(deleteAt에 현재 날짜 세팅)
        contract.softDelete();
    }
}







