package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.*;
import com.peoplecore.approval.entity.ApprovalForm;
import com.peoplecore.approval.entity.ApprovalFormFolder;
import com.peoplecore.approval.entity.FormWritePermission;
import com.peoplecore.approval.entity.FrequentForm;
import com.peoplecore.approval.repository.ApprovalFormFolderRepository;
import com.peoplecore.approval.repository.ApprovalFormRepository;
import com.peoplecore.approval.repository.FrequentFormRepository;
import com.peoplecore.common.entity.CommonCode;
import com.peoplecore.common.entity.CommonCodeGroup;
import com.peoplecore.common.repository.CommonCodeGroupRepository;
import com.peoplecore.common.repository.CommonCodeRepository;
import com.peoplecore.common.service.MinioService;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ApprovalFormService {


    /*
     * 수정/비활성화 보호 대상 시스템 양식 식별자.
     *  - 키 형식: "{folderName}/{formName}"
     *  - initFormFolder() 에서 해당 키에 매칭되는 양식만 isProtected=true 로 저장
     *  - 운영 중 보호 대상 추가/해제는 DB UPDATE 로 isProtected 플래그만 바꾸면 됨
     */
    private static final java.util.Set<String> PROTECTED_FORM_KEYS = java.util.Set.of(
            "보고-시행문/급여지급결의서",
            "보고-시행문/퇴직급여지급결의서",
            "휴가/초과근로신청서",
            "휴가/휴가신청서",
            "인사/사직서 #2",
            "일반기안/근태정정신청서"
    );

    /*
     * HR / 전자결재 계약 고정 formCode.
     *  - 키 형식: "{folderName}/{formName}" (seed 파일 경로 기준)
     *  - 값: 프론트·HR 서비스와 합의된 영문 SCREAMING_SNAKE formCode (계약 식별자)
     *  - 매칭되지 않는 양식은 기존 규칙(`formName + "_001"`) 으로 자동 생성
     *  - 새 계약 양식 추가 시 이 맵에만 등록하면 됨
     */
    private static final java.util.Map<String, String> FIXED_FORM_CODES = java.util.Map.of(
            "휴가/초과근로신청서", "OVERTIME_REQUEST",
            "휴가/휴가신청서", "VACATION_REQUEST",
            "인사/사직서 #2", "RESIGNATION",
            "보고-시행문/급여지급결의서", "PAYROLL_RESOLUTION",
            "보고-시행문/퇴직급여지급결의서", "SEVERANCE_RESOLUTION",
            "일반기안/근태정정신청서", "ATTENDANCE_MODIFY"
    );

    private static final String FORM_CODE_GROUP = "FORM_CODE";

    private final ApprovalFormFolderRepository approvalFormFolderRepository;
    private final ApprovalFormRepository approvalFormRepository;
    private final FrequentFormRepository frequentFormRepository;
    private final CommonCodeGroupRepository commonCodeGroupRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final MinioService minioService;
    private final ResourcePatternResolver resourcePatternResolver;

    @Autowired
    public ApprovalFormService(ApprovalFormFolderRepository approvalFormFolderRepository,
                               ApprovalFormRepository approvalFormRepository,
                               FrequentFormRepository frequentFormRepository,
                               CommonCodeGroupRepository commonCodeGroupRepository,
                               CommonCodeRepository commonCodeRepository,
                               MinioService minioService, ResourcePatternResolver resourcePatternResolver) {
        this.approvalFormFolderRepository = approvalFormFolderRepository;
        this.approvalFormRepository = approvalFormRepository;
        this.frequentFormRepository = frequentFormRepository;
        this.commonCodeGroupRepository = commonCodeGroupRepository;
        this.commonCodeRepository = commonCodeRepository;
        this.minioService = minioService;
        this.resourcePatternResolver = resourcePatternResolver;
    }

    //    결재 양식 폴더 조회하는 메서드
    public List<FormFolderResponse> getFormFolder(UUID companyId) {
//        전체 폴더 조회
        List<ApprovalFormFolder> allFolders = approvalFormFolderRepository
                .findByFolderCompanyIdAndFolderIsVisibleTrueOrderByFolderSortOrder(companyId);

//        DTO 변환후 map에 저장 (folderId -> dto
        Map<Long, FormFolderResponse> map = new LinkedHashMap<>();
        for (ApprovalFormFolder folder : allFolders) {
            map.put(folder.getFolderId(), FormFolderResponse.from(folder));
        }

        /*부모-자식 관계 조립*/
        List<FormFolderResponse> root = new ArrayList<>();
        for (ApprovalFormFolder folder : allFolders) {
            FormFolderResponse dto = map.get(folder.getFolderId());
            if (folder.getParent() == null) {
                root.add(dto);
            } else {
//              부모 폴더에 자식 추가
                FormFolderResponse parentDto = map.get(folder.getParent().getFolderId());
                if (parentDto != null) {
                    parentDto.getChildren().add(dto);
                }
            }
        }
        return root;
    }

    /*관리자용 전체 폴더 조회 (숨김 포함) */
    public List<FormFolderResponse> getAllFormFolders(UUID companyId) {
        List<ApprovalFormFolder> allFolders = approvalFormFolderRepository.findByFolderCompanyIdOrderByFolderSortOrder(companyId);

        Map<Long, FormFolderResponse> map = new LinkedHashMap<>();
        for (ApprovalFormFolder folder : allFolders) {
            map.put(folder.getFolderId(), FormFolderResponse.from(folder));
        }

        List<FormFolderResponse> root = new ArrayList<>();
        for (ApprovalFormFolder folder : allFolders) {
            FormFolderResponse dto = map.get(folder.getFolderId());
            if (folder.getParent() == null) {
                root.add(dto);
            } else {
                FormFolderResponse parentDto = map.get(folder.getParent().getFolderId());
                if (parentDto != null) {
                    parentDto.getChildren().add(dto);
                }
            }
        }
        return root;
    }

    /* 폴더 추가 */
    @Transactional
    public FormFolderResponse createFormFolder(UUID companyId, Long empId, ApprovalFormFolderCreateRequest request) {
        if (approvalFormFolderRepository.existsByFolderCompanyIdAndFolderName(companyId, request.getFolderName())) {
            throw new BusinessException("이미 존재하는 폴더명입니다, ");
        }
        ApprovalFormFolder parent = null;
        if (request.getParentId() != null) {
            parent = approvalFormFolderRepository.findById(request.getParentId()).orElseThrow(() -> new BusinessException("상위 폴더를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        }
        Integer maxSort = approvalFormFolderRepository.findMaxSortOrder(companyId, request.getParentId());

        /* minio 경로 생성 */
        String folderPath = parent != null ? parent.getFolderPath() + "/" + request.getFolderName() : "forms/" + companyId + "/" + request.getFolderName();

        ApprovalFormFolder folder = ApprovalFormFolder.builder()
                .folderCompanyId(companyId)
                .folderName(request.getFolderName())
                .parent(parent)
                .folderPath(folderPath)
                .folderSortOrder(maxSort + 1)
                .folderIsVisible(true)
                .folderEmpId(empId)
                .build();

        return FormFolderResponse.from(approvalFormFolderRepository.save(folder));
    }

    /*폴더 수정 */
    @Transactional
    public FormFolderResponse updateFormFolder(UUID companyId, Long folderId, ApprovalFormFolderUpdateRequest request) {
        ApprovalFormFolder folder = approvalFormFolderRepository.findById(folderId).orElseThrow(() -> new BusinessException("폴더를 찾을 수 없습니다. ", HttpStatus.NOT_FOUND));

        if (!folder.getFolderCompanyId().equals(companyId)) {
            throw new BusinessException("접근 권한이 없습니다, ", HttpStatus.FORBIDDEN);
        }
        if (approvalFormFolderRepository.existsByFolderCompanyIdAndFolderName(companyId, request.getFolderName())) {
            throw new BusinessException("이미 존재하는 폴더명입니다. ");
        }

        folder.updateFolderName(request.getFolderName());
        return FormFolderResponse.from(folder);
    }

    /*폴더 삭제 */
    @Transactional
    public void deleteFormFolder(UUID companyId, Long folderId) {
        ApprovalFormFolder folder = approvalFormFolderRepository.findById(folderId).orElseThrow(() -> new BusinessException("폴더를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        if (!folder.getFolderCompanyId().equals(companyId)) {
            throw new BusinessException("접근 권한이 없습니다, ", HttpStatus.FORBIDDEN);
        }

        if (approvalFormFolderRepository.existsFormByFolderId(folderId)) {
            throw new BusinessException("양식이 존재하는 폴더는 삭제할 수 없습니다.");
        }
        approvalFormFolderRepository.delete(folder);
    }

    /*폴더 노출 여부 변경 */
    @Transactional
    public FormFolderResponse updateFolderVisibility(UUID companyId, Long folderId, ApprovalFormFolderVisibilityRequest request) {
        ApprovalFormFolder folder = approvalFormFolderRepository.findById(folderId).orElseThrow(() -> new BusinessException("폴더를 찾을 수 없습니다,"));

        if (!folder.getFolderCompanyId().equals(companyId)) {
            throw new BusinessException("접근 권한이 없습니다, ", HttpStatus.FORBIDDEN);
        }

        folder.updateVisibility(request.getFolderIsVisible());
        return FormFolderResponse.from(folder);

    }

    /*
    양식 목록 조회(폴더별 필터도 가능함)
    folderId가 null이면 전체, 있으면 해당 폴더 양식만 조회
     */
    public List<FormListResponse> getForms(UUID companyId, Long folderId) {
        if (folderId != null) {
            return approvalFormRepository
                    .findAllWithFolderByFolderId(companyId, folderId)
                    .stream()
                    .map(FormListResponse::from)
                    .toList();
        }
        return approvalFormRepository
                .findAllWithFolder(companyId)
                .stream()
                .map(FormListResponse::from)
                .toList();
    }

    /*
    양식 상세 조회
   인사과에서 양식 수정 시 또는 기안자가 새문서 작성시 사용
     */
    public FormDetailResponse getFormDetailEditing(UUID companyId, Long formId) {
        ApprovalForm approvalForm = approvalFormRepository.findDetailById(formId, companyId).orElseThrow(() -> new BusinessException("양식을 찾을 수 없음", HttpStatus.NOT_FOUND));

        /*minio 오브젝트 이름 : forms/{companyId}/{formCode}_v{version}.html*/
        String objectName = String.format("forms/%s/%s_v%d.html", companyId, approvalForm.getFormCode(), approvalForm.getFormVersion());
        String formHtml = minioService.getFormHtml(objectName); //MinIO 최신본

        FormDetailResponse response = FormDetailResponse.from(approvalForm);
        response.setFormHtml(formHtml); // minio 에서 가져온 html로 교체
        return response;
    }

    public FormDetailResponse getFormDetail(UUID companyId, Long formId) {
        ApprovalForm form = approvalFormRepository
                .findDetailById(formId, companyId)
                .orElseThrow(() -> new BusinessException("양식을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        return FormDetailResponse.from(form);
    }

    /*
    자주 쓰는 양식 목록 조회(사원별) 현재 굥툥 즐겨찾기 테이블이 있는데 이 테이블을 쓸지 말지 고민할 것
     */
    public List<FormListResponse> getFrequentForms(UUID companyId, Long empId) {
        return frequentFormRepository.findAllWithForm(companyId, empId)
                .stream()
                .map(ff -> FormListResponse.from(ff.getForm()))
                .toList();
    }

    /*
    자주 쓰는 양식 추가
    양식 존재 여부 검증 후 저장
     */
    @Transactional
    public void addFrequentForm(UUID companyId, Long empId, Long formId) {
        if (frequentFormRepository.existsByCompanyIdAndEmpIdAndForm_FormId(companyId, empId, formId)) {
            throw new BusinessException("이미 자주 쓰는 양식에 등록되어 있습니다.");
        }

        ApprovalForm form = approvalFormRepository
                .findDetailById(formId, companyId)
                .orElseThrow(() -> new BusinessException("양식을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        FrequentForm frequentForm = FrequentForm.builder()
                .companyId(companyId)
                .empId(empId)
                .form(form)
                .build();

        frequentFormRepository.save(frequentForm);
    }

    /*
    자주 쓰는 양식 삭제
     */
    @Transactional
    public void removeFrequentForm(UUID companyId, Long empId, Long formId) {
        FrequentForm frequentForm = frequentFormRepository
                .findByCompanyIdAndEmpIdAndForm_FormId(companyId, empId, formId)
                .orElseThrow(() -> new BusinessException("자주 쓰는 양식에 등록되지 않은 양식입니다.", HttpStatus.NOT_FOUND));

        frequentFormRepository.delete(frequentForm);
    }


    /*양식 관리 ====== 관리자용=============*/

    /* 양식 추가 */
    @Transactional
    public FormDetailResponse createForm(UUID companyId, Long empId, ApprovalFormCreateRequest request) {
        /*양식 코드 중복 체크 (활성 양식만) */
        if (approvalFormRepository.existsByCompanyIdAndFormCodeAndIsActiveTrue(companyId, request.getFormCode())) {
            throw new BusinessException("이미 존재하는 양식 코드입니다. ");
        }

        /*양식명 중복 체크 (활성 양식만) */
        if (approvalFormRepository.existsByCompanyIdAndFormNameAndIsActiveTrue(companyId, request.getFormName())) {
            throw new BusinessException("이미 존재하는 양식명입니다, ");
        }

        ApprovalFormFolder folder = approvalFormFolderRepository.findById(request.getFolderId()).orElseThrow(() -> new BusinessException("폴더를 찾을 수 없습니다, "));

        Integer maxSort = approvalFormRepository.findMaxSortOrderInFolder(companyId, request.getFolderId());

        ApprovalForm form = ApprovalForm.builder()
                .companyId(companyId)
                .formName(request.getFormName())
                .formCode(request.getFormCode())
                .formHtml(request.getFormHtml())
                .isSystem(false)
                .formVersion(1)
                .isCurrent(true)
                .isActive(true)
                .empId(empId)
                .formWritePermission(request.getFormWritePermission())
                .formIsPublic(request.getFormIsPublic() != null ? request.getFormIsPublic() : true)
                .formRetentionYear(request.getFormRetentionYear())
                .formPreApprovalYn(request.getFormPreApprovalYn() != null ? request.getFormPreApprovalYn() : false)
                .folderId(folder)
                .formSortOrder(maxSort + 1)
                .build();

        ApprovalForm saved = approvalFormRepository.save(form);

        /* 공통코드 테이블에 양식 코드 등록 */
        CommonCodeGroup codeGroup = commonCodeGroupRepository
                .findByCompanyIdAndGroupCodeAndIsActiveTrue(companyId, FORM_CODE_GROUP)
                .orElseGet(() -> commonCodeGroupRepository.save(
                        CommonCodeGroup.builder()
                                .companyId(companyId)
                                .groupCode(FORM_CODE_GROUP)
                                .groupName("결재 양식 코드")
                                .groupDescription("결재 양식 코드를 관리하는 그룹")
                                .isActive(true)
                                .build()
                ));

        Integer maxCodeSort = commonCodeRepository.findMaxSortOrder(codeGroup.getGroupId());
        CommonCode commonCode = CommonCode.builder()
                .groupId(codeGroup.getGroupId())
                .codeValue(saved.getFormCode())
                .codeName(saved.getFormName())
                .sortOrder(maxCodeSort + 1)
                .isActive(true)
                .build();
        commonCodeRepository.save(commonCode);

        /*minio에 Html 업로드 */
        String objectName = String.format("forms/%s/%s_v%d.html", companyId, saved.getFormCode(), saved.getFormVersion());
        minioService.uploadFormHtml(objectName, request.getFormHtml());

        return FormDetailResponse.from(saved);
    }

    /*양식 수정 */
    @Transactional
    public FormDetailResponse updateForm(UUID companyId, Long formId, ApprovalFormUpdateRequest request) {
        ApprovalForm form = approvalFormRepository.findDetailById(formId, companyId).orElseThrow(() -> new BusinessException("양식을 찾을 수 없습니다, ", HttpStatus.NOT_FOUND));

        form.updateForm(request.getFormName(), request.getFormHtml(), request.getFormWritePermission(), request.getFormIsPublic(), request.getFormRetentionYear(), request.getFormPreApprovalYn());

        /*minio에 Html 업뎅트 */
        String objectName = String.format("forms/%s/%s_v%d.html", companyId, form.getFormCode(), form.getFormVersion());
        minioService.uploadFormHtml(objectName, request.getFormHtml());
        return FormDetailResponse.from(form);
    }

    /*양식 삭제 (소프트 딜리트) */
    @Transactional
    public void deleteForm(UUID companyId, Long formId) {
        ApprovalForm form = approvalFormRepository.findDetailById(formId, companyId).orElseThrow(() -> new BusinessException("양식을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        form.deactivate();

        /* 공통코드 테이블에서도 비활성화 */
        commonCodeGroupRepository
                .findByCompanyIdAndGroupCodeAndIsActiveTrue(companyId, FORM_CODE_GROUP)
                .ifPresent(codeGroup ->
                        commonCodeRepository.findByGroupIdAndCodeValueAndIsActiveTrue(codeGroup.getGroupId(), form.getFormCode())
                                .ifPresent(code -> code.updateCode(code.getCodeName(), code.getSortOrder(), false))
                );
    }

    /*양식 순서 변경 */
    @Transactional
    public List<FormListResponse> reorderForms(UUID companyId, ApprovalFormReorderRequest request) {
        /*양식별 정렬 순서 업데이트  */
        for (ApprovalFormReorderRequest.FormOrder order : request.getOrderList()) {
            approvalFormRepository.updateSortOrder(companyId, order.getFormId(), order.getFormSortOrder());
        }

        /*변경된 양식 목록 조회 후 반환*/
        List<Long> formIds = request.getOrderList().stream()
                .map(ApprovalFormReorderRequest.FormOrder::getFormId).toList();
        return approvalFormRepository.findAllByCompanyIdAndFormIds(companyId, formIds)
                .stream().map(FormListResponse::from).toList();
    }

    /*양식 일괄 설정 */
    @Transactional
    public List<FormListResponse> batchUpdateFormSettings(UUID companyId, ApprovalFormBatchSettingRequest request) {
        List<ApprovalForm> forms = approvalFormRepository.findAllByCompanyIdAndFormIds(companyId, request.getFormIds());

        if (forms.size() != request.getFormIds().size()) {
            throw new BusinessException("일부 양식을 찾을 수 없습니다, ", HttpStatus.NOT_FOUND);
        }

        for (ApprovalForm form : forms) {
            form.updateBatchSettings(request.getFormIsPublic(), request.getFormPreApprovalYn());
        }
        return forms.stream().map(FormListResponse::from).toList();
    }

    private final List<String> subFolderNames = List.of("스크립트 양식", "보고-시행문", "회계-총무", "일반기안","휴가", "출장", "인사");

    @Transactional
    public void initFormFolder(UUID companyId) {
        try {
            if (approvalFormFolderRepository.existsByFolderCompanyId(companyId)) {
                log.info("========================  이미 회사 루트 폴더가 존재합니다. companyId = {}", companyId);
                return;
            }

            String folderPath = "forms/" + companyId + "/양식모음";

            ApprovalFormFolder folder = ApprovalFormFolder.builder()
                    .folderCompanyId(companyId)
                    .folderName("양식모음")
                    .parent(null)
                    .folderPath(folderPath)
                    .folderIsVisible(true)
                    .folderSortOrder(1)
                    .build();
            approvalFormFolderRepository.save(folder);
            Map<String, ApprovalFormFolder> formFolderMap = new HashMap<>();

            for (int i = 0; i < subFolderNames.size(); i++) {
                ApprovalFormFolder subFolder = ApprovalFormFolder.builder()
                        .folderCompanyId(companyId)
                        .folderName(subFolderNames.get(i))
                        .parent(folder)
                        .folderPath(folderPath + "/" + subFolderNames.get(i))
                        .folderIsVisible(true)
                        .folderSortOrder(i + 1)
                        .build();
                approvalFormFolderRepository.save(subFolder);
                formFolderMap.put(subFolderNames.get(i), subFolder);
            }

            CommonCodeGroup codeGroup = commonCodeGroupRepository.findByCompanyIdAndGroupCodeAndIsActiveTrue(companyId, FORM_CODE_GROUP).orElseGet(() -> commonCodeGroupRepository.save(CommonCodeGroup.builder()
                    .companyId(companyId)
                    .groupCode(FORM_CODE_GROUP)
                    .groupName("결재 양식 코드")
                    .groupDescription("결재 양식을 관리하는 코드 ")
                    .isActive(true)
                    .build()));

            for (Map.Entry<String, ApprovalFormFolder> entry : formFolderMap.entrySet()) {
                String folderName = entry.getKey();
                ApprovalFormFolder subFolder = entry.getValue();

                Resource[] resources = resourcePatternResolver.getResources("classpath:default-forms/" + folderName + "/*.html");

                for (int j = 0; j < resources.length; j++) {
                    Resource resource = resources[j];

                    /*1. 파일명에서 .html제거 -> formName으로 저장하기 위해 */
                    String fileName = resource.getFilename();
                    if (fileName == null) continue;
                    String formName = fileName.replace(".html", "");
                    /*2. html 내용 읽기 -> formHtml
                     * getInputStream : resource 객체가 가리키는 파일의 내용을 바이트 스트림으로 열어주는 메서드 반환은 바이트 데이터
                     * StreamUtils.copyToString : 바이트 스트림-> String으로 변환해주는 Spring 유ㅠ틸 / UTF_8 인코딩으로 해석해라 라는 뜻. 한글이 포함된 HTML이기 때문에
                     *  순서는 파일 열기 -> 바이트 읽기 -> UTF_8 문자열로 반환 -> formHtml에 담기*/
                    String formHtml = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

                    /*3. formCode 생성
                     *  - FIXED_FORM_CODES 에 등록된 계약 양식은 영문 고정 코드 사용 (OVERTIME_REQUEST 등)
                     *  - 미등록 양식은 기존 규칙(formName + "_001") 유지
                     *  String.format 서식: %03d = 최소 3자리 정수, 앞자리 0 패딩 */
                    String fixedKey = folderName + "/" + formName;
                    String formCode = FIXED_FORM_CODES.getOrDefault(
                            fixedKey,
                            formName + "_" + String.format("%03d", j + 1));

                    /* 보호 대상 양식은 isProtected = true 로 세팅 */
                    boolean isProtectedForm = PROTECTED_FORM_KEYS.contains(folderName + "/" + formName);

                    /* 4. ApprovalForm 엔티티 생성 -> 저장 */
                    ApprovalForm form = ApprovalForm.builder()
                            .companyId(companyId)
                            .formName(formName)
                            .formCode(formCode)
                            .formHtml(formHtml)
                            .isSystem(true)
                            .isProtected(isProtectedForm)
                            .isActive(true)
                            .isCurrent(true)
                            .formWritePermission(FormWritePermission.ALL)
                            .formIsPublic(true)
                            .formRetentionYear(5)
                            .formPreApprovalYn(true)
                            .folderId(subFolder)
                            .formSortOrder(j + 1)
                            .build();
                    if (approvalFormRepository.existsByCompanyIdAndFormNameAndIsCurrent(companyId, formName, true)) {
                        continue; // 이미 존재하면 skip
                    }
                    ApprovalForm saved = approvalFormRepository.save(form);
                    String objectName = String.format("forms/%s/%s_v%d.html", companyId, saved.getFormCode(), saved.getFormVersion());
                    minioService.uploadFormHtml(objectName, formHtml);


                    Integer maxCodeSort = commonCodeRepository.findMaxSortOrder(codeGroup.getGroupId());
                    CommonCode commonCode = CommonCode.builder()
                            .groupId(codeGroup.getGroupId())
                            .codeValue(saved.getFormCode())
                            .codeName(saved.getFormName())
                            .sortOrder(maxCodeSort + 1)
                            .isActive(true)
                            .build();
                    commonCodeRepository.save(commonCode);
                }
            }
        } catch (Exception e) {
            log.error("오류가 발생했습니다. e = {}", e.getMessage());
        }
    }

    /* formCode + companyId 로 활성 양식 ID 조회 — hr-service 의 ApprovalFormIdCache 가 REST 로 호출 */
    public Long getFormIdByCode(UUID companyId, String formCode) {
        return approvalFormRepository
                .findByCompanyIdAndFormCodeAndIsActiveTrueAndIsCurrentTrue(companyId, formCode)
                .map(ApprovalForm::getFormId)
                .orElse(null);
    }
}
