package com.peoplecore.approval.service;

import com.peoplecore.alarm.publisher.AlarmEventPublisher;
import com.peoplecore.approval.publisher.ApprovalEventPublisher;
import com.peoplecore.approval.dto.DocumentCreateRequest;
import com.peoplecore.approval.dto.DocumentDetailResponse;
import com.peoplecore.approval.dto.DocumentUpdateRequest;
import com.peoplecore.approval.entity.*;
import com.peoplecore.approval.repository.*;
import com.peoplecore.approval.entity.SourceBoxType;
import com.peoplecore.client.component.HrServiceClient;
import com.peoplecore.client.dto.AttendanceModifyHrMemberResDto;
import com.peoplecore.common.service.MinioService;

import java.util.*;
import java.util.stream.Collectors;
import com.peoplecore.approval.repository.ApprovalStatusHistoryRepository;
import com.peoplecore.approval.slot.SlotContextDto;
import com.peoplecore.client.component.HrCacheService;
import com.peoplecore.client.dto.CompanyInfoResponse;
import com.peoplecore.client.dto.DeptInfoResponse;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
@Slf4j
public class ApprovalDocumentService {

    private final ApprovalDocumentRepository documentRepository;
    private final ApprovalLineRepository lineRepository;
    private final ApprovalFormRepository formRepository;
    private final ApprovalNumberService numberService;
    private final HrCacheService hrCacheService;
    private final ApprovalStatusHistoryRepository historyRepository;
    private final ApprovalAttachmentService attachmentService;
    private final AlarmEventPublisher alarmEventPublisher;
    private final AutoClassifyExecutor autoClassifyExecutor;
    private final ApprovalSignatureRepository signatureRepository;
    private final MinioService minioService;
    private final ApprovalEventPublisher approvalEventPublisher;
    private final HrServiceClient hrServiceClient;

    @Autowired
    public ApprovalDocumentService(ApprovalDocumentRepository documentRepository, ApprovalLineRepository lineRepository, ApprovalFormRepository formRepository, ApprovalNumberService numberService, HrCacheService hrCacheService, ApprovalStatusHistoryRepository historyRepository, ApprovalAttachmentService attachmentService, AlarmEventPublisher alarmEventPublisher, AutoClassifyExecutor autoClassifyExecutor, ApprovalSignatureRepository signatureRepository, MinioService minioService, ApprovalEventPublisher approvalEventPublisher, HrServiceClient hrServiceClient) {
        this.documentRepository = documentRepository;
        this.lineRepository = lineRepository;
        this.formRepository = formRepository;
        this.numberService = numberService;
        this.hrCacheService = hrCacheService;
        this.historyRepository = historyRepository;
        this.attachmentService = attachmentService;
        this.alarmEventPublisher = alarmEventPublisher;
        this.autoClassifyExecutor = autoClassifyExecutor;
        this.signatureRepository = signatureRepository;
        this.minioService = minioService;
        this.approvalEventPublisher = approvalEventPublisher;
        this.hrServiceClient = hrServiceClient;
    }

