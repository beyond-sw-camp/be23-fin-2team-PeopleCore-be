package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.*;
import com.peoplecore.approval.entity.ApprovalForm;
import com.peoplecore.approval.entity.ApprovalFormFolder;
import com.peoplecore.approval.entity.FrequentForm;
import com.peoplecore.approval.repository.ApprovalFormFolderRepository;
import com.peoplecore.approval.repository.ApprovalFormRepository;
import com.peoplecore.approval.repository.FrequentFormRepository;
import com.peoplecore.common.service.MinioService;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ApprovalFormService {

    private final ApprovalFormFolderRepository approvalFormFolderRepository;
    private final ApprovalFormRepository approvalFormRepository;
    private final FrequentFormRepository frequentFormRepository;
    private final MinioService minioService;

    @Autowired
    public ApprovalFormService(ApprovalFormFolderRepository approvalFormFolderRepository, ApprovalFormRepository approvalFormRepository, FrequentFormRepository frequentFormRepository, MinioService minioService) {
        this.approvalFormFolderRepository = approvalFormFolderRepository;
        this.approvalFormRepository = approvalFormRepository;
        this.frequentFormRepository = frequentFormRepository;
        this.minioService = minioService;
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
        String formHtml = minioService.getFormHtml(objectName);

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
        /*양식 코드 중복 체크 */
        if (approvalFormRepository.existsByCompanyIdAndFormCode(companyId, request.getFormCode())) {
            throw new BusinessException("이미 존재하는 양식 코드입니다. ");
        }

        /*양식명 중복 체크 */
        if (approvalFormRepository.existsByCompanyIdAndFormName(companyId, request.getFormName())) {
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
                .formWritePermission(request.getFormWriterPermission())
                .formIsPublic(request.getFormIsPublic() != null ? request.getFormIsPublic() : true)
                .formRetentionYear(request.getFormRetentionYear())
                .formMobileYn(request.getFormMobileYn() != null ? request.getFormMobileYn() : false)
                .formPreApprovalYn(request.getFormPreApprovalYn() != null ? request.getFormPreApprovalYn() : false)
                .folderId(folder)
                .formSortOrder(maxSort + 1)
                .build();

        ApprovalForm saved = approvalFormRepository.save(form);

        /*minio에 Html 업로드 */
        String objectName = String.format("forms/%s/%s_v%d.html", companyId, saved.getFormCode(), saved.getFormVersion());
        minioService.uploadFormHtml(objectName, request.getFormHtml());

        return FormDetailResponse.from(saved);
    }

    /*양식 수정 */
    @Transactional
    public FormDetailResponse updateForm(UUID companyId, Long formId, ApprovalFormUpdateRequest request) {
        ApprovalForm form = approvalFormRepository.findDetailById(formId, companyId).orElseThrow(() -> new BusinessException("양식을 찾을 수 없습니다, ", HttpStatus.NOT_FOUND));

        form.updateForm(request.getFormName(), request.getFormHtml(), request.getFormWritePermission(), request.getFormIsPublic(), request.getFormRetentionYear(), request.getFormMobileYn(), request.getFormPreApprovalYn());

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
            form.updateBatchSettings(request.getFormIsPublic(), request.getFormMobileYn(), request.getFormPreApprovalYn());
        }
        return forms.stream().map(FormListResponse::from).toList();
    }
}
