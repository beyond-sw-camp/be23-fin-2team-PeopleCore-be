package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.ApprovalSignatureResponseDto;
import com.peoplecore.approval.entity.ApprovalSignature;
import com.peoplecore.approval.repository.ApprovalSignatureRepository;
import com.peoplecore.common.entity.CommonAttachFile;
import com.peoplecore.common.repository.CommonAttachFileRepository;
import com.peoplecore.common.service.MinioService;
import com.peoplecore.exception.BusinessException;
import io.minio.StatObjectResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ApprovalSignatureService {
    private static final String ENTITY_TYPE = "SIGNATURE";
    private static final String URL_PREFIX = "/approval/signatures/";

    private final CommonAttachFileRepository attachFileRepository;
    private final ApprovalSignatureRepository signatureRepository;
    private final MinioService minioService;

    @Autowired
    public ApprovalSignatureService(CommonAttachFileRepository attachFileRepository, ApprovalSignatureRepository signatureRepository, MinioService minioService) {
        this.attachFileRepository = attachFileRepository;
        this.signatureRepository = signatureRepository;
        this.minioService = minioService;
    }

    /* 서명 조회 -> CommonAttachFile에서 파일, ApprovalSignature에서 managerId */
    public ApprovalSignatureResponseDto getSignature(UUID companyId, Long empId) {
        CommonAttachFile attachFile = attachFileRepository
                .findByCompanyIdAndEntityTypeAndEntityId(companyId, ENTITY_TYPE, empId)
                .orElseThrow(() -> new BusinessException("서명이 등록되어 있지 않습니다.", HttpStatus.NOT_FOUND));

        Long managerId = signatureRepository.findByCompanyIdAndSigEmpId(companyId, empId)
                .map(ApprovalSignature::getSigManagerId)
                .orElse(null);

        /* 백엔드 프록시 URL — attachId 를 v 쿼리로 붙여 캐시버스팅 */
        String fileUrl = URL_PREFIX + empId + "/file?v=" + attachFile.getAttachId();
        return ApprovalSignatureResponseDto.from(attachFile, managerId, fileUrl);
    }

    /**
     * 서명 등록/수정
     */
    @Transactional
    public ApprovalSignatureResponseDto createOrUpdate(UUID companyId, Long empId,
                                                       Long managerId, MultipartFile file) {
        // 파일 검증
        if (file == null || file.isEmpty()) {
            throw new BusinessException("파일을 선택해주세요.", HttpStatus.BAD_REQUEST);
        }
        if (!file.getContentType().startsWith("image/")) {
            throw new BusinessException("이미지 파일만 업로드 가능합니다.", HttpStatus.BAD_REQUEST);
        }

        String objectName = String.format("signatures/%s/%d/%s_%s",
                companyId, empId, UUID.randomUUID(), file.getOriginalFilename());
        String fileType = resolveFileType(file.getContentType());

        // 기존 서명 있으면 삭제
        attachFileRepository.findByCompanyIdAndEntityTypeAndEntityId(companyId, ENTITY_TYPE, empId)
                .ifPresent(existing -> {
                    String oldObjectName = existing.getStoredFileName();
                    attachFileRepository.delete(existing);                    // DB 먼저 삭제
                    minioService.deleteObject(oldObjectName);                 // MinIO 나중에 삭제
                });
        signatureRepository.deleteByCompanyIdAndSigEmpId(companyId, empId);   // 고아 row 대비 항상 정리
        signatureRepository.flush();                                          // INSERT 전 DELETE 강제 반영 (UNIQUE 충돌 방지)
        /* MinIO 업로드 */
        minioService.uploadAttachment(objectName, file);

        /* CommonAttachFile 저장 (storedFileName = objectName) */
        CommonAttachFile attachFile = attachFileRepository.save(CommonAttachFile.builder()
                .companyId(companyId)
                .entityType(ENTITY_TYPE)
                .entityId(empId)
                .originalFileName(file.getOriginalFilename())
                .storedFileName(objectName)
                .fileUrl(objectName)
                .fileSize(file.getSize())
                .fileType(fileType)
                .build());

        /* ApprovalSignature 이력 저장 */
        signatureRepository.save(ApprovalSignature.builder()
                .companyId(companyId)
                .sigEmpId(empId)
                .sigUrl(objectName)
                .sigManagerId(managerId)
                .build());

        /* 백엔드 프록시 URL 반환 (attachId 캐시버스팅) */
        String fileUrl = URL_PREFIX + empId + "/file?v=" + attachFile.getAttachId();
        return ApprovalSignatureResponseDto.from(attachFile, managerId, fileUrl);
    }

    /**
     * 서명 삭제
     */
    @Transactional
    public void delete(UUID companyId, Long empId) {
        CommonAttachFile attachFile = attachFileRepository
                .findByCompanyIdAndEntityTypeAndEntityId(companyId, ENTITY_TYPE, empId)
                .orElseThrow(() -> new BusinessException("서명이 등록되어 있지 않습니다.", HttpStatus.NOT_FOUND));

        String oldObjectName = attachFile.getStoredFileName();
        attachFileRepository.delete(attachFile);
        signatureRepository.deleteByCompanyIdAndSigEmpId(companyId, empId);
        minioService.deleteObject(oldObjectName);
    }

    /*파일 타입 결정 */
    private String resolveFileType(String contentType) {
        if (contentType != null && contentType.startsWith("image/")) return "IMAGE";
        return "ETC";
    }

    /**
     * 서명 이미지 프록시 다운로드 (회사 내 인증된 사용자)
     */
    public ResponseEntity<Resource> downloadFile(UUID companyId, Long empId) {
        /* 회사 + 사원 기준 서명 첨부 메타 조회 (없으면 404) */
        CommonAttachFile attachFile = attachFileRepository
                .findByCompanyIdAndEntityTypeAndEntityId(companyId, ENTITY_TYPE, empId)
                .orElseThrow(() -> new BusinessException("서명이 등록되어 있지 않습니다.", HttpStatus.NOT_FOUND));

        String objectName = attachFile.getStoredFileName();
        /* MinIO 객체 메타데이터 조회 (content-type/size) */
        StatObjectResponse stat = minioService.stat(objectName);
        /* MinIO 객체 스트림 획득 */
        InputStream stream = minioService.download(objectName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(stat.contentType()));
        headers.setContentLength(stat.size());
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline");
        /* URL ?v= 쿼리로 cache-busting → 강캐싱 안전 */
        headers.setCacheControl("public, max-age=86400");
        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(stream));
    }
}
