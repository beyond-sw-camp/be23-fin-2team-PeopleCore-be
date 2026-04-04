package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.AttachmentListResponse;
import com.peoplecore.approval.dto.AttachmentResponse;
import com.peoplecore.approval.entity.ApprovalAttachment;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.repository.ApprovalAttachmentRepository;
import com.peoplecore.approval.repository.ApprovalDocumentRepository;
import com.peoplecore.common.service.MinioService;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@Slf4j
public class ApprovalAttachmentService {

    private final ApprovalAttachmentRepository attachmentRepository;
    private final ApprovalDocumentRepository documentRepository;
    private final MinioService minioService;

    @Autowired
    public ApprovalAttachmentService(ApprovalAttachmentRepository attachmentRepository,
                                     ApprovalDocumentRepository documentRepository,
                                     MinioService minioService) {
        this.attachmentRepository = attachmentRepository;
        this.documentRepository = documentRepository;
        this.minioService = minioService;
    }

    /**
     * 첨부파일 업로드 — 문서에 여러 파일을 한번에 첨부
     * MinIO에 파일 저장 후 메타데이터를 DB에 INSERT
     * objectName 규칙: attachments/{companyId}/{docId}/{UUID}_{원본파일명}
     */
    @Transactional
    public List<AttachmentResponse> uploadAttachments(UUID companyId, Long empId, Long docId, List<MultipartFile> files) {
        ApprovalDocument document = documentRepository.findByDocIdAndCompanyId(docId, companyId)
                .orElseThrow(() -> new BusinessException("문서를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        /* 본인 문서인지 확인 */
        if (!document.getEmpId().equals(empId)) {
            throw new BusinessException("본인의 문서에만 파일을 첨부할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        List<AttachmentResponse> responses = new ArrayList<>();

        for (MultipartFile file : files) {
            /* MinIO 오브젝트 이름 생성 */
            String objectName = String.format("attachments/%s/%d/%s_%s",
                    companyId, docId, UUID.randomUUID(), file.getOriginalFilename());

            /* MinIO에 파일 업로드 */
            minioService.uploadAttachment(objectName, file);

            /* DB에 메타데이터 저장 */
            ApprovalAttachment attachment = ApprovalAttachment.builder()
                    .docId(document)
                    .companyId(companyId)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .objectName(objectName)
                    .contentType(file.getContentType())
                    .build();

            attachmentRepository.save(attachment);

            /* Pre-signed URL 포함하여 응답 */
            String fileUrl = minioService.getPresignedUrl(objectName);
            responses.add(AttachmentResponse.from(attachment, fileUrl));
        }

        return responses;
    }

    /** 문서의 첨부파일 목록 조회 (URL 없이) */
    public List<AttachmentListResponse> getAttachments(Long docId) {
        List<ApprovalAttachment> attachments = attachmentRepository.findByDocId_DocId(docId);
        return attachments.stream()
                .map(AttachmentListResponse::from)
                .toList();
    }

    /** 첨부파일 다운로드 URL 발급 (단건) */
    public String getDownloadUrl(Long attachId) {
        ApprovalAttachment attachment = attachmentRepository.findById(attachId)
                .orElseThrow(() -> new BusinessException("첨부파일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        return minioService.getPresignedUrl(attachment.getObjectName());
    }

    /** 첨부파일 단건 삭제 (MinIO + DB) */
    @Transactional
    public void deleteAttachment(UUID companyId, Long empId, Long attachId) {
        ApprovalAttachment attachment = attachmentRepository.findWithDocById(attachId).orElseThrow(() -> new BusinessException("첨부파일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        /* 본인 문서인지 확인 */
        if (!attachment.getDocId().getEmpId().equals(empId)) {
            throw new BusinessException("본인의 첨부파일만 삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        /* MinIO에서 파일 삭제 */
        minioService.deleteObject(attachment.getObjectName());

        /* DB에서 메타데이터 삭제 */
        attachmentRepository.delete(attachment);
    }

    /** 문서의 첨부파일 전체 삭제 (문서 삭제 시 호출) */
    @Transactional
    public void deleteAllAttachments(Long docId) {
        List<ApprovalAttachment> attachments = attachmentRepository.findByDocId_DocId(docId);
        for (ApprovalAttachment attachment : attachments) {
            minioService.deleteObject(attachment.getObjectName());
        }
        attachmentRepository.deleteByDocId_DocId(docId);
    }
}
