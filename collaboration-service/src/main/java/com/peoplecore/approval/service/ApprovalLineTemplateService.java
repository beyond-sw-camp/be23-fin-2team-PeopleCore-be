package com.peoplecore.approval.service;


import com.peoplecore.approval.dto.ApprovalLineTemplateCreateRequest;
import com.peoplecore.approval.dto.ApprovalLineTemplateResponse;
import com.peoplecore.approval.entity.ApprovalLineTemplate;
import com.peoplecore.approval.entity.ApprovalLineTemplateList;
import com.peoplecore.approval.repository.ApprovalLineTemplateListRepository;
import com.peoplecore.approval.repository.ApprovalLineTemplateRepository;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ApprovalLineTemplateService {
    private final ApprovalLineTemplateRepository templateRepository;
    private final ApprovalLineTemplateListRepository templateListRepository;

    @Autowired
    public ApprovalLineTemplateService(ApprovalLineTemplateRepository templateRepository, ApprovalLineTemplateListRepository templateListRepository) {
        this.templateRepository = templateRepository;
        this.templateListRepository = templateListRepository;
    }

    /*결재선 템플릿 목록 조회*/
    public List<ApprovalLineTemplateResponse> getTemplates(UUID companyId, Long empId) {
        List<ApprovalLineTemplate> templates = templateRepository
                .findWithItemsByCompanyIdAndEmpId(companyId, empId);

        return templates.stream()
                .map(template -> ApprovalLineTemplateResponse.from(template, template.getItems()))
                .toList();
    }

    /*걀재선 템플릿 저장*/
    @Transactional
    public Long createTemplate(UUID companyId, Long empId, ApprovalLineTemplateCreateRequest request) {

        /*기본 결재선 설정 시 기존 기본 해제 */
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefault(companyId, empId);
        }

        ApprovalLineTemplate template = ApprovalLineTemplate.builder()
                .companyId(companyId)
                .lineTemEmpId(empId)
                .lineTemName(request.getLineTemName())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .build();
        templateRepository.save(template);

        saveItems(template, companyId, request);
        return template.getLineTemId();
    }

    /*결재선 ㅌㅁ플릿 수정 */
    @Transactional
    public void updateTemplate(UUID companyId, Long empId, Long lineTemId, ApprovalLineTemplateCreateRequest request) {
        ApprovalLineTemplate template = findOwnTemplate(companyId, empId, lineTemId);

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefault(companyId, empId);
        }

        template.updateName(request.getLineTemName());
        template.updateDefault(Boolean.TRUE.equals(request.getIsDefault()));

        /*기존 항목 삭제 후 새로 저장*/
        templateListRepository.deleteByApprovalLineTemplateId_LineTemId(lineTemId);
        saveItems(template, companyId, request);
    }

    /* 결재선 템플릿 삭제 */
    @Transactional
    public void deleteTemplate(UUID companyId, Long empId, Long lineTemId) {
        findOwnTemplate(companyId, empId, lineTemId);
        templateListRepository.deleteByApprovalLineTemplateId_LineTemId(lineTemId);
        templateRepository.deleteById(lineTemId);
    }

    /* 기본 결재선 조회*/
    public ApprovalLineTemplateResponse getDefaultTemplate(UUID companyId, Long empId) {
        ApprovalLineTemplate template = templateRepository
                .findDefaultWithItems(companyId, empId)
                .orElseThrow(() -> new BusinessException("기본 결재선이 설정되지 않았습니다.", HttpStatus.NOT_FOUND));

        return ApprovalLineTemplateResponse.from(template, template.getItems());
    }


    /* 본인 소유 템플릿 조회 (공통) */
    private ApprovalLineTemplate findOwnTemplate(UUID companyId, Long empId, Long lineTemId) {
        return templateRepository
                .findByLineTemIdAndCompanyIdAndLineTemEmpId(lineTemId, companyId, empId)
                .orElseThrow(() -> new BusinessException("결재선 템플릿을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }

    /* 기존 기본 결재선 해제 */
    private void clearDefault(UUID companyId, Long empId) {
        templateRepository.findByCompanyIdAndLineTemEmpIdAndIsDefaultTrue(companyId, empId)
                .ifPresent(t -> t.updateDefault(false));
    }

    /* 항목 일괄 저장 */
    private void saveItems(ApprovalLineTemplate template, UUID companyId, ApprovalLineTemplateCreateRequest request) {
        request.getItemDto().forEach(item -> {
            ApprovalLineTemplateList entity = ApprovalLineTemplateList.builder()
                    .approvalLineTemplateId(template)
                    .companyId(companyId)
                    .lineTemListEmpId(item.getEmpId())
                    .approvalRole(item.getApprovalRole())
                    .lineTemListStep(item.getStep())
                    .build();
            templateListRepository.save(entity);
        });
    }

}
