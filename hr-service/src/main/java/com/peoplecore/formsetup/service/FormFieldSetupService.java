package com.peoplecore.formsetup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.WorkGroupRepository;
import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.formsetup.domain.FieldType;
import com.peoplecore.formsetup.domain.FormFieldSetup;
import com.peoplecore.formsetup.domain.FormType;
import com.peoplecore.formsetup.dto.FormFieldSetupRequest;
import com.peoplecore.formsetup.dto.FormFieldSetupResponse;
import com.peoplecore.formsetup.repository.FormFieldSetupRepository;
import com.peoplecore.pay.domain.InsuranceJobTypes;
import com.peoplecore.pay.domain.PayItems;
import com.peoplecore.pay.repository.InsuranceJobTypesRepository;
import com.peoplecore.pay.repository.PayItemsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


@Service
@Transactional
public class FormFieldSetupService {


    private final FormFieldSetupRepository repository;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;
    private final PayItemsRepository payItemsRepository;
    private final InsuranceJobTypesRepository insuranceJobTypesRepository;
    private final WorkGroupRepository workGroupRepository;

    @Autowired
    public FormFieldSetupService(FormFieldSetupRepository repository, CompanyRepository companyRepository, ObjectMapper objectMapper, PayItemsRepository payItemsRepository, InsuranceJobTypesRepository insuranceJobTypesRepository, WorkGroupRepository workGroupRepository) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.objectMapper = objectMapper;
        this.payItemsRepository = payItemsRepository;
        this.insuranceJobTypesRepository = insuranceJobTypesRepository;
        this.workGroupRepository = workGroupRepository;
    }


    // 1. 폼 설정 조회 (회사 등록 시 들어간 sql)
    public List<FormFieldSetupResponse> getSetup(UUID companyId, FormType formType) {
        List<FormFieldSetup> entities = repository.findAllByCompany_CompanyIdAndFormTypeOrderBySectionAscSortOrderAsc(companyId, formType);

        // 첫 조회 시 기본값 자동 생성 (회사 생성 시 누락된 경우 대비)
        if (entities.isEmpty()) {
            initDefault(companyId, formType);
            entities = repository.findAllByCompany_CompanyIdAndFormTypeOrderBySectionAscSortOrderAsc(companyId, formType);
        }

        List<FormFieldSetupResponse> result = new ArrayList<>();
        for (FormFieldSetup entity : entities) {
//            연봉 form 조회 시 급여부분 skip (단, 고정필드 annualSalary는 통과)
            if (formType == FormType.SALARY_CONTRACT
                    && "급여".equals(entity.getSection())
                    && !"annualSalary".equals(entity.getFieldKey())) {
                continue;
            }
            FormFieldSetupResponse dto = FormFieldSetupResponse.from(entity);

//            사원등록 form: 업종 SELECT는 회사가 등록한 활성 산재보험 업종 목록을 동적 주입
            if (formType == FormType.EMPLOYEE_REGISTER && "insuranceJobType".equals(entity.getFieldKey())) {
                List<InsuranceJobTypes> jobTypes = insuranceJobTypesRepository
                        .findByCompany_CompanyIdAndIsActiveTrueOrderByJobTypesIdAsc(companyId);
                List<String> names = new ArrayList<>();
                for (InsuranceJobTypes jt : jobTypes) {
                    names.add(jt.getJobTypeName());
                }
                dto.setOptions(names);
            }

//            사원등록 form: 근무그룹 SELECT는 회사가 등록한 근무그룹 목록을 동적 주입
            if (formType == FormType.EMPLOYEE_REGISTER && "workGroup".equals(entity.getFieldKey())) {
                List<WorkGroup> groups = workGroupRepository
                        .findByCompany_CompanyIdAndGroupDeleteAtIsNullOrderByGroupNameAsc(companyId);
                List<String> names = new ArrayList<>();
                for (WorkGroup g : groups) {
                    names.add(g.getGroupName());
                }
                dto.setOptions(names);
            }
            result.add(dto);
        }

//        급여부분 동적생성 - 지급항목 중 활성/비법정 항목만 연동
        if (formType == FormType.SALARY_CONTRACT) {
            List<PayItems> payItems = payItemsRepository.findActiveNonLegalPaymentItems(companyId);
            int order = 1;
            for (PayItems items : payItems) {
                result.add(FormFieldSetupResponse.builder()
                        .fieldKey("payItem_" + items.getPayItemId())
                        .label(items.getPayItemName())
                        .section("급여")
                        .fieldType("NUMBER")
                        .visible(true)
                        .required(false)
                        .sortOrder(order++)
                        .locked(false)
                        .isFixed(Boolean.TRUE.equals(items.getIsFixed()))
                        .build());
            }


        }
        return result;

    }
    // 2. 폼 설정 일괄 저장 (기존 삭제 -> 새로 insert)
    public List<FormFieldSetupResponse> saveSetup(UUID companyId, FormType formType, List<FormFieldSetupRequest> requests) {
        // 급여부분 제외 후 값 덮어쓰기 (단, 고정필드 annualSalary는 통과)
        if(formType == FormType.SALARY_CONTRACT){
            List<FormFieldSetupRequest>filtered = new ArrayList<>();
            for(FormFieldSetupRequest req : requests){
                if(!"급여".equals(req.getSection()) || "annualSalary".equals(req.getFieldKey())){
                    filtered.add(req);
                }
            }
            requests = filtered;
        }


        //      기존 데이터 조회
        List<FormFieldSetup> existingList = repository.findAllByCompany_CompanyIdAndFormTypeOrderBySectionAscSortOrderAsc(companyId, formType);

//        fieldKey기준으로 Map변환 -빠른 조회(수정-update할 목록 )
        Map<String, FormFieldSetup> existingMap = new HashMap<>();
        for (FormFieldSetup e : existingList) {
            existingMap.put(e.getFieldKey(), e);
        }

//        fieldKey목록 (비교해서 빠진데이터 -삭제할 목록)
        Set<String> requestKeys = new HashSet<>();
        for (FormFieldSetupRequest req : requests) {
            requestKeys.add(req.getFieldKey());
        }

//      등록/수정
        List<FormFieldSetup> toSave = new ArrayList<>();
        for (FormFieldSetupRequest req : requests) {
            if(req.getAutoFillFrom() != null && !req.getAutoFillFrom().isBlank()){
                req.setRequired(true);
            }
            FormFieldSetup existing = existingMap.get(req.getFieldKey());
            if (existing != null) {
//                db있는 필드 = 수정
                existing.update(req.getLabel(),
                        req.getSection(),
                        FieldType.valueOf(req.getFieldType()),
                        req.getVisible(),
                        req.getRequired(),
                        req.getSortOrder(),
                        toJson(req.getOptions()), req.getAutoFillFrom());
                toSave.add(existing);
            } else { //or 등록
                Company company = companyRepository.getReferenceById(companyId);
                toSave.add(FormFieldSetup.builder()
                        .company(company)
                        .formType(formType)
                        .fieldKey(req.getFieldKey())
                        .label(req.getLabel())
                        .section(req.getSection())
                        .fieldType(FieldType.valueOf(req.getFieldType()))
                        .visible(req.getVisible())
                        .required(req.getRequired())
                        .sortOrder(req.getSortOrder())
                        .options(toJson(req.getOptions()))
                        .autoFillFrom(req.getAutoFillFrom())
                        .build());
            }
        }
//        삭제
        List<FormFieldSetup>toDelete = new ArrayList<>();
        for(FormFieldSetup e: existingList){
            if(!requestKeys.contains(e.getFieldKey())){
                toDelete.add(e);
            }
        }
        if(!toDelete.isEmpty()){
            repository.deleteAll(toDelete);
        }

        List<FormFieldSetup> saved = repository.saveAll(toSave);
        List<FormFieldSetupResponse> result = new ArrayList<>();
        for (FormFieldSetup entity : saved) {
            result.add(FormFieldSetupResponse.from(entity));
        }
        return result;
    }

    // 3. 기본값으로 초기화
    public List<FormFieldSetupResponse> resetSetup(UUID companyId, FormType formType) {
//        기존 삭제
        repository.deleteAllByCompany_CompanyIdAndFormType(companyId, formType);
        repository.flush();
//        기본값 생성
        initDefault(companyId, formType);
        return getSetup(companyId, formType);
    }


    // 폼 타입별 기본 필드 생성
    private void initDefault(UUID companyId, FormType formType) {
        List<FormFieldSetup> defaults;
        if (formType == FormType.EMPLOYEE_REGISTER) {
            defaults = buildEmployeeRegisterDefaults(companyId);
        } else if (formType == FormType.SALARY_CONTRACT) {
            defaults = buildSalaryContractDefaults(companyId);
        } else if (formType == FormType.HR_ORDER) {
            defaults = buildHrOrderDefaults(companyId);
        } else {
            defaults = buildResignDefaults(companyId);
        }
        repository.saveAll(defaults);
    }

    // ── 사원등록 기본 필드 ──
    // field(회사ID, 폼종류, 필드키, 라벨, 섹션명, 입력방식, 표시, 필수, 순서, 옵션JSON, 자동입력키) -기본값 세팅
    private List<FormFieldSetup> buildEmployeeRegisterDefaults(UUID companyId) {
        List<FormFieldSetup> list = new ArrayList<>();

        // 기본 인적사항
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "empName", "성명", "기본 인적사항", FieldType.TEXT, true, true, 1, null, null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "empNameEn", "영문명", "기본 인적사항", FieldType.TEXT, true, false, 2, null, null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "birthDate", "생년월일", "기본 인적사항", FieldType.DATE, true, true, 3, null, null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "residentNumber", "주민등록번호", "기본 인적사항", FieldType.TEXT, true, true, 4, null, null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "phone", "연락처", "기본 인적사항", FieldType.TEXT, true, true, 5, null, null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "gender", "성별", "기본 인적사항", FieldType.RADIO, true, true, 6, null, null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "personalEmail", "개인 이메일", "기본 인적사항", FieldType.TEXT, true, true, 7, null, null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "address", "주소", "기본 인적사항", FieldType.TEXT, true, false, 8, null, null));

        // 소속 및 고용 정보
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "hireDate", "입사일", "소속 및 고용 정보", FieldType.DATE, true, true, 1, null, null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "employType", "고용 형태", "소속 및 고용 정보", FieldType.SELECT, true, true, 2, "[\"정규직\",\"계약직\"]", null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "contractEnd", "계약 만료일", "소속 및 고용 정보", FieldType.DATE, true, false, 3, null, null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "department", "부서", "소속 및 고용 정보", FieldType.SELECT, true, true, 4, null, null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "rank", "직급", "소속 및 고용 정보", FieldType.SELECT, true, true, 5, null, null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "position", "직책", "소속 및 고용 정보", FieldType.SELECT, true, true, 6, null, null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "insuranceJobType", "업종", "소속 및 고용 정보", FieldType.SELECT, true, true, 7, null, null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "workGroup", "근무그룹", "소속 및 고용 정보", FieldType.SELECT, true, true, 8, null, null));

        // 시스템 계정 설정
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "empId", "사번", "시스템 계정 설정", FieldType.AUTO, true, true, 1, null, null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "companyEmail", "사내 이메일", "시스템 계정 설정", FieldType.TEXT, true, true, 2, null, null));
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "pwMethod", "초기 비밀번호 발급 방식", "시스템 계정 설정", FieldType.RADIO, true, true, 3, null, null));

        // 메뉴 / 기능 권한 설정
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "authTemplate", "권한", "메뉴 / 기능 권한 설정", FieldType.SELECT, true, true, 1, "[\"일반 사원\",\"HR 담당자\",\"인사 최고 관리자\"]", null));

        // 인사 서류 등록
        list.add(field(companyId, FormType.EMPLOYEE_REGISTER, "documents", "서류 첨부", "인사 서류 등록", FieldType.FILE, true, false, 1, null, null));

        return list;
    }

    // ── 연봉계약서 기본 필드 ──
    private List<FormFieldSetup> buildSalaryContractDefaults(UUID companyId) {
        List<FormFieldSetup> list = new ArrayList<>();

        // 인적사항 (autoFillFrom = 사원 검색 시 해당 사원 정보에서 자동입력할 필드키)
        list.add(field(companyId, FormType.SALARY_CONTRACT, "empSearch", "사원 검색", "인적사항", FieldType.SEARCH, true, true, 1, null, null));
        list.add(field(companyId, FormType.SALARY_CONTRACT, "department", "부서", "인적사항", FieldType.TEXT, true, true, 2, null, "department"));
        list.add(field(companyId, FormType.SALARY_CONTRACT, "rank", "직급", "인적사항", FieldType.TEXT, true, true, 3, null, "rank"));
        list.add(field(companyId, FormType.SALARY_CONTRACT, "position", "직책", "인적사항", FieldType.TEXT, true, true, 4, null, "position"));
        list.add(field(companyId, FormType.SALARY_CONTRACT, "employType", "근로형태", "인적사항", FieldType.TEXT, true, true, 5, null, "employType"));

        // 계약기간
        list.add(field(companyId, FormType.SALARY_CONTRACT, "contractStart", "계약 시작일", "계약기간", FieldType.DATE, true, true, 1, null, null));
        list.add(field(companyId, FormType.SALARY_CONTRACT, "contractEnd", "계약 종료일", "계약기간", FieldType.DATE, true, false, 2, null, null));
        list.add(field(companyId, FormType.SALARY_CONTRACT, "weeklyHours", "주당 근로시간", "계약기간", FieldType.SELECT, true, true, 3, "[\"40시간 (주 5일)\",\"35시간\",\"30시간\",\"20시간 (시간제)\",\"15시간 (단시간)\"]", null));

        // 급여 (고정 필드 — 동적 PayItems 보다 항상 앞)
        list.add(field(companyId, FormType.SALARY_CONTRACT, "annualSalary", "연봉", "급여", FieldType.NUMBER, true, false, 0, null, null));

        // 기타사항
        list.add(field(companyId, FormType.SALARY_CONTRACT, "memo", "특약사항 / 메모", "기타사항", FieldType.TEXTAREA, true, false, 1, null, null));
        list.add(field(companyId, FormType.SALARY_CONTRACT, "attachment", "서명 완료 계약서 첨부", "기타사항", FieldType.FILE, true, false, 2, null, null));

        return list;
    }

    // ── 인사발령 기본 필드 ──
    private List<FormFieldSetup> buildHrOrderDefaults(UUID companyId) {
        List<FormFieldSetup> list = new ArrayList<>();

        // 발령 기본 정보
        list.add(field(companyId, FormType.HR_ORDER, "orderDate", "발령일자", "발령 기본 정보", FieldType.DATE, true, true, 1, null, null));
        list.add(field(companyId, FormType.HR_ORDER, "orderNumber", "발령번호", "발령 기본 정보", FieldType.AUTO, true, true, 2, null, null));
        list.add(field(companyId, FormType.HR_ORDER, "orderTitle", "발령제목", "발령 기본 정보", FieldType.TEXT, true, true, 3, null, null));
        list.add(field(companyId, FormType.HR_ORDER, "orderCount", "발령 인원", "발령 기본 정보", FieldType.AUTO, true, false, 4, null, null));

        // 발령 유형
        list.add(field(companyId, FormType.HR_ORDER, "orderType", "발령유형", "발령 유형", FieldType.SELECT, true, true, 1, "[\"입사\",\"퇴사\",\"직위변경\",\"부서변경\",\"보직변경\"]", null));

        // 대상자 정보 (autoFillFrom = 사원 검색 시 자동입력)
        list.add(field(companyId, FormType.HR_ORDER, "empSearch", "사원명", "대상자 정보", FieldType.SEARCH, true, true, 1, null, null));
        list.add(field(companyId, FormType.HR_ORDER, "department", "부서", "대상자 정보", FieldType.TEXT, true, true, 2, null, "department"));
        list.add(field(companyId, FormType.HR_ORDER, "rank", "직위", "대상자 정보", FieldType.TEXT, true, true, 3, null, "rank"));
        list.add(field(companyId, FormType.HR_ORDER, "newDepartment", "변경 부서", "대상자 정보", FieldType.SELECT, true, false, 4, null, null));
        list.add(field(companyId, FormType.HR_ORDER, "newRank", "변경 직위", "대상자 정보", FieldType.SELECT, true, false, 5, null, null));
        list.add(field(companyId, FormType.HR_ORDER, "newPosition", "변경 보직", "대상자 정보", FieldType.SELECT, true, false, 6, null, null));
        list.add(field(companyId, FormType.HR_ORDER, "empOrderDate", "발령일", "대상자 정보", FieldType.DATE, true, true, 7, null, null));

        // 기타
        list.add(field(companyId, FormType.HR_ORDER, "orderReason", "발령 사유", "기타", FieldType.TEXTAREA, true, false, 1, null, null));

        return list;
    }

    // ── 퇴직관리 기본 필드 ──
    private List<FormFieldSetup> buildResignDefaults(UUID companyId) {
        List<FormFieldSetup> list = new ArrayList<>();

        // 대상자 (autoFillFrom = 사원 검색 시 자동입력)
        list.add(field(companyId, FormType.RESIGN_REGISTER, "empSearch", "사원 검색", "대상자", FieldType.SEARCH, true, true, 1, null, null));
        list.add(field(companyId, FormType.RESIGN_REGISTER, "department", "부서", "대상자", FieldType.TEXT, true, true, 2, null, "department"));
        list.add(field(companyId, FormType.RESIGN_REGISTER, "rank", "직급", "대상자", FieldType.TEXT, true, true, 3, null, "rank"));
        list.add(field(companyId, FormType.RESIGN_REGISTER, "hireDate", "입사일", "대상자", FieldType.TEXT, true, true, 4, null, "hireDate"));

        // 퇴직 정보
        list.add(field(companyId, FormType.RESIGN_REGISTER, "resignDate", "퇴직일", "퇴직 정보", FieldType.DATE, true, true, 1, null, null));
        list.add(field(companyId, FormType.RESIGN_REGISTER, "resignType", "퇴직 사유", "퇴직 정보", FieldType.SELECT, true, true, 2, "[\"자진퇴사\",\"권고사직\",\"정년퇴직\",\"계약만료\",\"기타\"]", null));
        list.add(field(companyId, FormType.RESIGN_REGISTER, "resignDetail", "상세 사유", "퇴직 정보", FieldType.TEXTAREA, true, false, 3, null, null));

        // 인수인계 현황
        list.add(field(companyId, FormType.RESIGN_REGISTER, "handoverWork", "업무 인수인계 완료", "인수인계 현황", FieldType.RADIO, true, false, 1, null, null));
        list.add(field(companyId, FormType.RESIGN_REGISTER, "handoverEquip", "장비 반납", "인수인계 현황", FieldType.RADIO, true, false, 2, null, null));
        list.add(field(companyId, FormType.RESIGN_REGISTER, "handoverAccount", "계정 비활성화", "인수인계 현황", FieldType.RADIO, true, false, 3, null, null));
        list.add(field(companyId, FormType.RESIGN_REGISTER, "handoverPay", "퇴직금 정산", "인수인계 현황", FieldType.RADIO, true, false, 4, null, null));

        return list;
    }

    // DB에 저장할 폼 필드 객체 1
    // 화면의 입력칸 1개 = 이 메서드 1번 호출 = DB 행 1개
    //     builder 20번 반복 = 200줄 -> 한 줄 × 20 = 약 20줄
    private FormFieldSetup field(
            UUID companyId,
            FormType formType,
            String fieldKey,
            String label,
            String section,
            FieldType fieldType,
            boolean visible,
            boolean required,
            int sortOrder,
            String options,
            String autoFillFrom
    ) {
        Company company = companyRepository.getReferenceById(companyId);
        return FormFieldSetup.builder()
                .company(company)
                .formType(formType)
                .fieldKey(fieldKey)
                .label(label)
                .section(section)
                .fieldType(fieldType)
                .visible(visible)
                .required(required)
                .sortOrder(sortOrder)
                .options(options)
                .autoFillFrom(autoFillFrom)
                .build();
    }


    // List<String>(옵션) -> JSON 문자열 변환
    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }
}
