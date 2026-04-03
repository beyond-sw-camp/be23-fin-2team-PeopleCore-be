package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.DocumentCreateRequest;
import com.peoplecore.approval.dto.DocumentDetailResponse;
import com.peoplecore.approval.dto.DocumentUpdateRequest;
import com.peoplecore.approval.entity.*;
import com.peoplecore.approval.repository.*;
import com.peoplecore.approval.slot.SlotContextDto;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.el.lang.ELArithmetic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@Slf4j
public class ApprovalDocumentService {

    private final ApprovalDocumentRepository documentRepository;
    private final ApprovalLineRepository lineRepository;
    private final ApprovalFormRepository formRepository;
    private final ApprovalNumberService numberService;

    @Autowired
    public ApprovalDocumentService(ApprovalDocumentRepository documentRepository, ApprovalLineRepository lineRepository, ApprovalFormRepository formRepository, ApprovalNumberService numberService) {
        this.documentRepository = documentRepository;
        this.lineRepository = lineRepository;
        this.formRepository = formRepository;
        this.numberService = numberService;
    }

    /* 문서 기안(결재 요청) - Pending 상태로 바로 생성 + 채번*/
    @Transactional
    public Long createDocument(UUID companyId, Long empId, String empName, String empDeptName, String empGrade, String empTitle, DocumentCreateRequest request) {
        ApprovalForm form = formRepository.findDetailById(request.getFormId(), companyId).orElseThrow(() -> new BusinessException("양식을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        /*slotCOntext 조립*/
        SlotContextDto contextDto = SlotContextDto.builder()
                .deptName(empDeptName)
                .formCode(form.getFormCode())
                .formName(form.getFormName())
                .build();
        /*채번 조립*/
        String docNum = numberService.generateDocNum(companyId, contextDto);

        ApprovalDocument document = ApprovalDocument.builder()
                .companyId(companyId)
                .docNum(docNum)
                .formId(form)
                .empId(empId)
                .empName(empName)
                .empDeptName(empDeptName)
                .empGrade(empGrade)
                .empTitle(empTitle)
                .docType(request.getDocType())
                .docData(request.getDocData())
                .docTitle(request.getDocTitle())
                .approvalStatus(ApprovalStatus.PENDING)
                .isEmergency(request.getIsEmergency() != null ? request.getIsEmergency() : false)
                .build();

        document.markSubmitted();
        documentRepository.save(document);

        /*결재선 저장 */
        saveApprovalLine(companyId, document, request.getApprovalLine());
        return document.getDocId();
    }

    /*문서 상세 조회 */
    public DocumentDetailResponse getDocumentDetail(UUID companyId, Long docId) {
        ApprovalDocument document = documentRepository.findWithFormById(companyId, docId).orElseThrow(() -> new BusinessException("문서를 찾을 수 없습니다. ", HttpStatus.NOT_FOUND));
        List<ApprovalLine> lines = lineRepository.findByDocId_DocIdOrderByLineStep(docId);
        return DocumentDetailResponse.from(document, lines);
    }

    /*문서 수정( 임시 저장 문서만 )*/
    @Transactional
    public void updateDocument(UUID companyId, Long empId, Long docId, DocumentUpdateRequest request) {
        ApprovalDocument document = findOwnDraftDocument(companyId, empId, docId);
        document.updateDraft(request.getDocTitle(), request.getDocData(), request.getIsEmergency());

        /*결재선 교체 : 기존 삭제 -> 새로 저장 */
        if (request.getApprovalLines() != null) {
            lineRepository.deleteByDocId_DocId(docId);
            saveApprovalLine(companyId, document, request.getApprovalLines());
        }
    }

    /*임시저장 문서 삭제 */
    @Transactional
    public void deleteDocument(UUID companyId, Long empId, Long docId) {
        ApprovalDocument document = findOwnDraftDocument(companyId, empId, docId);
        lineRepository.deleteByDocId_DocId(docId);
        documentRepository.delete(document);
    }

    /*임시 저장 - Draft 상태로 생성 (채번 없음) */
    @Transactional
    public Long saveTempDocument(UUID companyId, Long empId, String empName, String empDeptName, String empGrade, String empTitle, DocumentCreateRequest request) {
        ApprovalForm form = formRepository.findDetailById(request.getFormId(), companyId).orElseThrow(() -> new BusinessException("양식을 찾을 수 없습니다. ", HttpStatus.NOT_FOUND));

        ApprovalDocument document = ApprovalDocument.builder()
                .companyId(companyId)
                .formId(form)
                .empId(empId)
                .empName(empName)
                .empDeptName(empDeptName)
                .empGrade(empGrade)
                .empTitle(empTitle)
                .docType(request.getDocType())
                .docData(request.getDocData())
                .docTitle(request.getDocTitle())
                .approvalStatus(ApprovalStatus.DRAFT)
                .isEmergency(request.getIsEmergency() != null ? request.getIsEmergency() : false)
                .build();
        documentRepository.save(document);

        /*결재선이 있다면 함께 저장 */
        if (request.getApprovalLine() != null) {
            saveApprovalLine(companyId, document, request.getApprovalLine());
        }
        return document.getDocId();
    }


    /*임시 저장 수정*/
    @Transactional
    public void updateTempDocument(UUID companyId, Long empId, Long docId, DocumentUpdateRequest request) {
        updateDocument(companyId, empId, docId, request);
    }

    /*임시 저장 -> 결재 요청 전환(상태 패턴을 사용 + 낙관적 락)*/
    @Transactional
    public void submitDocument(UUID companyId, Long empId, Long docId) {
        ApprovalDocument document = findOwnDraftDocument(companyId, empId, docId);


        /* 채번 생성 */
        SlotContextDto contextDto = SlotContextDto.builder()
                .deptName(document.getEmpDeptName())
                .formCode(document.getFormId().getFormCode())
                .formName(document.getFormId().getFormName())
                .build();

        String docNum = numberService.generateDocNum(companyId, contextDto);
        document.assignDocNum(docNum);

        /*상태 패턴 : DRAFT -> PENDING으로 DraftState.submit() 호출 */
        document.submit();

        // @Version 낙관적 락: 동시 수정 시 OptimisticLockingFailureException 발생

    }

    /* TODO: 반려 됐을 때 상황 고려 + 상신 올린 문서 취소할 수 있어야 함/ 시간이 남는다면 결재 문서가 반려됐을 때 결재 문서를 다시 수정할 수 있도록 해여함  */
    /*--------------------------------------------*/

    /*본인의 임시 저장 문서 조회*/
    private ApprovalDocument findOwnDraftDocument(UUID companyId, Long empId, Long docId) {
        ApprovalDocument document = documentRepository.findByDocIdAndCompanyId(docId, companyId).orElseThrow(() -> new BusinessException("문서를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        if (!document.getEmpId().equals(empId)) {
            throw new BusinessException("본인의 문서만 수정할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
        if (document.getApprovalStatus() != ApprovalStatus.DRAFT) {
            throw new BusinessException("임시 저장 문서만 수정/ 삭제할 수 있습니다. ");
        }
        return document;
    }


    /*결재선 저장 */
    private void saveApprovalLine(UUID companyId, ApprovalDocument document, List<DocumentCreateRequest.ApprovalLineRequest> lineRequests) {
        if (lineRequests == null) return;

        List<ApprovalLine> lines = lineRequests.stream()
                .map(req -> ApprovalLine.builder()
                        .companyId(companyId)
                        .docId(document)
                        .empId(req.getEmpId())
                        .empName(req.getEmpName())
                        .empGrade(req.getEmpGrade())
                        .empDeptName(req.getEmpDeptName())
                        .empTitle(req.getEmpTitle())
                        .approvalRole(ApprovalRole.valueOf(req.getApprovalRole()))
                        .lineStep(req.getLineStep())
                        .build()).toList();

        lineRepository.saveAll(lines);
    }

}
