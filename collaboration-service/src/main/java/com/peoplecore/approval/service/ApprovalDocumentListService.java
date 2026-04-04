package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.DocumentListResponseDto;
import com.peoplecore.approval.dto.DocumentListSearchDto;
import com.peoplecore.approval.repository.ApprovalDocumentRepository;
import com.peoplecore.client.component.HrCacheService;
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
    private final HrCacheService hrCacheService;

    @Autowired
    public ApprovalDocumentListService(ApprovalDocumentRepository documentRepository, HrCacheService hrCacheService) {
        this.documentRepository = documentRepository;
        this.hrCacheService = hrCacheService;
    }

    /*개인 문서함 */

    public Page<DocumentListResponseDto> getWaitingDocuments(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        return documentRepository.findWaitingDocument(companyId, empId, searchDto, pageable);
    }
    public Page<DocumentListResponseDto> getReceivedDocuments(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        return documentRepository.findReceiveDocument(companyId, empId, searchDto, pageable);
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

    public Page<DocumentListResponseDto> getSentDocuments(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        return documentRepository.findSentDocument(companyId, empId, searchDto, pageable);
    }

    public Page<DocumentListResponseDto> getInboxDocuments(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        return documentRepository.findInboxDocument(companyId, empId, searchDto, pageable);
    }

    /* === 부서 문서함 (deptId → deptName 변환) === */

    public Page<DocumentListResponseDto> getDeptCompletedDocuments(UUID companyId, Long deptId, DocumentListSearchDto searchDto, Pageable pageable) {
        String deptName = hrCacheService.getDept(deptId).getDeptName();
        return documentRepository.findDeptCompletedDocument(companyId, deptName, searchDto, pageable);
    }

    public Page<DocumentListResponseDto> getDeptReceivedDocuments(UUID companyId, Long deptId, DocumentListSearchDto searchDto, Pageable pageable) {
        String deptName = hrCacheService.getDept(deptId).getDeptName();
        return documentRepository.findDeptReceiveDocument(companyId, deptName, searchDto, pageable);
    }

    public Page<DocumentListResponseDto> getDeptSentDocuments(UUID companyId, Long deptId, DocumentListSearchDto searchDto, Pageable pageable) {
        String deptName = hrCacheService.getDept(deptId).getDeptName();
        return documentRepository.findDeptSentDocument(companyId, deptName, searchDto, pageable);
    }
}
