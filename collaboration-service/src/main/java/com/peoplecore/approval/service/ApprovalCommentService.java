package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.ApprovalCommentRequest;
import com.peoplecore.approval.dto.ApprovalCommentResponse;
import com.peoplecore.alram.publiher.AlarmEventPublisher;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalLine;
import com.peoplecore.approval.repository.ApprovalCommentRepository;
import com.peoplecore.approval.repository.ApprovalDocumentRepository;
import com.peoplecore.approval.repository.ApprovalLineRepository;
import com.peoplecore.client.component.HrCacheService;
import com.peoplecore.common.entity.CommonComment;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalCommentService {

    private static final String ENTITY_TYPE = "APPROVAL_DOCUMENT";

    private final ApprovalCommentRepository commentRepository;
    private final ApprovalDocumentRepository documentRepository;
    private final ApprovalLineRepository lineRepository;
    private final HrCacheService hrCacheService;
    private final AlarmEventPublisher alarmEventPublisher;

    /** 댓글 목록 조회 */
    public List<ApprovalCommentResponse> getComments(UUID companyId, Long docId) {
        return commentRepository
                .findByCompanyIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(companyId, ENTITY_TYPE, docId)
                .stream()
                .map(ApprovalCommentResponse::from)
                .toList();
    }

    /** 댓글 작성 */
    @Transactional
    public ApprovalCommentResponse create(UUID companyId, Long empId, String empName,
                                          Long deptId, String empGrade, String empTitle,
                                          Long docId, ApprovalCommentRequest request) {
        validateCommentPermission(companyId, empId, docId);

        String empDeptName = hrCacheService.getDept(deptId).getDeptName();

        CommonComment comment = CommonComment.builder()
                .companyId(companyId)
                .entityType(ENTITY_TYPE)
                .entityId(docId)
                .parentCommentId(request.getParentCommentId())
                .empId(empId)
                .empName(empName)
                .empDeptName(empDeptName)
                .empGradeName(empGrade)
                .empTitleName(empTitle != null ? empTitle : "")
                .content(request.getContent())
                .build();

        commentRepository.save(comment);

        /* 기안자 + 결재라인 전원에게 댓글 알림 (본인 제외) */
        ApprovalDocument document = documentRepository.findByDocIdAndCompanyId(docId, companyId)
                .orElseThrow(() -> new BusinessException("문서를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        List<Long> receiverIds = new ArrayList<>();
        receiverIds.add(document.getEmpId());
        lineRepository.findByDocId_DocIdOrderByLineStep(docId)
                .stream()
                .map(ApprovalLine::getEmpId)
                .forEach(receiverIds::add);
        receiverIds.remove(empId);

        if (!receiverIds.isEmpty()) {
            String contentPreview = request.getContent().length() > 30
                    ? request.getContent().substring(0, 30) + "..."
                    : request.getContent();

            alarmEventPublisher.publisher(AlarmEvent.builder()
                    .companyId(companyId)
                    .empIds(receiverIds.stream().distinct().toList())
                    .alarmType("APPROVAL")
                    .alarmTitle(empName + "이(가) 댓글을 남겼습니다.")
                    .alarmContent("[" + document.getDocTitle() + "] " + contentPreview)
                    .alarmLink("/approval")
                    .alarmRefType("APPROVAL_DOCUMENT")
                    .alarmRefId(docId)
                    .build());
        }

        return ApprovalCommentResponse.from(comment);
    }

    /** 댓글 수정 */
    @Transactional
    public ApprovalCommentResponse update(UUID companyId, Long empId, Long commentId,
                                          ApprovalCommentRequest request) {
        CommonComment comment = findOwnComment(companyId, empId, commentId);
        comment.updateContent(request.getContent());
        return ApprovalCommentResponse.from(comment);
    }

    /** 댓글 삭제 */
    @Transactional
    public void delete(UUID companyId, Long empId, Long commentId) {
        CommonComment comment = findOwnComment(companyId, empId, commentId);
        commentRepository.delete(comment);
    }

    /** 권한 검증: 기안자 또는 결재라인에 포함된 사람만 */
    private void validateCommentPermission(UUID companyId, Long empId, Long docId) {
        ApprovalDocument document = documentRepository.findByDocIdAndCompanyId(docId, companyId)
                .orElseThrow(() -> new BusinessException("문서를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        if (document.getEmpId().equals(empId)) return;

        boolean inLine = lineRepository.findByDocId_DocIdAndEmpId(docId, empId).isPresent();
        if (!inLine) {
            throw new BusinessException("기안자 또는 결재라인에 포함된 사람만 댓글을 작성할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
    }

    /** 본인 댓글 조회 */
    private CommonComment findOwnComment(UUID companyId, Long empId, Long commentId) {
        CommonComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("댓글을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        if (!comment.getCompanyId().equals(companyId) || !comment.getEmpId().equals(empId)) {
            throw new BusinessException("본인의 댓글만 수정/삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
        return comment;
    }
}
