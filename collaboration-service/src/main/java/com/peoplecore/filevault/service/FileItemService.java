package com.peoplecore.filevault.service;

import com.peoplecore.exception.BusinessException;
import com.peoplecore.filevault.audit.AuditAction;
import com.peoplecore.filevault.audit.FileVaultAuditEvent;
import com.peoplecore.filevault.audit.ResourceType;
import com.peoplecore.filevault.dto.*;
import com.peoplecore.filevault.entity.FileFolder;
import com.peoplecore.filevault.entity.FileItem;
import com.peoplecore.filevault.repository.FileFolderRepository;
import com.peoplecore.filevault.repository.FileItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FileItemService {

    private final FileItemRepository fileItemRepository;
    private final FileFolderRepository folderRepository;
    private final FileVaultMinioService minioService;
    private final ApplicationEventPublisher eventPublisher;

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

        FileItem saved = fileItemRepository.save(fileItem);
        Map<String, Object> meta = new HashMap<>();
        meta.put("size", saved.getSizeBytes());
        meta.put("mimeType", saved.getMimeType());
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.UPLOAD_FILE)
            .resourceType(ResourceType.FILE)
            .resourceId(saved.getId())
            .resourceName(saved.getName())
            .parentFolderId(saved.getFolderId())
            .parentName(lookupFolderName(saved.getFolderId()))
            .metadata(meta)
            .build());
        return FileResponse.from(saved);
    }

    @Transactional
    public String generateDownloadUrl(Long fileId, boolean attachment) {
        FileItem file = findActiveFile(fileId);
        String url = attachment
            ? minioService.generatePresignedGetUrl(file.getStorageKey(), 5, file.getName())
            : minioService.generatePresignedGetUrl(file.getStorageKey(), 5);
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.DOWNLOAD_FILE)
            .resourceType(ResourceType.FILE)
            .resourceId(file.getId())
            .resourceName(file.getName())
            .parentFolderId(file.getFolderId())
            .parentName(lookupFolderName(file.getFolderId()))
            .metadata(Map.of("expiresInMinutes", 5))
            .build());
        return url;
    }

    @Transactional
    public FileResponse renameFile(Long fileId, String newName) {
        FileItem file = findActiveFile(fileId);
        String oldName = file.getName();
        file.rename(newName);
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.RENAME_FILE)
            .resourceType(ResourceType.FILE)
            .resourceId(file.getId())
            .resourceName(newName)
            .parentFolderId(file.getFolderId())
            .parentName(lookupFolderName(file.getFolderId()))
            .changes(Map.of("from", oldName, "to", newName))
            .build());
        return FileResponse.from(file);
    }

    @Transactional
    public FileResponse moveFile(Long fileId, Long newFolderId) {
        FileItem file = findActiveFile(fileId);
        Long oldFolderId = file.getFolderId();
        file.moveTo(newFolderId);
        Map<String, Object> changes = new HashMap<>();
        changes.put("fromFolderId", oldFolderId);
        changes.put("toFolderId", newFolderId);
        changes.put("fromFolderName", lookupFolderName(oldFolderId));
        changes.put("toFolderName", lookupFolderName(newFolderId));
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.MOVE_FILE)
            .resourceType(ResourceType.FILE)
            .resourceId(file.getId())
            .resourceName(file.getName())
            .parentFolderId(newFolderId)
            .parentName(lookupFolderName(newFolderId))
            .changes(changes)
            .build());
        return FileResponse.from(file);
    }

    @Transactional
    public void softDelete(Long fileId) {
        FileItem file = findActiveFile(fileId);
        file.softDelete();
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.SOFT_DELETE_FILE)
            .resourceType(ResourceType.FILE)
            .resourceId(file.getId())
            .resourceName(file.getName())
            .parentFolderId(file.getFolderId())
            .parentName(lookupFolderName(file.getFolderId()))
            .build());
    }

    @Transactional
    public void restore(Long fileId) {
        FileItem file = fileItemRepository.findById(fileId)
            .orElseThrow(() -> new BusinessException("파일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        file.restore();
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.RESTORE_FILE)
            .resourceType(ResourceType.FILE)
            .resourceId(file.getId())
            .resourceName(file.getName())
            .parentFolderId(file.getFolderId())
            .parentName(lookupFolderName(file.getFolderId()))
            .build());
    }

    @Transactional
    public void permanentDelete(Long fileId) {
        FileItem file = fileItemRepository.findById(fileId)
            .orElseThrow(() -> new BusinessException("파일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        Long folderId = file.getFolderId();
        String name = file.getName();
        minioService.deleteObject(file.getStorageKey());
        fileItemRepository.delete(file);
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.PERMANENT_DELETE_FILE)
            .resourceType(ResourceType.FILE)
            .resourceId(fileId)
            .resourceName(name)
            .parentFolderId(folderId)
            .parentName(lookupFolderName(folderId))
            .build());
    }

    private String lookupFolderName(Long folderId) {
        if (folderId == null) return "(루트)";
        return folderRepository.findById(folderId)
            .map(FileFolder::getName)
            .orElse("(unknown)");
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
