package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.ApprovalSignatureResponseDto;
import com.peoplecore.approval.entity.ApprovalSignature;
import com.peoplecore.approval.repository.ApprovalSignatureRepository;
import com.peoplecore.common.entity.CommonAttachFile;
import com.peoplecore.common.repository.CommonAttachFileRepository;
import com.peoplecore.common.service.MinioService;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ApprovalSignatureService {
    private static final String ENTITY_TYPE = "SIGNATURE";

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

        String presignedUrl = minioService.getPresignedUrl(attachFile.getStoredFileName());
        return ApprovalSignatureResponseDto.from(attachFile, managerId, presignedUrl);
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
        // MinIO 업로드
        minioService.uploadAttachment(objectName, file);
        String presignedUrl = minioService.getPresignedUrl(objectName);

        // CommonAttachFile 저장 (storedFileName = objectName, fileUrl = 조립된 공개 URL)
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

        // ApprovalSignature 이력 저장
        signatureRepository.save(ApprovalSignature.builder()
                .companyId(companyId)
                .sigEmpId(empId)
                .sigUrl(objectName)
                .sigManagerId(managerId)
                .build());



        return ApprovalSignatureResponseDto.from(attachFile, managerId,presignedUrl);
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
}
