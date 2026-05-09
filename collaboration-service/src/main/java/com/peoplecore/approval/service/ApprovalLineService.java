package com.peoplecore.approval.service;

import com.peoplecore.alarm.publisher.AlarmEventPublisher;
import com.peoplecore.approval.entity.*;
import com.peoplecore.approval.handler.ApprovalFormHandlerRegistry;
import com.peoplecore.approval.publisher.ApprovalEventPublisher;
import com.peoplecore.approval.repository.ApprovalDelegationRepository;
import com.peoplecore.approval.repository.ApprovalDocumentRepository;
import com.peoplecore.approval.repository.ApprovalLineRepository;
import com.peoplecore.approval.repository.ApprovalSignatureRepository;
import com.peoplecore.approval.repository.ApprovalStatusHistoryRepository;
import com.peoplecore.client.component.HrServiceClient;
import com.peoplecore.client.dto.EmployeeSimpleResDto;
import com.peoplecore.common.service.MinioService;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.exception.BusinessException;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ApprovalLineService {
    private final ApprovalDocumentRepository documentRepository;
    private final ApprovalLineRepository lineRepository;
    private final ApprovalStatusHistoryRepository historyRepository;
    private final ApprovalSignatureRepository signatureRepository;
    private final AlarmEventPublisher alarmEventPublisher;
    private final MinioService minioService;
    private final ApprovalEventPublisher approvalEventPublisher;
    private final HrServiceClient hrServiceClient;
    private final ApprovalFormHandlerRegistry formHandlerRegistry;
    private final ApprovalDelegationRepository delegationRepository;

    @Autowired
    public ApprovalLineService(ApprovalDocumentRepository documentRepository, ApprovalLineRepository lineRepository, ApprovalStatusHistoryRepository historyRepository, ApprovalSignatureRepository signatureRepository, AlarmEventPublisher alarmEventPublisher, MinioService minioService, ApprovalEventPublisher approvalEventPublisher, HrServiceClient hrServiceClient, ApprovalFormHandlerRegistry formHandlerRegistry, ApprovalDelegationRepository delegationRepository) {
        this.documentRepository = documentRepository;
        this.lineRepository = lineRepository;
        this.historyRepository = historyRepository;
        this.signatureRepository = signatureRepository;
        this.alarmEventPublisher = alarmEventPublisher;
        this.minioService = minioService;
        this.approvalEventPublisher = approvalEventPublisher;
        this.hrServiceClient = hrServiceClient;
        this.formHandlerRegistry = formHandlerRegistry;
        this.delegationRepository = delegationRepository;
    }

    /** 결재 권한 라인 조회 — 본인 라인 우선, 없으면 활성 위임 케이스(APPROVER 라인만)로 fallback */
    private LineWithDelegation findActorLine(UUID companyId, Long docId, Long empId) {
        Optional<ApprovalLine> direct = lineRepository.findByDocId_DocIdAndEmpId(docId, empId);
        if (direct.isPresent()) return new LineWithDelegation(direct.get(), null);
        /* 위임 케이스: 본인이 deleEmpId 인 활성 위임 목록을 돌며 그 위임자(empId)의 라인 탐색 */
        List<ApprovalDelegation> deles = delegationRepository.findActiveByDelegate(companyId, empId, LocalDate.now());
        for (ApprovalDelegation d : deles) {
            Optional<ApprovalLine> lineOpt = lineRepository.findByDocId_DocIdAndEmpId(docId, d.getEmpId());
            if (lineOpt.isPresent() && lineOpt.get().getApprovalRole() == ApprovalRole.APPROVER) {
                return new LineWithDelegation(lineOpt.get(), d);
            }
        }
        throw new BusinessException("결재 권한이 없습니다.", HttpStatus.FORBIDDEN);
    }

    private record LineWithDelegation(ApprovalLine line, ApprovalDelegation delegation) {
        boolean isDelegateAction() { return delegation != null; }
    }

    /** 다음 결재자(또는 그 위임받은자) 알림 수신자 산출. APPROVER 한정 IN 절 1회 조회. */
    private List<Long> nextReceivers(UUID companyId, List<ApprovalLine> approvalLines, int nextStep) {
        List<ApprovalLine> nextLines = approvalLines.stream()
                .filter(l -> l.getLineStep() == nextStep && l.getApprovalRole() == ApprovalRole.APPROVER)
                .toList();
        if (nextLines.isEmpty()) return List.of();
        List<Long> empIds = nextLines.stream().map(ApprovalLine::getEmpId).toList();
        Map<Long, Long> deleMap = delegationRepository.findActiveByEmps(companyId, empIds, LocalDate.now()).stream()
                .collect(Collectors.toMap(ApprovalDelegation::getEmpId, ApprovalDelegation::getDeleEmpId, (a, b) -> a));
        Set<Long> set = new LinkedHashSet<>();
        for (ApprovalLine line : nextLines) {
            set.add(line.getEmpId());
            Long dele = deleMap.get(line.getEmpId());
            if (dele != null) set.add(dele);
        }
        return new ArrayList<>(set);
    }

    /* 승인 처리 / 순차 처리(이전 단계가 approved여야만 승인 가능) / 마지막 결재자가 승인 해야만 문서상태 approved*/
    @Transactional
    public void approvalDocument(UUID companyId, Long empId, Long docId, String comment) {
        ApprovalDocument document = findPendingDocument(companyId, docId);

        /* 본인 라인 또는 위임 권한 라인 조회 */
        LineWithDelegation found = findActorLine(companyId, docId, empId);
        ApprovalLine myLine = found.line();
        boolean delegated = found.isDelegateAction();

        if (myLine.getApprovalRole() != ApprovalRole.APPROVER) {
            throw new BusinessException("결재자만 승인 가능합니다. ");
        }
        if (myLine.getApprovalLineStatus() != ApprovalLineStatus.PENDING) {
            throw new BusinessException("이미 처리된 결재입니다. ");
        }
        validatePreviousStepApproved(docId, myLine.getLineStep());

        /* 위임 처리면 라인 스냅샷을 위임받은자 정보로 swap (원 결재자 empId 는 lineDelegatedId 에 보존) */
        if (delegated) {
            ApprovalDelegation d = found.delegation();
            myLine.markDelegatedBy(d.getDeleEmpId(), d.getDeleName(), d.getDeleDeptName(), d.getDeleGrade(), d.getDeleTitle());
        }

        myLine.approve(comment);
        myLine.markRead();

        /* 이력 — 위임 처리 시 [위임결재] 라벨 prefix */
        String reasonText = comment != null ? comment : myLine.getLineStep() + "단계 승인";
        historyRepository.save(ApprovalStatusHistory.builder()
                .docId(docId)
                .companyId(companyId)
                .previousStatus(ApprovalStatus.PENDING)
                .changedStatus(ApprovalStatus.PENDING)
                .changedBy(empId)
                .changeByName(myLine.getEmpName())          // swap 후 이름 = 실제 처리자
                .changeByDeptName(myLine.getEmpDeptName())
                .changeByGrade(myLine.getEmpGrade())
                .changeReason((delegated ? "[위임결재] " : "") + reasonText)
                .changedAt(LocalDateTime.now())
                .build());

        /*모든 결재자가 승인했는지 확인 -> 문서 상태 변경*/
        List<ApprovalLine> approvalLines = lineRepository.findByDocId_DocIdOrderByLineStep(docId, ApprovalRole.APPROVER);
        boolean allApproved = approvalLines.stream().allMatch(line -> line.getApprovalLineStatus() == ApprovalLineStatus.APPROVED);

        if (allApproved) {
            document.approve();

            String docHtml = buildCompletedDocumentHtml(companyId, document, approvalLines);
            String objectName = String.format("completed/%s/%d/%s.html",
                    companyId, docId, document.getDocNum());
            minioService.uploadFormHtml(objectName, docHtml);
            document.assignDocUrl(objectName);

            historyRepository.save(ApprovalStatusHistory.builder()
                    .docId(docId)
                    .companyId(companyId)
                    .previousStatus(ApprovalStatus.PENDING)
                    .changedStatus(ApprovalStatus.APPROVED)
                    .changedBy(empId)
                    .changeByName(myLine.getEmpName())
                    .changeByDeptName(myLine.getEmpDeptName())
                    .changeByGrade(myLine.getEmpGrade())
                    .changeReason((delegated ? "[위임결재] " : "") + (comment != null ? comment : "최종 승인"))
                    .changedAt(LocalDateTime.now())
                    .build());

            formHandlerRegistry.find(document).ifPresent(h -> h.onApproved(document));
            approvalEventPublisher.publishResult(document, "APPROVED", empId, null);
        }

        /*기안자에게 승인 알림 발행 — 위임은 [위임] prefix */
        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .empIds(List.of(document.getEmpId()))
                .alarmType("APPROVAL")
                .alarmTitle((delegated ? "[위임] " : "")
                        + myLine.getEmpDeptName() + " " + myLine.getEmpName() + " " + myLine.getEmpGrade()
                        + "이(가) 결재 문서를 승인하였습니다.")
                .alarmContent("[" + document.getDocNum() + "] " + document.getDocTitle())
                .alarmLink("/approval")
                .alarmRefType("APPROVAL_DOCUMENT")
                .alarmRefId(document.getDocId())
                .build());

        /* 다음 결재자 + 그 위임받은자에게 알림 (최종 승인이 아닌 경우) */
        if (!allApproved) {
            List<Long> nextIds = nextReceivers(companyId, approvalLines, myLine.getLineStep() + 1);
            if (!nextIds.isEmpty()) {
                alarmEventPublisher.publisher(AlarmEvent.builder()
                        .companyId(companyId)
                        .empIds(nextIds)
                        .alarmType("APPROVAL")
                        .alarmTitle("결재할 문서가 도착하였습니다.")
                        .alarmLink("/approval")
                        .alarmRefType("APPROVAL_DOCUMENT")
                        .alarmRefId(docId)
                        .build());
            }
        }
    }

    /* 결재 반려 처리 / 한명이라도 반려하면 전부 반려 */
    @Transactional
    public void rejectDocument(UUID companyId, Long empId, Long docId, String reason) {
        ApprovalDocument document = findPendingDocument(companyId, docId);

        LineWithDelegation found = findActorLine(companyId, docId, empId);
        ApprovalLine myLine = found.line();
        boolean delegated = found.isDelegateAction();

        if (myLine.getApprovalRole() != ApprovalRole.APPROVER) {
            throw new BusinessException("결재자만 반려할 수 있습니다. ");
        }
        if (myLine.getApprovalLineStatus() != ApprovalLineStatus.PENDING) {
            throw new BusinessException("이미 처리된 문서입니다. ");
        }
        validatePreviousStepApproved(docId, myLine.getLineStep());

        /* 위임 처리면 라인 스냅샷을 위임받은자 정보로 swap */
        if (delegated) {
            ApprovalDelegation d = found.delegation();
            myLine.markDelegatedBy(d.getDeleEmpId(), d.getDeleName(), d.getDeleDeptName(), d.getDeleGrade(), d.getDeleTitle());
        }

        myLine.reject(reason);
        myLine.markRead();
        document.reject();

        /* 다른 PENDING 결재자 라인(같은 step 병렬합의자 + 뒷 step) 일괄 취소 */
        lineRepository.findByDocId_DocIdOrderByLineStep(docId).stream()
                .filter(l -> !l.getLineId().equals(myLine.getLineId())
                        && l.getApprovalRole() == ApprovalRole.APPROVER
                        && l.getApprovalLineStatus() == ApprovalLineStatus.PENDING)
                .forEach(ApprovalLine::cancel);

        approvalEventPublisher.publishResult(document, "REJECTED", empId, reason);

        historyRepository.save(
                ApprovalStatusHistory.builder()
                        .docId(docId)
                        .companyId(companyId)
                        .previousStatus(ApprovalStatus.PENDING)
                        .changedStatus(ApprovalStatus.REJECTED)
                        .changedBy(empId)
                        .changeByName(myLine.getEmpName())
                        .changeByDeptName(myLine.getEmpDeptName())
                        .changeByGrade(myLine.getEmpGrade())
                        .changeReason((delegated ? "[위임반려] " : "") + reason)
                        .changedAt(LocalDateTime.now())
                        .build());

        /*기안자한테 반려 알림 발성 — 위임은 [위임] prefix */
        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .empIds(List.of(document.getEmpId()))
                .alarmType("APPROVAL")
                .alarmTitle((delegated ? "[위임] " : "")
                        + myLine.getEmpDeptName() + " " + myLine.getEmpName() + " " + myLine.getEmpGrade()
                        + "이(가) 결재 문서를 반려하였습니다.")
                .alarmContent(reason)
                .alarmLink("/approval")
                .alarmRefType("APPROVAL_DOCUMENT")
                .alarmRefId(docId)
                .build());
    }

    /*결재자가 문서 확인 / 수신 접수*/
    @Transactional
    public void receiveDocument(UUID companyId, Long empId, Long docId) {
        ApprovalLine myLine = findMyLine(docId, empId);
        myLine.markRead();
    }

    /*열람 확인 */
    @Transactional
    public void readDocument(UUID companyId, Long empId, Long docId) {
        ApprovalLine myLine = findMyLine(docId, empId);
        if (myLine.getApprovalRole() != ApprovalRole.VIEWER) {
            throw new BusinessException("열람자만 열람 확인할 수 있습니다.");
        }
        myLine.markRead();
    }

    /*참조 확인 */
    @Transactional
    public void ccConfirm(UUID companyId, Long empId, Long docId) {
        ApprovalLine myLine = findMyLine(docId, empId);
        if (myLine.getApprovalRole() != ApprovalRole.REFERENCE) {
            throw new BusinessException("참조자만 참조 확인할 수 있습니다. ");
        }
        myLine.markRead();
    }


    /*pending 상태 문서 조회 (낙관적 락으로 방어) — 상태 가드는 State 패턴에 위임 */
    private ApprovalDocument findPendingDocument(UUID companyId, Long docId) {
        ApprovalDocument document = documentRepository.findByDocIdAndCompanyId(docId, companyId).orElseThrow(() -> new BusinessException("문서를 찾을 수 없습니다. ", HttpStatus.NOT_FOUND));
        document.requireOpenForApproval();
        return document;
    }

    /* 본인 결재선 조회 */
    private ApprovalLine findMyLine(Long docId, Long empId) {
        return lineRepository.findByDocId_DocIdAndEmpId(docId, empId).orElseThrow(() -> new BusinessException("결재 권한이 없습니다. ", HttpStatus.FORBIDDEN));
    }

    /*순차 합의 검증: 현재 단계 이전의 모든 결재자가 Approved인지 확인
     * 같은 단계(병렬 합의)는 동시 처리 허용 */
    private void validatePreviousStepApproved(Long docId, int myStep) {
        List<ApprovalLine> approvalLines = lineRepository.findByDocId_DocIdOrderByLineStep(docId, ApprovalRole.APPROVER);

        boolean previousAppApproved = approvalLines.stream().filter(line -> line.getLineStep() < myStep).allMatch(line -> line.getApprovalLineStatus() == ApprovalLineStatus.APPROVED);

        if (!previousAppApproved) {
            throw new BusinessException("이전 단계 결재가 완료되지 않습니다.");
        }
    }

    /**
     * 최종 승인 시 완성 문서 HTML 생성
     * 양식 HTML + 기안자 정보 + 결재선(서명 포함) + 입력 데이터를 하나의 HTML로 조립
     */
    private String buildCompletedDocumentHtml(UUID companyId, ApprovalDocument document, List<ApprovalLine> approvalLines) {
        /* 결재선 서명 일괄 조회 */
        List<Long> empIds = approvalLines.stream().map(ApprovalLine::getEmpId).distinct().toList();
        Map<Long, String> signatureMap = signatureRepository.findByCompanyIdAndSigEmpIdIn(companyId, empIds)
                .stream()
                .collect(Collectors.toMap(
                        ApprovalSignature::getSigEmpId,
                        sig -> minioService.getPublicUrl(sig.getSigUrl())
                ));

        /* 기안자 서명 */
        String drafterSigUrl = signatureRepository.findByCompanyIdAndSigEmpId(companyId, document.getEmpId())
                .map(sig -> minioService.getPublicUrl(sig.getSigUrl()))
                .orElse("");

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        html.append("<title>").append(document.getDocTitle()).append("</title>");
        html.append("<style>body{font-family:'Malgun Gothic',sans-serif;margin:20px;}");
        html.append(".header{border-bottom:2px solid #333;padding-bottom:10px;margin-bottom:20px;}");
        html.append(".info-table{width:100%;border-collapse:collapse;margin-bottom:20px;}");
        html.append(".info-table td,.info-table th{border:1px solid #ddd;padding:8px;text-align:left;}");
        html.append(".info-table th{background:#f5f5f5;width:120px;}");
        html.append(".approval-table{width:100%;border-collapse:collapse;margin-bottom:20px;text-align:center;}");
        html.append(".approval-table td,.approval-table th{border:1px solid #ddd;padding:8px;}");
        html.append(".approval-table th{background:#f5f5f5;}");
        html.append(".signature{max-width:80px;max-height:40px;}");
        html.append(".content{margin-top:20px;}</style></head><body>");

        /* 문서 헤더 */
        html.append("<div class='header'>");
        html.append("<h2>").append(document.getDocTitle()).append("</h2>");
        html.append("<p>문서번호: ").append(document.getDocNum()).append("</p>");
        html.append("</div>");

        /* 기안자 정보 */
        html.append("<table class='info-table'>");
        html.append("<tr><th>기안자</th><td>").append(document.getEmpName()).append("</td>");
        html.append("<th>부서</th><td>").append(document.getEmpDeptName()).append("</td></tr>");
        html.append("<tr><th>직급</th><td>").append(document.getEmpGrade()).append("</td>");
        html.append("<th>기안일</th><td>").append(document.getDocSubmittedAt()).append("</td></tr>");
        if (document.getDocOpinion() != null && !document.getDocOpinion().isBlank()) {
            html.append("<tr><th>기안의견</th><td colspan='3'>").append(document.getDocOpinion()).append("</td></tr>");
        }
        html.append("</table>");

        /* 결재선 */
        html.append("<table class='approval-table'><tr><th>구분</th><th>기안</th>");
        for (ApprovalLine line : approvalLines) {
            html.append("<th>").append(line.getLineStep()).append("단계</th>");
        }
        html.append("</tr>");

        /* 직급 행 */
        html.append("<tr><td>직급</td><td>").append(document.getEmpGrade()).append("</td>");
        for (ApprovalLine line : approvalLines) {
            html.append("<td>").append(line.getEmpGrade()).append("</td>");
        }
        html.append("</tr>");

        /* 이름 행 */
        html.append("<tr><td>성명</td><td>").append(document.getEmpName()).append("</td>");
        for (ApprovalLine line : approvalLines) {
            html.append("<td>").append(line.getEmpName()).append("</td>");
        }
        html.append("</tr>");

        /* 서명 행 */
        html.append("<tr><td>서명</td><td>");
        if (!drafterSigUrl.isEmpty()) {
            html.append("<img class='signature' src='").append(drafterSigUrl).append("'/>");
        }
        html.append("</td>");
        for (ApprovalLine line : approvalLines) {
            String sigUrl = signatureMap.get(line.getEmpId());
            html.append("<td>");
            if (sigUrl != null) {
                html.append("<img class='signature' src='").append(sigUrl).append("'/>");
            }
            html.append("</td>");
        }
        html.append("</tr>");

        /* 상태 행 */
        html.append("<tr><td>상태</td><td>기안</td>");
        for (ApprovalLine line : approvalLines) {
            html.append("<td>").append(line.getApprovalLineStatus().name()).append("</td>");
        }
        html.append("</tr></table>");

        /* 양식 내용 */
        html.append("<div class='content'>");
        html.append(document.getFormId().getFormHtml());
        html.append("</div>");

        html.append("</body></html>");
        return html.toString();
    }

    /* 전결 처리 — 본인(또는 위임받은) 라인 + 이후 모든 PENDING 라인 일괄 승인 */
    @Transactional
    public void approvalDocumentAll(UUID companyId, Long empId, Long docId, String comment) {
        /* 처리자 사원 정보 — 위임이면 위임받은자 정보가 들어옴 */
        EmployeeSimpleResDto myInfo = hrServiceClient.getEmployees(List.of(empId)).stream().findFirst().orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        ApprovalDocument document = findPendingDocument(companyId, docId);

        /* 본인 라인 또는 위임 권한 라인 조회 */
        LineWithDelegation found = findActorLine(companyId, docId, empId);
        ApprovalLine approvalLine = found.line();
        boolean delegated = found.isDelegateAction();

        if (approvalLine.getApprovalRole() != ApprovalRole.APPROVER) {
            throw new CustomException(ErrorCode.APPROVAL_NOT_ROLE);
        }
        if (approvalLine.getApprovalLineStatus() != ApprovalLineStatus.PENDING) {
            throw new CustomException(ErrorCode.APPROVAL_ALREADY_APPROVED);
        }
        validatePreviousStepApproved(docId, approvalLine.getLineStep());

        /* 위임 처리면 actor 라인 스냅샷을 위임받은자로 swap (원 결재자 empId/이름은 lineDelegated* 에 보존) */
        if (delegated) {
            ApprovalDelegation d = found.delegation();
            approvalLine.markDelegatedBy(d.getDeleEmpId(), d.getDeleName(),
                    d.getDeleDeptName(), d.getDeleGrade(), d.getDeleTitle());
        }

        /*전체 결재선 조회 */
        List<ApprovalLine> approvalLines = lineRepository.findByDocId_DocIdOrderByLineStep(docId, ApprovalRole.APPROVER);

        /* 본인 포함 이후 모든 pending 결재선 일괄 승인
         *  - actor 라인: 입력 comment(미입력 시 "전결"), 위임이면 history reason 에 [위임전결] prefix
         *  - 이후 라인: 자동 "전결 - N단계" */
        approvalLines.stream().filter(l -> l.getLineStep() >= approvalLine.getLineStep() && l.getApprovalLineStatus() == ApprovalLineStatus.PENDING).forEach(l -> {
            String lineComment = l.getLineId().equals(approvalLine.getLineId())
                    ? (comment != null ? comment : "전결")
                    : "전결 - " + l.getLineStep() + "단계";
            l.approve(lineComment);
            l.markRead();
            boolean isActorLine = l.getLineId().equals(approvalLine.getLineId());
            historyRepository.save(ApprovalStatusHistory.builder()
                    .docId(docId)
                    .companyId(companyId)
                    .previousStatus(ApprovalStatus.PENDING)
                    .changedStatus(ApprovalStatus.APPROVED)
                    .changedBy(empId)
                    .changeByName(myInfo.getEmpName())
                    .changeByDeptName(myInfo.getDeptName())
                    .changeByGrade(myInfo.getGradeName())
                    .changeReason((delegated && isActorLine ? "[위임전결] " : "") + "전결 - " + l.getLineStep() + "단계")
                    .changedAt(LocalDateTime.now())
                    .build());
        });

        boolean allApproved = approvalLines.stream().allMatch(l -> l.getApprovalLineStatus() == ApprovalLineStatus.APPROVED);

        if (allApproved) {
            document.approve();

            String docHTML = buildCompletedDocumentHtml(companyId, document, approvalLines);
            String objectName = String.format("completed/%s/%d/%s.html", companyId, docId, document.getDocNum());
            minioService.uploadFormHtml(objectName, docHTML);
            document.assignDocUrl(objectName);

            historyRepository.save(ApprovalStatusHistory.builder()
                    .docId(docId)
                    .companyId(companyId)
                    .previousStatus(ApprovalStatus.PENDING)
                    .changedStatus(ApprovalStatus.APPROVED)
                    .changedBy(empId)
                    .changeByName(myInfo.getEmpName())
                    .changeByDeptName(myInfo.getDeptName())
                    .changeByGrade(myInfo.getGradeName())
                    .changeReason((delegated ? "[위임전결] " : "") + "전결 처리")
                    .changedAt(LocalDateTime.now())
                    .build());

            formHandlerRegistry.find(document).ifPresent(h -> h.onApproved(document));
            approvalEventPublisher.publishResult(document, "APPROVED", empId, null);
        }

        /*기안자한테 승인 알림 발행 — 위임은 [위임] prefix */
        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .empIds(List.of(document.getEmpId()))
                .alarmType("APPROVAL")
                .alarmTitle((delegated ? "[위임] " : "")
                        + myInfo.getDeptName() + " " + myInfo.getEmpName() + " " + myInfo.getGradeName()
                        + "이(가) 결재 문서를 승인하였습니다.")
                .alarmContent("[" + document.getDocNum() + "]" + document.getDocTitle())
                .alarmLink("/approval")
                .alarmRefId(docId)
                .alarmRefType("APPROVAL_DOCUMENT")
                .build());
    }

}
