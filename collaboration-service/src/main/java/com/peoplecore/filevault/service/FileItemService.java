package com.peoplecore.filevault.service;

import com.peoplecore.exception.BusinessException;
import com.peoplecore.filevault.dto.*;
import com.peoplecore.filevault.entity.FileItem;
import com.peoplecore.filevault.repository.FileItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FileItemService {

    private final FileItemRepository fileItemRepository;
    private final FileVaultMinioService minioService;

    public List<FileResponse> listByFolder(Long folderId) {
        return fileItemRepository.findByFolderIdAndDeletedAtIsNull(folderId)
            .stream()
            .map(FileResponse::from)
            .toList();
    }

    public UploadUrlResponse generateUploadUrl(UUID companyId, Long folderId, UploadUrlRequest request) {
        String storageKey = String.format("c%s/f%d/%s-%s",
            companyId, folderId, UUID.randomUUID(), request.getFileName());

        String uploadUrl = minioService.generatePresignedPutUrl(storageKey, 10);

        return UploadUrlResponse.builder()
            .uploadUrl(uploadUrl)
            .storageKey(storageKey)
            .build();
    }

    @Transactional
    public FileResponse confirmUpload(Long empId, FileUploadConfirmRequest request) {
        long actualSize = minioService.headObject(request.getStorageKey());
        if (actualSize < 0) {
            throw new BusinessException("MinIO에 파일이 존재하지 않습니다. 업로드를 다시 시도해주세요.",
                HttpStatus.BAD_REQUEST);
        }

        FileItem fileItem = FileItem.builder()
            .folderId(request.getFolderId())
            .name(request.getName())
            .mimeType(request.getMimeType())
            .sizeBytes(actualSize)
            .storageKey(request.getStorageKey())
            .uploadedBy(empId)
            .build();

        return FileResponse.from(fileItemRepository.save(fileItem));
    }

    public String generateDownloadUrl(Long fileId) {
        FileItem file = findActiveFile(fileId);
        return minioService.generatePresignedGetUrl(file.getStorageKey(), 5);
    }

    @Transactional
    public FileResponse renameFile(Long fileId, String newName) {
        FileItem file = findActiveFile(fileId);
        file.rename(newName);
        return FileResponse.from(file);
    }

    @Transactional
    public FileResponse moveFile(Long fileId, Long newFolderId) {
        FileItem file = findActiveFile(fileId);
        file.moveTo(newFolderId);
        return FileResponse.from(file);
    }

    @Transactional
    public void softDelete(Long fileId) {
        FileItem file = findActiveFile(fileId);
        file.softDelete();
    }

    @Transactional
    public void restore(Long fileId) {
        FileItem file = fileItemRepository.findById(fileId)
            .orElseThrow(() -> new BusinessException("파일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        file.restore();
    }

    @Transactional
    public void permanentDelete(Long fileId) {
        FileItem file = fileItemRepository.findById(fileId)
            .orElseThrow(() -> new BusinessException("파일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        minioService.deleteObject(file.getStorageKey());
        fileItemRepository.delete(file);
    }

    private FileItem findActiveFile(Long fileId) {
        FileItem file = fileItemRepository.findById(fileId)
            .orElseThrow(() -> new BusinessException("파일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (file.isDeleted()) {
            throw new BusinessException("삭제된 파일입니다.", HttpStatus.GONE);
        }
        return file;
    }
}
