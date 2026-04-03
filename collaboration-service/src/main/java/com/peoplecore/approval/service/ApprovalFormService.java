package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.FormDetailResponse;
import com.peoplecore.approval.dto.FormFolderResponse;
import com.peoplecore.approval.dto.FormListResponse;
import com.peoplecore.approval.entity.ApprovalForm;
import com.peoplecore.approval.entity.ApprovalFormFolder;
import com.peoplecore.approval.entity.FrequentForm;
import com.peoplecore.approval.repository.ApprovalFormFolderRepository;
import com.peoplecore.approval.repository.ApprovalFormRepository;
import com.peoplecore.approval.repository.FrequentFormRepository;
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

    @Autowired
    public ApprovalFormService(ApprovalFormFolderRepository approvalFormFolderRepository, ApprovalFormRepository approvalFormRepository, FrequentFormRepository frequentFormRepository) {
        this.approvalFormFolderRepository = approvalFormFolderRepository;
        this.approvalFormRepository = approvalFormRepository;
        this.frequentFormRepository = frequentFormRepository;
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
    TODO : 현재 코드의 의도에 따라 로직이 변해야 함. 현재 코드는 db에 저장된 html을 불러오는 코드기 때문에 만약 인사과에서 문서 수정을 위한 문서 열람일 경우 minio에서 가져오는 로직으로 변경 . 만일 이미 승인이 다 된 문서를 조회하는 경우에는 이 로직 그대로 사용하는게 맞음
     */

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

}
