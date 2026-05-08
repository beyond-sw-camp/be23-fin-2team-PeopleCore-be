package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.AttachmentListResponse;
import com.peoplecore.approval.dto.AttachmentResponse;
import com.peoplecore.approval.entity.ApprovalAttachment;
import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.repository.ApprovalAttachmentRepository;
import com.peoplecore.approval.repository.ApprovalDocumentRepository;
import com.peoplecore.approval.repository.ApprovalLineRepository;
import com.peoplecore.common.service.MinioService;
import com.peoplecore.exception.BusinessException;
import io.minio.StatObjectResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@Slf4j
public class ApprovalAttachmentService {

    private static final String URL_PREFIX = "/approval/document/attachments/";

    private final ApprovalAttachmentRepository attachmentRepository;
    private final ApprovalDocumentRepository documentRepository;
    private final ApprovalLineRepository lineRepository;
    private final MinioService minioService;

    @Autowired
    public ApprovalAttachmentService(ApprovalAttachmentRepository attachmentRepository,
                                     ApprovalDocumentRepository documentRepository,
                                     ApprovalLineRepository lineRepository,
                                     MinioService minioService) {
        this.attachmentRepository = attachmentRepository;
        this.documentRepository = documentRepository;
        this.lineRepository = lineRepository;
        this.minioService = minioService;
    }

    /**
     * 첨부파일 업로드 — 문서에 여러 파일을 한번에 첨부
     * MinIO에 파일 저장 후 메타데이터를 DB에 INSERT
     * objectName 규칙: attachments/{companyId}/{docId}/{UUID}_{원본파일명}
     */
    @Transactional
    public List<AttachmentResponse> uploadAttachments(UUID companyId, Long empId, Long docId, List<MultipartFile> files) {
        log.info("[첨부 업로드 시작] docId={}, empId={}, fileCount={}", docId, empId, files.size());

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
            log.info("[MinIO 업로드 완료] objectName={}, fileName={}, size={}",
                    objectName, file.getOriginalFilename(), file.getSize());

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
            log.info("[DB 저장 완료] attachId={}, docId={}", attachment.getAttachId(), docId);

            /* 백엔드 프록시 URL 반환 (브라우저가 해당 경로로 GET → 백엔드가 MinIO 에서 스트림) */
            String fileUrl = URL_PREFIX + attachment.getAttachId() + "/file";
            responses.add(AttachmentResponse.from(attachment, fileUrl));
        }

        log.info("[첨부 업로드 전체 완료] docId={}, savedCount={}", docId, responses.size());
        return responses;
    }

    /** 문서의 첨부파일 목록 조회 (URL 없이) */
    public List<AttachmentListResponse> getAttachments(Long docId) {
        List<ApprovalAttachment> attachments = attachmentRepository.findByDocId_DocId(docId);
        return attachments.stream()
                .map(AttachmentListResponse::from)
                .toList();
    }

    /** 첨부파일 다운로드 URL 발급 (단건) - 기안자 또는 결재라인(결재/참조/열람) 본인만 통과, 그 외 403 */
    public String getDownloadUrl(Long empId, Long attachId) {
        ApprovalAttachment attachment = attachmentRepository.findWithDocById(attachId)
                .orElseThrow(() -> new BusinessException("첨부파일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        /* 권한 가드 - 기안자 또는 결재라인 본인만 통과 */
        ApprovalDocument doc = attachment.getDocId();
        boolean isDrafter = doc.getEmpId().equals(empId);
        boolean inLine = lineRepository.findByDocId_DocIdAndEmpId(doc.getDocId(), empId).isPresent();
        if (!isDrafter && !inLine) {
            throw new BusinessException("첨부파일에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        /* 백엔드 프록시 URL 반환 (실제 다운로드는 GET /attachments/{attachId}/file) */
        return URL_PREFIX + attachId + "/file";
    }

    /**
     * 첨부파일 프록시 다운로드 - 권한 검증 후 MinIO 스트림 응답
     */
    public ResponseEntity<Resource> downloadFile(Long empId, Long attachId) {
        ApprovalAttachment attachment = attachmentRepository.findWithDocById(attachId)
                .orElseThrow(() -> new BusinessException("첨부파일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        /* 권한 가드 - 기안자 또는 결재라인 본인만 통과 */
        ApprovalDocument doc = attachment.getDocId();
        boolean isDrafter = doc.getEmpId().equals(empId);
        boolean inLine = lineRepository.findByDocId_DocIdAndEmpId(doc.getDocId(), empId).isPresent();
        if (!isDrafter && !inLine) {
            throw new BusinessException("첨부파일에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        String objectName = attachment.getObjectName();
        /* MinIO 메타 + 스트림 획득 */
        StatObjectResponse stat = minioService.stat(objectName);
        InputStream stream = minioService.download(objectName);

        HttpHeaders headers = new HttpHeaders();
        /* DB 저장된 contentType 우선, 없으면 stat 의 값 사용 */
        String contentType = attachment.getContentType() != null
                ? attachment.getContentType()
                : stat.contentType();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(stat.size());
        /* 한글 파일명 RFC 5987 인코딩으로 attachment 다운로드 */
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(attachment.getFileName(), StandardCharsets.UTF_8)
                .build();
        headers.setContentDisposition(disposition);
        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(stream));
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
