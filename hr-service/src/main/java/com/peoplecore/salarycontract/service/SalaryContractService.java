package com.peoplecore.salarycontract.service;



import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.formsetup.domain.FormType;
import com.peoplecore.formsetup.dto.FormFieldSetupResponse;
import com.peoplecore.formsetup.service.FormFieldSetupService;
import com.peoplecore.minio.service.MinioService;
import com.peoplecore.salarycontract.domain.ContractStatus;
import com.peoplecore.salarycontract.domain.SalaryContract;
import com.peoplecore.salarycontract.domain.SalaryContractDetail;
import com.peoplecore.salarycontract.domain.SalaryContractSortField;
import com.peoplecore.salarycontract.dto.SalaryContractCreateReqDto;
import com.peoplecore.salarycontract.dto.SalaryContractDetailResDto;
import com.peoplecore.salarycontract.dto.SalaryContractListResDto;
import com.peoplecore.salarycontract.repository.SalaryContractRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;




@Service
@Transactional
public class SalaryContractService {

    private final SalaryContractRepository salaryContractRepository;
    private final EmployeeRepository employeeRepository;
    private final FormFieldSetupService formFieldSetupService;
    private final ObjectMapper objectMapper;
    private final MinioService minioService;

    public SalaryContractService(SalaryContractRepository salaryContractRepository, EmployeeRepository employeeRepository, FormFieldSetupService formFieldSetupService, ObjectMapper objectMapper, MinioService minioService) {
        this.salaryContractRepository = salaryContractRepository;
        this.employeeRepository = employeeRepository;
        this.formFieldSetupService = formFieldSetupService;
        this.objectMapper = objectMapper;
        this.minioService = minioService;
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

//       급여항목 분리 + totalAmount계산
        List<SalaryContractDetail> details = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

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
//                총액 누적
                totalAmount = totalAmount.add(BigDecimal.valueOf(amount));
//                처리항목 fieldMap에서 제거 //나머지값 toJson(fieldValue에 저장)
                it.remove();
            }
        }

//        현재 폼 설정 스냅샹(증적)
        List<FormFieldSetupResponse> currentForm = formFieldSetupService.getSetup(companyId, FormType.SALARY_CONTRACT);
        String formSnapshot = toJson(currentForm); //문자열로 반환 && 저장
        long formVersion = System.currentTimeMillis(); //타임스템프 시간기반 고유 값 생성(폼 생성 시점 식별)
//        계약서 저장


//      계약서 저장
        SalaryContract contract = SalaryContract.builder()
                .companyId(companyId)
                .employee(emp)
                .createBy(userId)
                .totalAmount(totalAmount)
                .status(ContractStatus.DRAFT)
                .formValues(toJson(fieldMap))
                .formSnapshot(formSnapshot)
                .formVersion(formVersion)
                .build();

//        첨부파일 처리
        String fileName = null;
        if (file != null && !file.isEmpty()) { //TODO: MinIO업로드
            try {
                fileName = minioService.uploadFile(file,"salary-contract");
            } catch (Exception e) {
                    throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
                }
            }

//        급여 상세 연결
        for (SalaryContractDetail d : details) {
            d.assignContract(contract);
        }
        salaryContractRepository.save(contract);
        return toDetailRes(contract);
    }

    //    entity -> dto
    private SalaryContractDetailResDto toDetailRes(SalaryContract contract) {
        Employee emp = contract.getEmployee();

//        formSnapshot에서 등록 당시 폼 구성 복원
        List<FormFieldSetupResponse> snapshot = fromJson(contract.getFormSnapshot(), new TypeReference<List<FormFieldSetupResponse>>() {
        });

//        formValues에서 저장된 값 복원
        Map<String, String> values = fromJson(contract.getFormValues(), new TypeReference<Map<String,String>>() {});
        if (values == null) values = new HashMap<>();

//        급여상세 필드로 합치기
        if (contract.getDetails() != null) {
            for (SalaryContractDetail d : contract.getDetails()) {
                values.put("payItem_" + d.getPayItemId(), String.valueOf(d.getAmount()));
            }
        }

//        snapshot기반으로 필드 목록 생성
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
                .registeredDate(contract.getCreatedAt() != null ? contract.getCreatedAt().toLocalDate() : null)
                .build();

    }

//    자바객체 json문자열 변환(수동변환 컨버터x)
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
//json문자열을 자바 객체로 복원(수동변환 컨버터x)
    private <T> T fromJson(String json, TypeReference<T> typeRef){
        if(json == null || json.isBlank())return null;
        try{
            return objectMapper.readValue(json,typeRef);
        }catch (JsonProcessingException e){
            return null;
        }
    }
//    List<FieldValue> → Map으로 변환 → JSON 문자열로 변환 → entity 저장, 조회는 entity의 JSON 문자열 → Map으로 복원 → FieldDetail 리스트로 변환 → 응답 DTO

}

