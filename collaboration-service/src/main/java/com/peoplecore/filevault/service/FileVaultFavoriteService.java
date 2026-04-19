package com.peoplecore.filevault.service;

import com.peoplecore.filevault.dto.FavoriteListResponse;
import com.peoplecore.filevault.dto.FavoriteToggleResponse;
import com.peoplecore.filevault.dto.FileResponse;
import com.peoplecore.filevault.dto.FolderResponse;
import com.peoplecore.filevault.entity.FavoriteTargetType;
import com.peoplecore.filevault.entity.FileVaultFavorite;
import com.peoplecore.filevault.repository.FileFolderRepository;
import com.peoplecore.filevault.repository.FileItemRepository;
import com.peoplecore.filevault.repository.FileVaultFavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileVaultFavoriteService {

    private final FileVaultFavoriteRepository favoriteRepository;
    private final FileFolderRepository folderRepository;
    private final FileItemRepository fileRepository;

    @Transactional
    public FavoriteToggleResponse toggle(Long empId, FavoriteTargetType targetType, Long targetId) {
        return favoriteRepository
            .findByEmpIdAndTargetTypeAndTargetId(empId, targetType, targetId)
            .map(existing -> {
                favoriteRepository.delete(existing);
                return FavoriteToggleResponse.builder()
                    .targetType(targetType).targetId(targetId).starred(false).build();
            })
            .orElseGet(() -> {
                favoriteRepository.save(FileVaultFavorite.builder()
                    .empId(empId).targetType(targetType).targetId(targetId).build());
                return FavoriteToggleResponse.builder()
                    .targetType(targetType).targetId(targetId).starred(true).build();
            });
    }

    public FavoriteListResponse list(Long empId) {
        List<FileVaultFavorite> folderFavs = favoriteRepository
            .findByEmpIdAndTargetType(empId, FavoriteTargetType.FOLDER);
        List<FileVaultFavorite> fileFavs = favoriteRepository
            .findByEmpIdAndTargetType(empId, FavoriteTargetType.FILE);

        List<Long> folderIds = folderFavs.stream().map(FileVaultFavorite::getTargetId).toList();
        List<Long> fileIds = fileFavs.stream().map(FileVaultFavorite::getTargetId).toList();

        List<FolderResponse> folders = folderIds.isEmpty()
            ? List.of()
            : folderRepository.findAllById(folderIds).stream()
                .filter(f -> f.getDeletedAt() == null)
                .map(f -> FolderResponse.from(f, true))
                .toList();

        List<FileResponse> files = fileIds.isEmpty()
            ? List.of()
            : fileRepository.findAllById(fileIds).stream()
                .filter(f -> f.getDeletedAt() == null)
                .map(f -> FileResponse.from(f, true))
                .toList();

        return FavoriteListResponse.builder().folders(folders).files(files).build();
    }

    /**
     * 폴더 응답 리스트에 starred 플래그를 in-place 로 채워 반환.
     */
    public List<FolderResponse> markStarredFolders(Long empId, List<FolderResponse> folders) {
        if (empId == null || folders == null || folders.isEmpty()) return folders;
        Set<Long> starredIds = findFavoriteIds(empId, FavoriteTargetType.FOLDER,
            folders.stream().map(FolderResponse::getFolderId).toList());
        if (!starredIds.isEmpty()) {
            folders.forEach(f -> f.setStarred(starredIds.contains(f.getFolderId())));
        }
        return folders;
    }

    /**
     * 파일 응답 리스트에 starred 플래그를 in-place 로 채워 반환.
     */
    public List<FileResponse> markStarredFiles(Long empId, List<FileResponse> files) {
        if (empId == null || files == null || files.isEmpty()) return files;
        Set<Long> starredIds = findFavoriteIds(empId, FavoriteTargetType.FILE,
            files.stream().map(FileResponse::getFileId).toList());
        if (!starredIds.isEmpty()) {
            files.forEach(f -> f.setStarred(starredIds.contains(f.getFileId())));
        }
        return files;
    }

    private Set<Long> findFavoriteIds(Long empId, FavoriteTargetType type, List<Long> ids) {
        if (ids.isEmpty()) return Set.of();
        return new HashSet<>(
            favoriteRepository
                .findByEmpIdAndTargetTypeAndTargetIdIn(empId, type, ids)
                .stream().map(FileVaultFavorite::getTargetId).toList()
        );
    }
}
