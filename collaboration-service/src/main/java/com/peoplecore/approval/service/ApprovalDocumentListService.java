package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.DocumentCountResponse;
import com.peoplecore.approval.dto.DocumentListResponseDto;
import com.peoplecore.approval.dto.DocumentListSearchDto;
import com.peoplecore.approval.repository.ApprovalDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@Slf4j
public class ApprovalDocumentListService {
    private final ApprovalDocumentRepository documentRepository;

    @Autowired
    public ApprovalDocumentListService(ApprovalDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /*개인 문서함 */

    public Page<DocumentListResponseDto> getWaitingDocuments(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        return documentRepository.findWaitingDocument(companyId, empId, searchDto, pageable);
    }

    public Page<DocumentListResponseDto> getCcViewDocuments(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        return documentRepository.findCcViewDocument(companyId, empId, searchDto, pageable);
    }

    public Page<DocumentListResponseDto> getUpcomingDocuments(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        return documentRepository.findUpcomingDocument(companyId, empId, searchDto, pageable);
    }

    public Page<DocumentListResponseDto> getDraftDocuments(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        return documentRepository.findDraftDocument(companyId, empId, searchDto, pageable);
    }

    public Page<DocumentListResponseDto> getTempDocuments(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        return documentRepository.findTempDocument(companyId, empId, searchDto, pageable);
    }

    public Page<DocumentListResponseDto> getApprovedDocuments(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        return documentRepository.findApprovedDocument(companyId, empId, searchDto, pageable);
    }

    public Page<DocumentListResponseDto> getCcViewBoxDocuments(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        return documentRepository.findCcViewBoxDocument(companyId, empId, searchDto, pageable);
    }

    public Page<DocumentListResponseDto> getInboxDocuments(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        return documentRepository.findInboxDocument(companyId, empId, searchDto, pageable);
    }

    /* === 전체 문서함 건수 조회 === */

    public DocumentCountResponse getDocumentCounts(UUID companyId, Long empId, Long deptId) {
        return documentRepository.countAllBoxes(companyId, empId, deptId);
    }

    /* === 개인 폴더 문서함 === */

    public Page<DocumentListResponseDto> getPersonalFolderDocuments(UUID companyId, Long empId, Long folderId, DocumentListSearchDto searchDto, Pageable pageable) {
        return documentRepository.findPersonalFolderDocument(companyId, empId, folderId, searchDto, pageable);
    }

    /* === 부서 문서함 (deptId 기준) === */

    public Page<DocumentListResponseDto> getDeptDocuments(UUID companyId, Long deptId, DocumentListSearchDto searchDto, Pageable pageable) {
        return documentRepository.findDeptDocument(companyId, deptId, searchDto, pageable);
    }
}
