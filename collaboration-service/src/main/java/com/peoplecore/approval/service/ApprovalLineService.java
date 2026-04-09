package com.peoplecore.approval.service;

import com.peoplecore.alarm.publisher.AlarmEventPublisher;
import com.peoplecore.approval.entity.*;
import com.peoplecore.approval.repository.ApprovalDocumentRepository;
import com.peoplecore.approval.repository.ApprovalLineRepository;
import com.peoplecore.approval.repository.ApprovalStatusHistoryRepository;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ApprovalLineService {
    private final ApprovalDocumentRepository documentRepository;
    private final ApprovalLineRepository lineRepository;
    private final ApprovalStatusHistoryRepository historyRepository;
    private final AlarmEventPublisher alarmEventPublisher;

    @Autowired
    public ApprovalLineService(ApprovalDocumentRepository documentRepository, ApprovalLineRepository lineRepository, ApprovalStatusHistoryRepository historyRepository, AlarmEventPublisher alarmEventPublisher) {
        this.documentRepository = documentRepository;
        this.lineRepository = lineRepository;
        this.historyRepository = historyRepository;
        this.alarmEventPublisher = alarmEventPublisher;
    }

    /* 승인 처리 / 순차 처리(이전 단계가 approved여야만 승인 가능) / 마지막 결재자가 승인 해야만 문서상태 approved*/
    @Transactional
    public void approvalDocument(UUID companyId, Long empId, Long docId, String comment) {
        ApprovalDocument document = findPendingDocument(companyId, docId);

        /*결재선 조회 */
        ApprovalLine myLine = lineRepository.findByDocId_DocIdAndEmpId(docId, empId).orElseThrow(() -> new BusinessException("결재 권한이 없습니다.", HttpStatus.FORBIDDEN));

        /*결재자만 승인 가능 */
        if (myLine.getApprovalRole() != ApprovalRole.APPROVER) {
            throw new BusinessException("결재자만 승인 가능합니다. ");
        }

        /* 이미 처리된 결재선인지 확인*/
        if (myLine.getApprovalLineStatus() != ApprovalLineStatus.PENDING) {
            throw new BusinessException("이미 처리된 결재입니다. ");
        }

        /*순차 합의 */
        validatePreviousStepApproved(docId, myLine.getLineStep());

        /*결재선 승인 처리 + 읽음 처리 */
        myLine.approve();
        myLine.markRead();

        /*결재자 승인 이력*/
        historyRepository.save(ApprovalStatusHistory.builder()
                .docId(docId)
                .companyId(companyId)
                .previousStatus(ApprovalStatus.PENDING)
                .changedStatus(ApprovalStatus.PENDING)
                .changedBy(empId)
                .changeByName(myLine.getEmpName())
                .changeByDeptName(myLine.getEmpDeptName())
                .changeByGrade(myLine.getEmpGrade())
                .changeReason(comment != null ? comment : myLine.getLineStep() + "단계 승인")
                .changedAt(LocalDateTime.now())
                .build());

        /*모든 결재자가 승인했는지 확인 -> 문서 상태 변경*/
        List<ApprovalLine> approvalLines = lineRepository.findByDocId_DocIdOrderByLineStep(docId, ApprovalRole.APPROVER);
        boolean allApproved = approvalLines.stream().allMatch(line -> line.getApprovalLineStatus() == ApprovalLineStatus.APPROVED);

        if (allApproved) {
            /*상태 패턴 호출*/
            document.approve();

            /* 상태 변경 이력 저장*/
            historyRepository.save(ApprovalStatusHistory.builder()
                    .docId(docId)
                    .companyId(companyId)
                    .previousStatus(ApprovalStatus.PENDING)
                    .changedStatus(ApprovalStatus.APPROVED)
                    .changedBy(empId)
                    .changeByName(myLine.getEmpName())
                    .changeByDeptName(myLine.getEmpDeptName())
                    .changeByGrade(myLine.getEmpGrade())
                    .changeReason(comment != null ? comment : "최종 승인")
                    .changedAt(LocalDateTime.now())
                    .build());
        }

        /*기안자에게 승인 알림 발행 */
        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .empIds(List.of(document.getEmpId()))
                .alarmType("APPROVAL")
                .alarmTitle(myLine.getEmpDeptName() + " " + myLine.getEmpName() + " " + myLine.getEmpGrade() + "이(가) 결재 문서를 승인하였습니다.")
                .alarmContent("[" + document.getDocNum() + "] " + document.getDocTitle())
                .alarmLink("/approval")
                .alarmRefType("APPROVAL_DOCUMENT")
                .alarmRefId(document.getDocId())
                .build());

        /* 다음 결재자에게 알림 (최종 승인이 아닌 경우) */
        if (!allApproved) {
            List<Long> nextIds = approvalLines.stream()
                    .filter(line -> line.getLineStep() == myLine.getLineStep() + 1)
                    .map(ApprovalLine::getEmpId)
                    .toList();
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

    /* 결재 반려 처리 / 한명이라도 반려하면 전부 반려 */
    @Transactional
    public void rejectDocument(UUID companyId, Long empId, Long docId, String reason) {
        /*문서 찾기 */
        ApprovalDocument document = findPendingDocument(companyId, docId);
        /*내 결재 라인 찾기*/
        ApprovalLine myLine = lineRepository.findByDocId_DocIdAndEmpId(docId, empId).orElseThrow(() -> new BusinessException("결재 권한이 없습니다.", HttpStatus.FORBIDDEN));

        /*권한 있는지 확인 */
        if (myLine.getApprovalRole() != ApprovalRole.APPROVER) {
            throw new BusinessException("결재자만 반려할 수 있습니다. ");
        }
        /* 결재 문서 확인*/
        if (myLine.getApprovalLineStatus() != ApprovalLineStatus.PENDING) {
            throw new BusinessException("이미 처리된 문서입니다. ");
        }

        /*순차 합의 */
        validatePreviousStepApproved(docId, myLine.getLineStep());

        /*결재선 반려 처리 + 읽음 처리 */
        myLine.reject(reason);
        myLine.markRead();
        document.reject();

        /*상태 패턴 변경 이력 저장 */
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
                        .changeReason(reason)
                        .changedAt(LocalDateTime.now())
                        .build());

        /*기안자한테 반려 알림 발성 */
        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .empIds(List.of(document.getEmpId()))
                .alarmType("APPROVAL")
                .alarmTitle(myLine.getEmpDeptName() + " " + myLine.getEmpName() + " " + myLine.getEmpGrade() + "이(가) 결재 문서를 반려하였습니다.")
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


    /*pending 상태 문서 조회 (낙관적 락으로 방어)*/
    private ApprovalDocument findPendingDocument(UUID companyId, Long docId) {
        ApprovalDocument document = documentRepository.findByDocIdAndCompanyId(docId, companyId).orElseThrow(() -> new BusinessException("문서를 찾을 수 없습니다. ", HttpStatus.NOT_FOUND));

        if (document.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("결재 진행 중인 문서만 처리할 수 있스빈다, ");
        }
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

}
