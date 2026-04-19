package com.peoplecore.filevault.service;

import com.peoplecore.exception.BusinessException;
import com.peoplecore.filevault.audit.AuditAction;
import com.peoplecore.filevault.audit.FileVaultAuditEvent;
import com.peoplecore.filevault.audit.ResourceType;
import com.peoplecore.filevault.dto.FolderCreateRequest;
import com.peoplecore.filevault.dto.FolderResponse;
import com.peoplecore.filevault.entity.FileFolder;
import com.peoplecore.filevault.entity.FolderType;
import com.peoplecore.filevault.repository.FileFolderRepository;
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
public class FileFolderService {

    private final FileFolderRepository folderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<FolderResponse> listRootFolders(UUID companyId, FolderType type, Long empId) {
        List<FileFolder> roots = folderRepository
            .findByCompanyIdAndTypeAndParentFolderIdIsNullAndDeletedAtIsNull(companyId, type);
        if (type == FolderType.PERSONAL) {
            roots = roots.stream()
                .filter(f -> empId != null && empId.equals(f.getOwnerEmpId()))
                .toList();
        }
        return roots.stream().map(FolderResponse::from).toList();
    }

    public List<FolderResponse> listChildren(Long parentFolderId) {
        return folderRepository
            .findByParentFolderIdAndDeletedAtIsNull(parentFolderId)
            .stream()
            .map(FolderResponse::from)
            .toList();
    }

    public FolderResponse getFolder(Long folderId) {
        FileFolder folder = findActiveFolder(folderId);
        return FolderResponse.from(folder);
    }

    @Transactional
    public FolderResponse createFolder(UUID companyId, Long empId, FolderCreateRequest request) {
        if (request.getParentFolderId() != null) {
            findActiveFolder(request.getParentFolderId());
        }

        if (folderRepository.existsByParentFolderIdAndNameAndDeletedAtIsNull(
                request.getParentFolderId(), request.getName())) {
            throw new BusinessException("같은 이름의 폴더가 이미 존재합니다.", HttpStatus.CONFLICT);
        }

        FileFolder folder = FileFolder.builder()
            .companyId(companyId)
            .name(request.getName())
            .type(request.getType())
            .parentFolderId(request.getParentFolderId())
            .deptId(request.getDeptId())
            .ownerEmpId(request.getType() == FolderType.PERSONAL ? empId : null)
            .createdBy(empId)
            .build();

        FileFolder saved = folderRepository.save(folder);
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.CREATE_FOLDER)
            .resourceType(ResourceType.FOLDER)
            .resourceId(saved.getId())
            .resourceName(saved.getName())
            .parentFolderId(saved.getParentFolderId())
            .parentName(lookupFolderName(saved.getParentFolderId()))
            .build());
        return FolderResponse.from(saved);
    }

    @Transactional
    public FolderResponse renameFolder(Long folderId, String newName) {
        FileFolder folder = findActiveFolder(folderId);
        String oldName = folder.getName();
        folder.rename(newName);
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.RENAME_FOLDER)
            .resourceType(ResourceType.FOLDER)
            .resourceId(folder.getId())
            .resourceName(newName)
            .parentFolderId(folder.getParentFolderId())
            .parentName(lookupFolderName(folder.getParentFolderId()))
            .changes(Map.of("from", oldName, "to", newName))
            .build());
        return FolderResponse.from(folder);
    }

    @Transactional
    public FolderResponse moveFolder(Long folderId, Long newParentFolderId) {
        FileFolder folder = findActiveFolder(folderId);
        if (newParentFolderId != null) {
            findActiveFolder(newParentFolderId);
        }
        Long oldParentId = folder.getParentFolderId();
        folder.moveTo(newParentFolderId);
        Map<String, Object> changes = new HashMap<>();
        changes.put("fromParentId", oldParentId);
        changes.put("toParentId", newParentFolderId);
        changes.put("fromParentName", lookupFolderName(oldParentId));
        changes.put("toParentName", lookupFolderName(newParentFolderId));
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.MOVE_FOLDER)
            .resourceType(ResourceType.FOLDER)
            .resourceId(folder.getId())
            .resourceName(folder.getName())
            .parentFolderId(newParentFolderId)
            .parentName(lookupFolderName(newParentFolderId))
            .changes(changes)
            .build());
        return FolderResponse.from(folder);
    }

    @Transactional
    public void softDelete(Long folderId) {
        FileFolder folder = findActiveFolder(folderId);
        folder.softDelete();
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.SOFT_DELETE_FOLDER)
            .resourceType(ResourceType.FOLDER)
            .resourceId(folder.getId())
            .resourceName(folder.getName())
            .parentFolderId(folder.getParentFolderId())
            .parentName(lookupFolderName(folder.getParentFolderId()))
            .build());
    }

    @Transactional
    public void restore(Long folderId) {
        FileFolder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new BusinessException("폴더를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        folder.restore();
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.RESTORE_FOLDER)
            .resourceType(ResourceType.FOLDER)
            .resourceId(folder.getId())
            .resourceName(folder.getName())
            .parentFolderId(folder.getParentFolderId())
            .parentName(lookupFolderName(folder.getParentFolderId()))
            .build());
    }

    /**
     * 사원의 PERSONAL 루트 파일함을 멱등하게 보장.
     * CDC 누락·신규 사원 등으로 루트가 없을 때 FE가 호출하는 자가치유 경로.
     */
    @Transactional
    public FolderResponse ensurePersonalRoot(UUID companyId, Long empId, String displayName) {
        return folderRepository.findByOwnerEmpIdAndTypeAndDeletedAtIsNull(empId, FolderType.PERSONAL)
            .filter(f -> f.getParentFolderId() == null)
            .map(FolderResponse::from)
            .orElseGet(() -> {
                String safeName = (displayName == null || displayName.isBlank()) ? "개인" : displayName;
                FileFolder created = createSystemDefaultFolder(
                    companyId, safeName + "의 파일함", FolderType.PERSONAL, null, empId, empId);
                log.info("PERSONAL 루트 자가치유 생성 empId={}, folderId={}", empId, created.getId());
                return FolderResponse.from(created);
            });
    }

    @Transactional
    public FileFolder createSystemDefaultFolder(UUID companyId, String name, FolderType type,
                                                 Long deptId, Long ownerEmpId, Long createdBy) {
        FileFolder saved = folderRepository.save(
            FileFolder.builder()
                .companyId(companyId)
                .name(name)
                .type(type)
                .deptId(deptId)
                .ownerEmpId(ownerEmpId)
                .createdBy(createdBy)
                .isSystemDefault(true)
                .build()
        );
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.CREATE_FOLDER)
            .resourceType(ResourceType.FOLDER)
            .resourceId(saved.getId())
            .resourceName(saved.getName())
            .parentFolderId(null)
            .parentName("(루트)")
            .metadata(Map.of("isSystemDefault", true, "folderType", type.name()))
            .build());
        return saved;
    }

    private FileFolder findActiveFolder(Long folderId) {
        FileFolder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new BusinessException("폴더를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (folder.isDeleted()) {
            throw new BusinessException("삭제된 폴더입니다.", HttpStatus.GONE);
        }
        return folder;
    }

    /**
     * 감사 로그 위치 표시용 — 부모 폴더 이름 스냅샷.
     * null 이면 "(루트)" 로 기록한다.
     */
    private String lookupFolderName(Long folderId) {
        if (folderId == null) return "(루트)";
        return folderRepository.findById(folderId)
            .map(FileFolder::getName)
            .orElse("(unknown)");
    }
}