    /* 문서 기안(결재 요청) - Pending 상태로 바로 생성 + 채번*/
    @Transactional
    public Long createDocument(UUID companyId, Long empId, String empName, Long deptId, String empGrade, String empTitle, DocumentCreateRequest request) {
        ApprovalForm form = formRepository.findDetailById(request.getFormId(), companyId).orElseThrow(() -> new BusinessException("양식을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        /* 근태 정정 양식이면 결재선에 HR 사원 포함 여부 검증 */
        if ("ATTENDANCE_MODIFY".equals(form.getFormCode())) {
            validateHrApproverIncluded(companyId, request.getApprovalLines());
        }
        CompanyInfoResponse companyInfoResponse = hrCacheService.getCompany(companyId);
        DeptInfoResponse deptInfoResponse = hrCacheService.getDept(deptId);
        /*slotCOntext 조립*/
        SlotContextDto contextDto = SlotContextDto.builder()
                .companyName(companyInfoResponse.getCompanyName())
                .deptCode(deptInfoResponse.getDeptCode())
                .deptName(deptInfoResponse.getDeptName())
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
                .empDeptId(deptId)
                .empDeptName(deptInfoResponse.getDeptName())
                .empGrade(empGrade)
                .empTitle(empTitle)
                .docType(request.getDocType())
                .docData(request.getDocData())
                .docTitle(request.getDocTitle())
                .docOpinion(request.getDocOpinion())
                .approvalStatus(ApprovalStatus.PENDING)
                .isEmergency(request.getIsEmergency() != null ? request.getIsEmergency() : false)
                .build();

        document.markSubmitted();
        if (request.getDeptFolderId() != null) {
            document.assignDeptFolder(request.getDeptFolderId());
        }
        documentRepository.save(document);

        historyRepository.save(ApprovalStatusHistory.builder()
                .docId(document.getDocId())
                .companyId(companyId)
                .previousStatus(ApprovalStatus.PENDING)
                .changedBy(empId)
                .changedStatus(ApprovalStatus.PENDING)
                .changeByName(empName)
                .changeByDeptName(deptInfoResponse.getDeptName())
                .changeByGrade(empGrade)
                .changeReason("문서 기안")
                .changedAt(LocalDateTime.now())
                .build());

        /*결재선 저장 */
        saveApprovalLine(companyId, document, request.getApprovalLines());

        /*기안자 자동분류 (SENT) */
        autoClassifyExecutor.classify(companyId, empId, SourceBoxType.SENT, document);

        /*결재선 전원 자동분류 (INBOX) */
        List<Long> receiverIds = lineRepository.findByDocId_DocIdOrderByLineStep(document.getDocId())
                .stream()
                .map(ApprovalLine::getEmpId)
                .toList();
        receiverIds.forEach(receiverId ->
                autoClassifyExecutor.classify(companyId, receiverId, SourceBoxType.INBOX, document));

        /*결재 라인 전원에게 알림 발행 */
        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .empIds(receiverIds)
                .alarmType("APPROVAL")
                .alarmTitle(document.getEmpDeptName() + " " + empName + " " + empGrade + "이(가) 결재 문서를 상신하였습니다. ")
                .alarmContent("[" + document.getDocNum() + "] " + document.getDocTitle())
                .alarmLink("/approval")
                .alarmRefType("APPROVAL_DOCUMENT")
                .alarmRefId(document.getDocId())
                .build());

        /* hr-service 에 docCreated 이벤트 발행 — 결재선 포함해 최종결재자까지 전달 */
        List<ApprovalLine> savedLines = lineRepository.findByDocId_DocIdOrderByLineStep(document.getDocId());
        approvalEventPublisher.publishDocCreated(document, savedLines);

        return document.getDocId();
    }

    /*문서 상세 조회 (열람 시 자동 읽음 처리) */
    @Transactional
    public DocumentDetailResponse getDocumentDetail(UUID companyId, Long empId, Long docId) {
        ApprovalDocument document = documentRepository.findWithFormById(companyId, docId).orElseThrow(() -> new BusinessException("문서를 찾을 수 없습니다. ", HttpStatus.NOT_FOUND));
        List<ApprovalLine> lines = lineRepository.findByDocId_DocIdOrderByLineStep(docId);

        /* 본인 결재선이 있으면 읽음 처리 */
        lines.stream()
                .filter(line -> line.getEmpId().equals(empId) && !line.getIsRead())
                .findFirst()
                .ifPresent(ApprovalLine::markRead);

        /* 기안자 + 결재선 서명 일괄 조회 (empId → presigned URL) */
        List<Long> empIds = new java.util.ArrayList<>(lines.stream().map(ApprovalLine::getEmpId).distinct().toList());
        if (!empIds.contains(document.getEmpId())) {
            empIds.add(document.getEmpId());
        }
        Map<Long, String> signatureMap = signatureRepository.findByCompanyIdAndSigEmpIdIn(companyId, empIds)
                .stream()
                .collect(Collectors.toMap(
                        ApprovalSignature::getSigEmpId,
                        sig -> minioService.getPresignedUrl(sig.getSigUrl())
                ));

        DocumentDetailResponse response = DocumentDetailResponse.from(document, lines, signatureMap);
        /* 첨부파일 목록 포함 (Pre-signed URL 포함) */
        response.setAttachments(attachmentService.getAttachments(docId));
        return response;
    }

    /*문서 수정( 임시 저장 문서만 )*/
    @Transactional
    public void updateDocument(UUID companyId, Long empId, Long docId, DocumentUpdateRequest request) {
        ApprovalDocument document = findOwnDraftDocument(companyId, empId, docId);
        document.updateDraft(request.getDocTitle(), request.getDocData(), request.getIsEmergency());

        /*결재선 교체 : 기존 삭제 -> 새로 저장 */
        if (request.getApprovalLines() != null && !request.getApprovalLines().isEmpty()) {
            lineRepository.deleteByDocId_DocId(docId);
            lineRepository.flush();
            saveApprovalLine(companyId, document, request.getApprovalLines());
        }
    }

    /*임시저장 문서 삭제 */
    @Transactional
    public void deleteDocument(UUID companyId, Long empId, Long docId) {
        ApprovalDocument document = findOwnDraftDocument(companyId, empId, docId);
        /* 첨부파일 삭제 (MinIO + DB) */
        attachmentService.deleteAllAttachments(docId);
        lineRepository.deleteByDocId_DocId(docId);
        documentRepository.delete(document);
    }


    /*임시 저장 - Draft 상태로 생성 (채번 없음) */
    @Transactional
    public Long saveTempDocument(UUID companyId, Long empId, String empName, Long deptId, String empGrade, String empTitle, DocumentCreateRequest request) {
        ApprovalForm form = formRepository.findDetailById(request.getFormId(), companyId).orElseThrow(() -> new BusinessException("양식을 찾을 수 없습니다. ", HttpStatus.NOT_FOUND));
        /*동기 요청*/
        DeptInfoResponse deptInfo = hrCacheService.getDept(deptId);

        ApprovalDocument document = ApprovalDocument.builder()
                .companyId(companyId)
                .formId(form)
                .empId(empId)
                .empName(empName)
                .empDeptId(deptId)
                .empDeptName(deptInfo.getDeptName())
                .empGrade(empGrade)
                .empTitle(empTitle)
                .docType(request.getDocType())
                .docData(request.getDocData())
                .docTitle(request.getDocTitle())
                .docOpinion(request.getDocOpinion())
                .approvalStatus(ApprovalStatus.DRAFT)
                .personalFolderId(request.getPersonalFolderId())
                .deptFolderId(request.getDeptFolderId())
                .isEmergency(request.getIsEmergency() != null ? request.getIsEmergency() : false)
                .build();
        documentRepository.save(document);

        /*결재선이 있다면 함께 저장 */
        if (request.getApprovalLines() != null && !request.getApprovalLines().isEmpty()) {
            saveApprovalLine(companyId, document, request.getApprovalLines());
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
    public void submitDocument(UUID companyId, Long deptId, Long empId, Long docId) {
        ApprovalDocument document = findOwnDraftDocument(companyId, empId, docId);
        /* 결재자 존재 검증 */
        List<ApprovalLine> lines = lineRepository.findByDocId_DocIdOrderByLineStep(docId);
        boolean hasApprover = lines.stream()
                .anyMatch(line -> line.getApprovalRole() == ApprovalRole.APPROVER);
        if (!hasApprover) {
            throw new BusinessException("결재자가 지정되지 않은 문서는 상신할 수 없습니다.");
        }

        DeptInfoResponse deptInfo = hrCacheService.getDept(deptId);
        CompanyInfoResponse companyInfo = hrCacheService.getCompany(companyId);

        /* 채번 생성 */
        SlotContextDto contextDto = SlotContextDto.builder()
                .companyName(companyInfo.getCompanyName())
                .deptCode(deptInfo.getDeptCode())
                .deptName(deptInfo.getDeptName())
                .formCode(document.getFormId().getFormCode())
                .formName(document.getFormId().getFormName())
                .build();

        String docNum = numberService.generateDocNum(companyId, contextDto);
        document.assignDocNum(docNum);

        /*상태 패턴 : DRAFT -> PENDING으로 DraftState.submit() 호출 */
        document.submit();

        /*기안자 자동분류 (SENT) */
        autoClassifyExecutor.classify(companyId, empId, SourceBoxType.SENT, document);

        /*결재선 전원 자동분류 (INBOX) */
        lineRepository.findByDocId_DocIdOrderByLineStep(docId)
                .forEach(line -> autoClassifyExecutor.classify(companyId, line.getEmpId(), SourceBoxType.INBOX, document));

        historyRepository.save(ApprovalStatusHistory.builder()
                .docId(docId)
                .companyId(companyId)
                .previousStatus(ApprovalStatus.DRAFT)
                .changedStatus(ApprovalStatus.PENDING)
                .changedBy(empId)
                .changeByName(document.getEmpName())
                .changeByDeptName(document.getEmpDeptName())
                .changeByGrade(document.getEmpGrade())
                .changeReason("임시저장 문서 상신")
                .changedAt(LocalDateTime.now())
                .build());

        /*결재 라인 전원에게 알림 발행 */
        List<Long> receiverIds = lineRepository.findByDocId_DocIdOrderByLineStep(document.getDocId())
                .stream()
                .map(ApprovalLine::getEmpId)
                .toList();

        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .empIds(receiverIds)
                .alarmType("APPROVAL")
                .alarmTitle(document.getEmpDeptName() + " " + document.getEmpName() + " " + document.getEmpGrade() + "이(가) 결재 문서를 상신하였습니다.")
                .alarmContent("[" + document.getDocNum() + "] " + document.getDocTitle())
                .alarmLink("/approval")
                .alarmRefType("APPROVAL_DOCUMENT")
                .alarmRefId(document.getDocId())
                .build());
        // @Version 낙관적 락: 동시 수정 시 OptimisticLockingFailureException 발생

    }

    /**
     * 반려 문서 재기안 — REJECTED → PENDING (문서 수정 + 새 채번 + 결재선 초기화 + 상태 전환)
     * DRAFT를 거치지 않고 한 번의 API 호출로 재제출 완료
     */
    @Transactional
    public void resubmitDocument(UUID companyId, Long empId, Long deptId,
                                 Long docId, DocumentUpdateRequest request) {
        ApprovalDocument document = documentRepository.findByDocIdAndCompanyId(docId, companyId)
                .orElseThrow(() -> new BusinessException("문서를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        /* 본인 문서인지 확인 */
        if (!document.getEmpId().equals(empId)) {
            throw new BusinessException("본인의 문서만 재기안할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        /* 반려 상태인지 확인 (상태 패턴에서도 막지만 명시적 검증) */
        if (document.getApprovalStatus() != ApprovalStatus.REJECTED) {
            throw new BusinessException("반려된 문서만 재기안할 수 있습니다.");
        }

        /* 상태 변경 이력 저장 (반려 사유는 결재선에서 가져옴) */
        List<ApprovalLine> lines = lineRepository.findByDocId_DocIdOrderByLineStep(docId);
        String rejectReason = lines.stream().map(ApprovalLine::getLineRejectReason).filter(Objects::nonNull).findFirst().orElse(null);

        historyRepository.save(ApprovalStatusHistory.builder()
                .docId(docId)
                .companyId(companyId)
                .previousStatus(ApprovalStatus.REJECTED)
                .changedStatus(ApprovalStatus.PENDING)
                .changedBy(empId)
                .changeByName(document.getEmpName())
                .changeByDeptName(document.getEmpDeptName())
                .changeByGrade(document.getEmpGrade())
                .changeReason("재기안" + (rejectReason != null ? " (이전 반려 사유: " + rejectReason + ")" : ""))
                .changedAt(LocalDateTime.now())
                .build());
        /* 문서 내용 수정 + 이전 채번/완료일시 초기화 */
        document.updateForReSubmit(request.getDocTitle(), request.getDocData(), request.getIsEmergency(), request.getDocOpinion());

        /* 새 채번 생성 */
        DeptInfoResponse deptInfo = hrCacheService.getDept(deptId);
        CompanyInfoResponse companyInfo = hrCacheService.getCompany(companyId);

        SlotContextDto contextDto = SlotContextDto.builder()
                .companyName(companyInfo.getCompanyName())
                .deptCode(deptInfo.getDeptCode())
                .deptName(deptInfo.getDeptName())
                .formCode(document.getFormId().getFormCode())
                .formName(document.getFormId().getFormName())
                .build();

        String docNum = numberService.generateDocNum(companyId, contextDto);
        document.assignDocNum(docNum);

        /* 상태 패턴: REJECTED → PENDING (RejectedState.submit() 호출) */
        document.submit();

        /*기안자 자동분류 (SENT) */
        autoClassifyExecutor.classify(companyId, empId, SourceBoxType.SENT, document);

        /* 결재선 교체가 필요한 경우 */
        /* (INBOX 자동분류는 결재선 교체 후 아래에서 실행) */
        if (request.getApprovalLines() != null) {
            lineRepository.deleteByDocId_DocId(docId);
            saveApprovalLine(companyId, document, request.getApprovalLines());
        } else {
            /* 기존 결재선 유지 시 상태만 초기화 */
            lines.forEach(ApprovalLine::resetStatus);
        }
        /*결재선 전원 자동분류 (INBOX) */
        List<Long> receiverIds = lineRepository.findByDocId_DocIdOrderByLineStep(document.getDocId())
                .stream()
                .map(ApprovalLine::getEmpId)
                .toList();
        receiverIds.forEach(receiverId ->
                autoClassifyExecutor.classify(companyId, receiverId, SourceBoxType.INBOX, document));

        /*결재 라인 전원에게 알림 발행 */
        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .empIds(receiverIds)
                .alarmType("APPROVAL")
                .alarmTitle(document.getEmpDeptName() + " " + document.getEmpName() + " " + document.getEmpGrade() + "이(가) 결재 문서를 재기안하였습니다.")
                .alarmContent("[" + document.getDocNum() + "] " + document.getDocTitle())
                .alarmLink("/approval")
                .alarmRefType("APPROVAL_DOCUMENT")
                .alarmRefId(document.getDocId())
                .build());
        // @Version 낙관적 락: 동시 수정 시 OptimisticLockingFailureException 발생
    }

    /**
     * 상신 취소(회수) - PENDING → CANCELED 전환
     */
    @Transactional
    public void recallDocument(UUID companyId, Long empId, Long docId) {
        ApprovalDocument document = documentRepository.findByDocIdAndCompanyId(docId, companyId)
                .orElseThrow(() -> new BusinessException("문서를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (document.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("결재 진행 중인 문서만 회수할 수 있습니다.");
        }
        /* 본인 문서인지 확인 */
        if (!document.getEmpId().equals(empId)) {
            throw new BusinessException("본인의 문서만 회수할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        /* 상태 변경 이력 저장 */
        historyRepository.save(ApprovalStatusHistory.builder()
                .docId(docId)
                .companyId(companyId)
                .previousStatus(ApprovalStatus.PENDING)
                .changedStatus(ApprovalStatus.CANCELED)
                .changedBy(empId)
                .changeByName(document.getEmpName())
                .changeByDeptName(document.getEmpDeptName())
                .changeByGrade(document.getEmpGrade())
                .changeReason("기안자 상신 취소(회수)")
                .changedAt(LocalDateTime.now())
                .build());

        /* 상태 패턴: PENDING → CANCELED (PendingState.recall() 호출) */
        document.recall();

        /* hr-service 에 회수 이벤트 발행 — 초과근무/휴가 양식에만 실제 발행 (Publisher 내부 formName 분기) */
        approvalEventPublisher.publishResult(document, "CANCELED", empId, null);

        /*결재 라인 전원에게 알림 발행 */
        List<Long> receiverIds = lineRepository.findByDocId_DocIdOrderByLineStep(document.getDocId())
                .stream()
                .map(ApprovalLine::getEmpId)
                .toList();

        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .empIds(receiverIds)
                .alarmType("APPROVAL")
                .alarmTitle(document.getEmpDeptName() + " " + document.getEmpName() + " " + document.getEmpGrade() + "이(가) 결재 문서를 회수하였습니다.")
                .alarmContent("[" + document.getDocNum() + "] " + document.getDocTitle())
                .alarmLink("/approval")
                .alarmRefType("APPROVAL_DOCUMENT")
                .alarmRefId(document.getDocId())
                .build());
        // @Version 낙관적 락: 동시에 결재자가 승인하면 OptimisticLockingFailureException 발생
    }

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
        if (lineRequests == null || lineRequests.isEmpty()) return;

        List<ApprovalLine> lines = lineRequests.stream()
                .map(req -> ApprovalLine.builder()
                        .companyId(companyId)
                        .docId(document)
                        .empId(req.getEmpId())
                        .empName(req.getEmpName())
                        .empGrade(req.getEmpGrade())
                        .empDeptId(req.getEmpDeptId())
                        .empDeptName(req.getEmpDeptName())
                        .empTitle(req.getEmpTitle())
                        .approvalRole(ApprovalRole.valueOf(req.getApprovalRole()))
                        .lineStep(req.getLineStep())
                        .build()).toList();

        lineRepository.saveAll(lines);
    }
    /* 근태 정정 상신 시 결재선에 HR_ADMIN 또는 HR_SUPER_ADMIN 사원이 1명 이상 포함되었는지 검증 */
    private void validateHrApproverIncluded(UUID companyId, List<DocumentCreateRequest.ApprovalLineRequest> approvalLines) {
        AttendanceModifyHrMemberResDto hrMembersDto = hrServiceClient.getHrMembers(companyId);
        Set<Long> hrEmpIds = hrMembersDto.getHrMembers().stream()
                .map(AttendanceModifyHrMemberResDto.HrMember::getEmpId)
                .collect(java.util.stream.Collectors.toSet());

        boolean hasHr = approvalLines.stream()
                .anyMatch(line -> hrEmpIds.contains(line.getEmpId()));

        if (!hasHr) {
            throw new BusinessException("결재선에 인사팀 사원이 1명 이상 포함되어야 합니다.");
        }
    }
}
