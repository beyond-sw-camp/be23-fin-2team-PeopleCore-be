package com.peoplecore.approval.repository;

import com.peoplecore.approval.dto.DocumentListResponseDto;
import com.peoplecore.approval.dto.DocumentListSearchDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.UUID;

public interface ApprovalDocumentCustomRepository {
    /*개인 문서함 사원 Id 기준*/

    /*결재 대기 문서*/
    Page<DocumentListResponseDto> findWaitingDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable);

    /*결재 수신 문서*/
    Page<DocumentListResponseDto> findReceiveDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable);

    /*참조/열람 대기 문서*/
    Page<DocumentListResponseDto> findCcViewDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable);

    /* 결재 예정 문서*/
    Page<DocumentListResponseDto> findUpcomingDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable);

    /*기안 문서함*/
    Page<DocumentListResponseDto> findDraftDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable);

    /* 임시 저장함*/
    Page<DocumentListResponseDto> findTempDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable);

    /* 결재 문서함*/
    Page<DocumentListResponseDto> findApprovedDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable);

    /*참조 열람 문서함*/
    Page<DocumentListResponseDto> findCcViewBoxDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable);

    /*발송 문서함*/
    Page<DocumentListResponseDto> findSentDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable);

    /*수신 문서함*/
    Page<DocumentListResponseDto> findInboxDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable);

    /*부서 문서함 deptName 기준 */

    /*부서 결재 대기함*/
    Page<DocumentListResponseDto> findDeptCompletedDocument(UUID companyId, String deptName, DocumentListSearchDto searchDto, Pageable pageable);

    /*부서 수신 문서함 */
    Page<DocumentListResponseDto> findDeptReceiveDocument(UUID companyId, String deptName, DocumentListSearchDto searchDto, Pageable pageable);

    /* 부서 발신 문서함 */
    Page<DocumentListResponseDto> findDeptSentDocument(UUID companyId, String deptName, DocumentListSearchDto searchDto, Pageable pageable);


}
