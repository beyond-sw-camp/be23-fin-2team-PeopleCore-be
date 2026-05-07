package com.peoplecore.approval.service;

import com.peoplecore.alarm.publisher.AlarmEventPublisher;
import com.peoplecore.approval.handler.ApprovalFormHandler;
import com.peoplecore.approval.handler.ApprovalFormHandlerRegistry;
import com.peoplecore.approval.publisher.ApprovalEventPublisher;
import com.peoplecore.approval.dto.DocumentCreateRequest;
import com.peoplecore.approval.dto.DocumentDetailResponse;
import com.peoplecore.approval.dto.DocumentUpdateRequest;
import com.peoplecore.approval.entity.*;
import com.peoplecore.approval.repository.*;
import com.peoplecore.approval.entity.SourceBoxType;
import com.peoplecore.client.component.HrServiceClient;
import com.peoplecore.client.dto.AttendanceModifyHrMemberResDto;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final ApprovalAttachmentRepository attachmentRepository;   // 재기안 시 첨부 row 복제용
    private final AlarmEventPublisher alarmEventPublisher;
    private final AutoClassifyExecutor autoClassifyExecutor;
    private final ApprovalSignatureRepository signatureRepository;
    private final ApprovalEventPublisher approvalEventPublisher;
    private final HrServiceClient hrServiceClient;
    private final ApprovalDocumentRepository approvalDocumentRepository;
    private final ApprovalFormHandlerRegistry formHandlerRegistry;

    public ApprovalDocumentService(ApprovalDocumentRepository documentRepository, ApprovalLineRepository lineRepository, ApprovalFormRepository formRepository, ApprovalNumberService numberService, HrCacheService hrCacheService, ApprovalStatusHistoryRepository historyRepository, ApprovalAttachmentService attachmentService, ApprovalAttachmentRepository attachmentRepository, AlarmEventPublisher alarmEventPublisher, AutoClassifyExecutor autoClassifyExecutor, ApprovalSignatureRepository signatureRepository, ApprovalEventPublisher approvalEventPublisher, HrServiceClient hrServiceClient, ApprovalDocumentRepository approvalDocumentRepository, ApprovalFormHandlerRegistry formHandlerRegistry) {
        this.documentRepository = documentRepository;
        this.lineRepository = lineRepository;
        this.formRepository = formRepository;
        this.numberService = numberService;
        this.hrCacheService = hrCacheService;
        this.historyRepository = historyRepository;
        this.attachmentService = attachmentService;
        this.attachmentRepository = attachmentRepository;
        this.alarmEventPublisher = alarmEventPublisher;
        this.autoClassifyExecutor = autoClassifyExecutor;
        this.signatureRepository = signatureRepository;
        this.approvalEventPublisher = approvalEventPublisher;
        this.hrServiceClient = hrServiceClient;
        this.approvalDocumentRepository = approvalDocumentRepository;
        this.formHandlerRegistry = formHandlerRegistry;
    }

    /* 문서 기안(결재 요청) - Pending 상태로 바로 생성 + 채번 + 첨부 업로드 */
    @Transactional
    public Long createDocument(UUID companyId, Long empId, String empName, Long deptId, String empGrade, String empTitle, DocumentCreateRequest request, List<MultipartFile> files) {
        log.info("[기안] empId={}, formId={}, filesReceived={}", empId, request.getFormId(), files != null ? files.size() : 0);
        ApprovalForm form = formRepository.findDetailById(request.getFormId(), companyId).orElseThrow(() -> new BusinessException("양식을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        /* 멱등키 필요 폼(근태정정/휴가부여)이면 결재선에 HR 사원 포함 여부 검증 */
        boolean needIdempotency = formHandlerRegistry.findByForm(form)
                .map(ApprovalFormHandler::requiresIdempotencyKey)
                .orElse(false);
        if (needIdempotency) {
            validateHrApproverIncluded(companyId, request.getApprovalLines());
        }
        /* 폼별 사전 검증(휴가 잔여 등) — 실패 시 BusinessException 으로 save 차단 */
        formHandlerRegistry.findByForm(form)
                .ifPresent(h -> h.preCreate(companyId, empId, request));
        CompanyInfoResponse companyInfoResponse = hrCacheService.getCompany(companyId);
        DeptInfoResponse deptInfoResponse = hrCacheService.getDept(deptId);
        /*slotContext 조립*/
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
                .isPublic(request.getIsPublic() != null ? request.getIsPublic() : true)
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
        approvalEventPublisher.publishDocCreated(document, savedLines, request.getHtmlContent());

        /* 첨부파일이 같이 왔으면 MinIO + DB 저장 (같은 트랜잭션) */
        if (files != null && !files.isEmpty()) {
            attachmentService.uploadAttachments(companyId, empId, document.getDocId(), files);
        }

        return document.getDocId();
    }

    /*문서 상세 조회 (열람 시 자동 읽음 처리) */
    @Transactional
    public DocumentDetailResponse getDocumentDetail(UUID companyId, Long empId, Long docId) {
        ApprovalDocument document = documentRepository.findWithFormById(companyId, docId).orElseThrow(() -> new BusinessException("문서를 찾을 수 없습니다. ", HttpStatus.NOT_FOUND));
        List<ApprovalLine> lines = lineRepository.findByDocId_DocIdOrderByLineStep(docId);

        /* 비공개 문서 진입 가드 — 기안자 본인 또는 결재라인 본인만 통과, 그 외(HR_ADMIN 포함) 403 */
        if (Boolean.FALSE.equals(document.getIsPublic())
                && !document.getEmpId().equals(empId)
                && lines.stream().noneMatch(l -> l.getEmpId().equals(empId))) {
            throw new BusinessException("비공개 문서입니다. 접근 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        /* 본인 결재선이 있으면 읽음 처리 */
        lines.stream()
                .filter(line -> line.getEmpId().equals(empId) && !line.getIsRead())
                .findFirst()
                .ifPresent(ApprovalLine::markRead);

        /* 기안자 + 결재선 서명 일괄 조회 (empId → 백엔드 프록시 URL) */
        List<Long> empIds = new java.util.ArrayList<>(lines.stream().map(ApprovalLine::getEmpId).distinct().toList());
        if (!empIds.contains(document.getEmpId())) {
            empIds.add(document.getEmpId());
        }
        Map<Long, String> signatureMap = signatureRepository.findByCompanyIdAndSigEmpIdIn(companyId, empIds)
                .stream()
                .collect(Collectors.toMap(
                        ApprovalSignature::getSigEmpId,
                        sig -> "/approval/signatures/" + sig.getSigEmpId() + "/file?v=" + sig.getSigId()
                ));

        DocumentDetailResponse response = DocumentDetailResponse.from(document, lines, signatureMap);
        /* 첨부파일 목록 포함 (Pre-signed URL 포함) */
        response.setAttachments(attachmentService.getAttachments(docId));
        return response;
    }

    /* 문서 수정 (임시 저장 문서) + 신규 첨부 추가 (기존 첨부는 유지, 개별 삭제는 DELETE /attachments/{attachId}) */
    @Transactional
    public void updateDocument(UUID companyId, Long empId, Long docId, DocumentUpdateRequest request, List<MultipartFile> files) {
        log.info("[문서 수정] docId={}, empId={}, filesReceived={}", docId, empId, files != null ? files.size() : 0);
        ApprovalDocument document = findOwnDraftDocument(companyId, empId, docId);
        document.updateDraft(request.getDocTitle(), request.getDocData(), request.getIsEmergency());

        /*결재선 교체 : 기존 삭제 -> 새로 저장 */
        if (request.getApprovalLines() != null && !request.getApprovalLines().isEmpty()) {
            lineRepository.deleteByDocId_DocId(docId);
            lineRepository.flush();
            saveApprovalLine(companyId, document, request.getApprovalLines());
        }

        /* 신규 첨부 추가 업로드 */
        if (files != null && !files.isEmpty()) {
            attachmentService.uploadAttachments(companyId, empId, docId, files);
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


    /* 임시 저장 - Draft 상태로 생성 (채번 없음) + 첨부 업로드 */
    @Transactional
    public Long saveTempDocument(UUID companyId, Long empId, String empName, Long deptId, String empGrade, String empTitle, DocumentCreateRequest request, List<MultipartFile> files) {
        log.info("[임시저장] empId={}, formId={}, filesReceived={}", empId, request.getFormId(), files != null ? files.size() : 0);
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

        /* 임시저장 단계에서도 첨부 허용 */
        if (files != null && !files.isEmpty()) {
            attachmentService.uploadAttachments(companyId, empId, document.getDocId(), files);
        }

        return document.getDocId();
    }


    /*임시 저장 수정*/
    @Transactional
    public void updateTempDocument(UUID companyId, Long empId, Long docId, DocumentUpdateRequest request, List<MultipartFile> files) {
        updateDocument(companyId, empId, docId, request, files);
    }

    /*임시 저장 -> 결재 요청 전환(상태 패턴을 사용 + 낙관적 락)*/
    @Transactional
    public void submitDocument(UUID companyId, Long deptId, Long empId, Long docId, Boolean isPublic) {
        ApprovalDocument document = findOwnDraftDocument(companyId, empId, docId);
        /* 결재자 존재 검증 */
        List<ApprovalLine> lines = lineRepository.findByDocId_DocIdOrderByLineStep(docId);
        boolean hasApprover = lines.stream()
                .anyMatch(line -> line.getApprovalRole() == ApprovalRole.APPROVER);
        if (!hasApprover) {
            throw new BusinessException("결재자가 지정되지 않은 문서는 상신할 수 없습니다.");
        }

        document.changeVisibility(isPublic); // 상신 시점에 공개여부 확정 (null 이면 기존 값 유지)

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
     * 반려 문서 재기안 — 이전 문서(REJECTED)는 보존하고 새 문서 row를 INSERT
     * 새 docId, 새 채번, 결재선/첨부 복제, previousDocId 로 체인 연결
     */
    @Transactional
    public Long resubmitDocument(UUID companyId, Long empId, Long deptId,
                                 Long docId, DocumentUpdateRequest request, List<MultipartFile> files) {
        log.info("[재기안] prevDocId={}, empId={}, filesReceived={}", docId, empId, files != null ? files.size() : 0);
        ApprovalDocument prev = documentRepository.findByDocIdAndCompanyId(docId, companyId)
                .orElseThrow(() -> new BusinessException("문서를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        /* 본인 문서만 재기안 가능 */
        if (!prev.getEmpId().equals(empId)) {
            throw new BusinessException("본인의 문서만 재기안할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
        /* REJECTED 외 상태는 State 가 throw */
        prev.requireResubmittable();

        /* 이전 문서는 손대지 않음 - REJECTED/docNum/docCompleteAt 그대로 보존 */

        /* 새 채번 생성 */
        DeptInfoResponse deptInfo = hrCacheService.getDept(deptId);
        CompanyInfoResponse companyInfo = hrCacheService.getCompany(companyId);

        SlotContextDto contextDto = SlotContextDto.builder()
                .companyName(companyInfo.getCompanyName())
                .deptCode(deptInfo.getDeptCode())
                .deptName(deptInfo.getDeptName())
                .formCode(prev.getFormId().getFormCode())
                .formName(prev.getFormId().getFormName())
                .build();
        String docNum = numberService.generateDocNum(companyId, contextDto);

        /* 새 문서 INSERT - 이전 스냅샷 복사 + 재기안 입력값 반영 */
        ApprovalDocument newDoc = ApprovalDocument.builder()
                .companyId(companyId)
                .formId(prev.getFormId())
                .empId(prev.getEmpId())
                .empName(prev.getEmpName())
                .empDeptId(prev.getEmpDeptId())
                .empDeptName(prev.getEmpDeptName())
                .empGrade(prev.getEmpGrade())
                .empTitle(prev.getEmpTitle())
                .docType(prev.getDocType())
                .docTitle(request.getDocTitle())   // 수정된 제목
                .docData(request.getDocData())     // 수정된 내용
                .docOpinion(request.getDocOpinion() != null ? request.getDocOpinion() : prev.getDocOpinion())
                .isEmergency(request.getIsEmergency() != null ? request.getIsEmergency() : prev.getIsEmergency())
                .isPublic(request.getIsPublic() != null ? request.getIsPublic() : prev.getIsPublic())          // 재기안 시 사용자가 새로 지정 가능, 미지정 시 이전 값
                .approvalStatus(ApprovalStatus.PENDING)
                .personalFolderId(prev.getPersonalFolderId())
                .deptFolderId(prev.getDeptFolderId())
                .previousDocId(prev.getDocId())   // 체인 링크
                .build();
        newDoc.assignDocNum(docNum);
        newDoc.markSubmitted();
        documentRepository.save(newDoc);

        /* 이전 반려 사유 조회 (history reason 용) — REJECTED 라인의 의견만 */
        List<ApprovalLine> prevLines = lineRepository.findByDocId_DocIdOrderByLineStep(docId);
        String rejectReason = prevLines.stream()
                .filter(l -> l.getApprovalLineStatus() == ApprovalLineStatus.REJECTED)
                .map(ApprovalLine::getLineComment)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);

        /* 이전 문서에 "재기안으로 대체됨" 이력 남김 (추적용) */
        historyRepository.save(ApprovalStatusHistory.builder()
                .docId(prev.getDocId())
                .companyId(companyId)
                .previousStatus(ApprovalStatus.REJECTED)
                .changedStatus(ApprovalStatus.REJECTED)
                .changedBy(empId)
                .changeByName(prev.getEmpName())
                .changeByDeptName(prev.getEmpDeptName())
                .changeByGrade(prev.getEmpGrade())
                .changeReason("재기안으로 대체됨 (새 docId=" + newDoc.getDocId() + ")")
                .changedAt(LocalDateTime.now())
                .build());

        /* 새 문서에 "재기안 기안" 이력 INSERT */
        historyRepository.save(ApprovalStatusHistory.builder()
                .docId(newDoc.getDocId())
                .companyId(companyId)
                .previousStatus(ApprovalStatus.PENDING)
                .changedStatus(ApprovalStatus.PENDING)
                .changedBy(empId)
                .changeByName(newDoc.getEmpName())
                .changeByDeptName(newDoc.getEmpDeptName())
                .changeByGrade(newDoc.getEmpGrade())
                .changeReason("재기안 (이전 docId=" + prev.getDocId()
                        + (rejectReason != null ? ", 이전 반려 사유: " + rejectReason : "") + ")")
                .changedAt(LocalDateTime.now())
                .build());

        /* 결재선: 요청에 있으면 그걸, 없으면 이전 결재선을 새 docId 로 복제 */
        if (request.getApprovalLines() != null && !request.getApprovalLines().isEmpty()) {
            saveApprovalLine(companyId, newDoc, request.getApprovalLines());
        } else {
            List<ApprovalLine> copied = prevLines.stream()
                    .map(src -> ApprovalLine.builder()
                            .companyId(companyId)
                            .docId(newDoc)
                            .empId(src.getEmpId())
                            .empName(src.getEmpName())
                            .empGrade(src.getEmpGrade())
                            .empDeptId(src.getEmpDeptId())
                            .empDeptName(src.getEmpDeptName())
                            .empTitle(src.getEmpTitle())
                            .approvalRole(src.getApprovalRole())
                            .lineStep(src.getLineStep())
                            .build())
                    .toList();
            lineRepository.saveAll(copied);
        }

        /* 첨부파일 복제 - row 만 새 docId 로 복사, MinIO objectName 은 공유 */
        List<ApprovalAttachment> copiedAttachments = attachmentRepository.findByDocId_DocId(docId).stream()
                .map(src -> ApprovalAttachment.builder()
                        .docId(newDoc)
                        .companyId(companyId)
                        .fileName(src.getFileName())
                        .fileSize(src.getFileSize())
                        .objectName(src.getObjectName())
                        .contentType(src.getContentType())
                        .build())
                .toList();
        if (!copiedAttachments.isEmpty()) {
            attachmentRepository.saveAll(copiedAttachments);
        }

        /* 기안자 자동분류 (SENT) - 새 문서 기준 */
        autoClassifyExecutor.classify(companyId, empId, SourceBoxType.SENT, newDoc);

        /* 결재선 전원 자동분류 (INBOX) - 새 문서 기준 */
        List<Long> receiverIds = lineRepository.findByDocId_DocIdOrderByLineStep(newDoc.getDocId())
                .stream().map(ApprovalLine::getEmpId).toList();
        receiverIds.forEach(receiverId ->
                autoClassifyExecutor.classify(companyId, receiverId, SourceBoxType.INBOX, newDoc));

        /* 결재라인 전원에게 재기안 알림 */
        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .empIds(receiverIds)
                .alarmType("APPROVAL")
                .alarmTitle(newDoc.getEmpDeptName() + " " + newDoc.getEmpName() + " "
                        + newDoc.getEmpGrade() + "이(가) 결재 문서를 재기안하였습니다.")
                .alarmContent("[" + newDoc.getDocNum() + "] " + newDoc.getDocTitle())
                .alarmLink("/approval")
                .alarmRefType("APPROVAL_DOCUMENT")
                .alarmRefId(newDoc.getDocId())
                .build());

        /* hr-service 에 docCreated 이벤트 발행 — 새 문서를 새 기안처럼 전파 */
        List<ApprovalLine> savedLines = lineRepository.findByDocId_DocIdOrderByLineStep(newDoc.getDocId());
        approvalEventPublisher.publishDocCreated(newDoc, savedLines, request.getHtmlContent());

        /* 재기안 시 신규로 추가되는 첨부 업로드 (이전 첨부는 위에서 row 복제됨) */
        if (files != null && !files.isEmpty()) {
            attachmentService.uploadAttachments(companyId, empId, newDoc.getDocId(), files);
        }

        return newDoc.getDocId();
    }

    /**
     * 상신 취소(회수) - PENDING → CANCELED 전환
     */
    @Transactional
    public void recallDocument(UUID companyId, Long empId, Long docId) {
        ApprovalDocument document = documentRepository.findByDocIdAndCompanyId(docId, companyId)
                .orElseThrow(() -> new BusinessException("문서를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        /* PENDING 외 상태는 State 가 throw */
        document.requireOpenForApproval();
        /* 본인 문서인지 확인 */
        if (!document.getEmpId().equals(empId)) {
            throw new BusinessException("본인의 문서만 회수할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        /* 결재자 중 한 명이라도 이미 승인했으면 회수 차단 */
        boolean anyApproved = lineRepository.findByDocId_DocIdOrderByLineStep(docId).stream()
                .anyMatch(line -> line.getApprovalLineStatus() == ApprovalLineStatus.APPROVED);
        if (anyApproved) {
            throw new BusinessException("이미 결재가 진행된 문서는 회수할 수 없습니다.");
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

        /* 결재선 조회 — 라인 취소 + 알림 수신자 수집을 한 번의 조회로 처리 */
        List<ApprovalLine> allLines = lineRepository.findByDocId_DocIdOrderByLineStep(document.getDocId());

        /* 아직 PENDING 인 결재자 라인 전부 CANCELED 로 종결 (라인-문서 상태 정합성 보장) */
        allLines.stream()
                .filter(l -> l.getApprovalRole() == ApprovalRole.APPROVER
                        && l.getApprovalLineStatus() == ApprovalLineStatus.PENDING)
                .forEach(ApprovalLine::cancel);

        /* hr-service 에 회수 이벤트 발행 — 초과근무/휴가 양식에만 실제 발행 (Publisher 내부 formName 분기) */
        approvalEventPublisher.publishResult(document, "CANCELED", empId, null);

        /*결재 라인 전원에게 알림 발행 */
        List<Long> receiverIds = allLines.stream().map(ApprovalLine::getEmpId).toList();

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

    /*본인의 임시 저장 문서 조회 — 상태 가드는 State 패턴에 위임 */
    private ApprovalDocument findOwnDraftDocument(UUID companyId, Long empId, Long docId) {
        ApprovalDocument document = documentRepository.findByDocIdAndCompanyId(docId, companyId).orElseThrow(() -> new BusinessException("문서를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        if (!document.getEmpId().equals(empId)) {
            throw new BusinessException("본인의 문서만 수정할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
        /* DRAFT 외 상태는 State 가 throw */
        document.requireDraftStage();
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
    /* 근태 정정 / 휴가 부여 신청 상신 시 결재선에 HR_ADMIN 또는 HR_SUPER_ADMIN 사원이 1명 이상 포함되었는지 검증 */
    private void validateHrApproverIncluded(UUID companyId, List<DocumentCreateRequest.ApprovalLineRequest> approvalLines) {
        AttendanceModifyHrMemberResDto hrMembersDto = hrServiceClient.getHrMembers(companyId);
        Set<Long> hrEmpIds = hrMembersDto.getHrMembers().stream()
                .map(AttendanceModifyHrMemberResDto.HrMember::getEmpId)
                .collect(java.util.stream.Collectors.toSet());

        boolean hasHr = approvalLines.stream()
                .anyMatch(line -> hrEmpIds.contains(line.getEmpId()));

        if (!hasHr) {
            throw new BusinessException("결재선에 인사 담당자(HR_ADMIN 이상 권한)가 1명 이상 포함되어야 합니다.");
        }
    }
}
