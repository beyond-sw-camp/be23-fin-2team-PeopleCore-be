package com.peoplecore.filevault.service;

import com.peoplecore.exception.BusinessException;
import com.peoplecore.filevault.dto.FileResponse;
import com.peoplecore.filevault.dto.FolderResponse;
import com.peoplecore.filevault.dto.TrashResponse;
import com.peoplecore.filevault.entity.FileFolder;
import com.peoplecore.filevault.entity.FileItem;
import com.peoplecore.filevault.repository.FileFolderRepository;
import com.peoplecore.filevault.repository.FileItemRepository;
import com.peoplecore.filevault.security.FileVaultAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 파일함 휴지통 서비스.
 *
 * <p>범위: 사용자의 write-scope 기준. 즉, 사용자가 영구삭제/복원할 수 있는 항목만 노출된다.
 * 시스템 기본 파일함은 소프트 삭제 자체가 차단되므로 휴지통에 등장하지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FileVaultTrashService {

    private final FileFolderRepository folderRepository;
    private final FileItemRepository fileItemRepository;
    private final FileVaultMinioService minioService;
    private final FileVaultAccessPolicy accessPolicy;

    public TrashResponse listTrash(UUID companyId, Long titleId, Long empId) {
        List<FileFolder> allCompanyFolders = folderRepository.findByCompanyId(companyId);
        Map<Long, FileFolder> byId = allCompanyFolders.stream()
            .collect(Collectors.toMap(FileFolder::getId, f -> f));

        List<FolderResponse> deletedFolders = allCompanyFolders.stream()
            .filter(FileFolder::isDeleted)
            .filter(f -> {
                FileFolder root = resolveRootInMemory(byId, f);
                return accessPolicy.canManageRoot(titleId, empId, root);
            })
            .map(FolderResponse::from)
            .toList();

        List<Long> companyFolderIds = allCompanyFolders.stream().map(FileFolder::getId).toList();
        List<FileResponse> deletedFiles = companyFolderIds.isEmpty()
            ? List.of()
            : fileItemRepository.findByFolderIdInAndDeletedAtIsNotNull(companyFolderIds).stream()
                .filter(file -> {
                    FileFolder folder = byId.get(file.getFolderId());
                    if (folder == null) return false;
                    FileFolder root = resolveRootInMemory(byId, folder);
                    return accessPolicy.canManageRoot(titleId, empId, root);
                })
                .map(FileResponse::from)
                .toList();

        return TrashResponse.builder()
            .folders(deletedFolders)
            .files(deletedFiles)
            .build();
    }

    @Transactional
    public void permanentDeleteFolder(Long titleId, Long empId, Long folderId) {
        FileFolder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new BusinessException("폴더를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (Boolean.TRUE.equals(folder.getIsSystemDefault())) {
            throw new BusinessException("시스템 기본 파일함은 영구 삭제할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        accessPolicy.ensureCanManageFolder(titleId, empId, folder);

        Set<Long> folderIds = collectDescendantFolderIds(folderId);
        List<FileItem> files = folderIds.isEmpty()
            ? List.of()
            : fileItemRepository.findByFolderIdIn(new ArrayList<>(folderIds));

        for (FileItem file : files) {
            try {
                minioService.deleteObject(file.getStorageKey());
            } catch (Exception e) {
                log.warn("MinIO 객체 삭제 실패 storageKey={}, error={}", file.getStorageKey(), e.getMessage());
            }
        }
        if (!files.isEmpty()) fileItemRepository.deleteAll(files);
        if (!folderIds.isEmpty()) folderRepository.deleteAllById(folderIds);
    }

    @Transactional
    public void permanentDeleteFile(Long titleId, Long empId, Long fileId) {
        FileItem file = fileItemRepository.findById(fileId)
            .orElseThrow(() -> new BusinessException("파일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        accessPolicy.ensureCanManageFile(titleId, empId, file);
        try {
            minioService.deleteObject(file.getStorageKey());
        } catch (Exception e) {
            log.warn("MinIO 객체 삭제 실패 storageKey={}, error={}", file.getStorageKey(), e.getMessage());
        }
        fileItemRepository.delete(file);
    }

    @Transactional
    public void emptyTrash(UUID companyId, Long titleId, Long empId) {
        TrashResponse trash = listTrash(companyId, titleId, empId);
        for (FolderResponse f : trash.getFolders()) {
            try {
                permanentDeleteFolder(titleId, empId, f.getFolderId());
            } catch (BusinessException e) {
                log.warn("휴지통 비우기 중 폴더 삭제 실패 folderId={}, error={}", f.getFolderId(), e.getMessage());
            }
        }
        for (FileResponse f : trash.getFiles()) {
            try {
                permanentDeleteFile(titleId, empId, f.getFileId());
            } catch (BusinessException e) {
                log.warn("휴지통 비우기 중 파일 삭제 실패 fileId={}, error={}", f.getFileId(), e.getMessage());
            }
        }
    }

    private FileFolder resolveRootInMemory(Map<Long, FileFolder> byId, FileFolder folder) {
        FileFolder current = folder;
        int depth = 0;
        while (current.getParentFolderId() != null) {
            if (++depth > 64) break;
            FileFolder parent = byId.get(current.getParentFolderId());
            if (parent == null) break;
            current = parent;
        }
        return current;
    }

    private Set<Long> collectDescendantFolderIds(Long rootFolderId) {
        Set<Long> result = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootFolderId);
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            if (!result.add(id)) continue;
            List<FileFolder> children = folderRepository.findByParentFolderIdIn(List.of(id));
            for (FileFolder c : children) queue.add(c.getId());
        }
        return result;
    }
}
