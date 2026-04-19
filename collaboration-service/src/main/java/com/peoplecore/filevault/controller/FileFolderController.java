package com.peoplecore.filevault.controller;

import com.peoplecore.filevault.dto.FolderCreateRequest;
import com.peoplecore.filevault.dto.FolderResponse;
import com.peoplecore.filevault.entity.FileFolder;
import com.peoplecore.filevault.entity.FolderType;
import com.peoplecore.filevault.security.FileVaultAccessPolicy;
import com.peoplecore.filevault.service.FileFolderService;
import com.peoplecore.filevault.service.FileVaultFavoriteService;
import com.peoplecore.filevault.service.FileVaultTrashService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/filevault/folders")
@RequiredArgsConstructor
public class FileFolderController {

    private final FileFolderService folderService;
    private final FileVaultAccessPolicy accessPolicy;
    private final FileVaultTrashService trashService;
    private final FileVaultFavoriteService favoriteService;

    @GetMapping
    public ResponseEntity<List<FolderResponse>> listRootFolders(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader("X-User-Id") Long empId,
        @RequestParam FolderType type
    ) {
        return ResponseEntity.ok(favoriteService.markStarredFolders(empId,
            new java.util.ArrayList<>(folderService.listRootFolders(companyId, type, empId))));
    }

    @PostMapping("/ensure-personal")
    public ResponseEntity<FolderResponse> ensurePersonalRoot(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader("X-User-Id") Long empId,
        @RequestHeader(value = "X-User-Name", required = false) String empName
    ) {
        return ResponseEntity.ok(folderService.ensurePersonalRoot(companyId, empId, empName));
    }

    @GetMapping("/{folderId}")
    public ResponseEntity<FolderResponse> getFolder(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @PathVariable Long folderId
    ) {
        accessPolicy.ensureCanReadFolder(titleId, empId, folderId);
        return ResponseEntity.ok(folderService.getFolder(folderId));
    }

    @GetMapping("/{folderId}/children")
    public ResponseEntity<List<FolderResponse>> listChildren(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @PathVariable Long folderId
    ) {
        accessPolicy.ensureCanReadFolder(titleId, empId, folderId);
        return ResponseEntity.ok(favoriteService.markStarredFolders(empId,
            new java.util.ArrayList<>(folderService.listChildren(folderId))));
    }

    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader("X-User-Id") Long empId,
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestBody FolderCreateRequest request
    ) {
        accessPolicy.ensureCanCreateFolder(titleId, empId, request.getType(), request.getParentFolderId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(folderService.createFolder(companyId, empId, request));
    }

    @PatchMapping("/{folderId}/rename")
    public ResponseEntity<FolderResponse> renameFolder(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @PathVariable Long folderId,
        @RequestBody Map<String, String> body
    ) {
        FileFolder folder = accessPolicy.loadFolder(folderId);
        accessPolicy.ensureCanManageFolder(titleId, empId, folder);
        return ResponseEntity.ok(folderService.renameFolder(folderId, body.get("name")));
    }

    @PatchMapping("/{folderId}/move")
    public ResponseEntity<FolderResponse> moveFolder(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @PathVariable Long folderId,
        @RequestBody Map<String, Long> body
    ) {
        FileFolder folder = accessPolicy.loadFolder(folderId);
        accessPolicy.ensureCanManageFolder(titleId, empId, folder);
        Long newParentFolderId = body.get("parentFolderId");
        if (newParentFolderId != null) {
            FileFolder newParent = accessPolicy.loadFolder(newParentFolderId);
            accessPolicy.ensureCanManageFolder(titleId, empId, newParent);
        }
        return ResponseEntity.ok(folderService.moveFolder(folderId, newParentFolderId));
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> softDelete(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @PathVariable Long folderId
    ) {
        FileFolder folder = accessPolicy.loadFolder(folderId);
        accessPolicy.ensureCanManageFolder(titleId, empId, folder);
        folderService.softDelete(folderId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{folderId}/restore")
    public ResponseEntity<Void> restore(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @PathVariable Long folderId
    ) {
        FileFolder folder = accessPolicy.loadFolder(folderId);
        accessPolicy.ensureCanManageFolder(titleId, empId, folder);
        folderService.restore(folderId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{folderId}/permanent")
    public ResponseEntity<Void> permanentDelete(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @PathVariable Long folderId
    ) {
        trashService.permanentDeleteFolder(titleId, empId, folderId);
        return ResponseEntity.noContent().build();
    }
}
