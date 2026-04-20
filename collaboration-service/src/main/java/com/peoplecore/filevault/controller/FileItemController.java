package com.peoplecore.filevault.controller;

import com.peoplecore.filevault.dto.*;
import com.peoplecore.filevault.entity.FileItem;
import com.peoplecore.filevault.security.FileVaultAccessPolicy;
import com.peoplecore.filevault.service.FileItemService;
import com.peoplecore.filevault.service.FileVaultFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/filevault/files")
@RequiredArgsConstructor
public class FileItemController {

    private final FileItemService fileItemService;
    private final FileVaultAccessPolicy accessPolicy;
    private final FileVaultFavoriteService favoriteService;

    @GetMapping
    public ResponseEntity<List<FileResponse>> listByFolder(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @RequestParam Long folderId
    ) {
        accessPolicy.ensureCanReadFolder(titleId, empId, folderId);
        return ResponseEntity.ok(favoriteService.markStarredFiles(empId,
            new java.util.ArrayList<>(fileItemService.listByFolder(folderId))));
    }

    @PostMapping("/upload-url")
    public ResponseEntity<UploadUrlResponse> generateUploadUrl(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @RequestBody UploadUrlRequest request
    ) {
        accessPolicy.ensureCanWriteFolder(titleId, empId, request.getFolderId());
        return ResponseEntity.ok(
            fileItemService.generateUploadUrl(companyId, request.getFolderId(), request));
    }

    @PostMapping
    public ResponseEntity<FileResponse> confirmUpload(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @RequestBody FileUploadConfirmRequest request
    ) {
        accessPolicy.ensureCanWriteFolder(titleId, empId, request.getFolderId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(fileItemService.confirmUpload(empId, request));
    }

    @GetMapping("/{fileId}/download-url")
    public ResponseEntity<Map<String, String>> generateDownloadUrl(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @PathVariable Long fileId
    ) {
        FileItem file = accessPolicy.loadFile(fileId);
        accessPolicy.ensureCanReadFolder(titleId, empId, file.getFolderId());
        String url = fileItemService.generateDownloadUrl(fileId);
        return ResponseEntity.ok(Map.of("downloadUrl", url));
    }

    @PatchMapping("/{fileId}/rename")
    public ResponseEntity<FileResponse> renameFile(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @PathVariable Long fileId,
        @RequestBody Map<String, String> body
    ) {
        FileItem file = accessPolicy.loadFile(fileId);
        accessPolicy.ensureCanManageFile(titleId, empId, file);
        return ResponseEntity.ok(fileItemService.renameFile(fileId, body.get("name")));
    }

    @PatchMapping("/{fileId}/move")
    public ResponseEntity<FileResponse> moveFile(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @PathVariable Long fileId,
        @RequestBody Map<String, Long> body
    ) {
        FileItem file = accessPolicy.loadFile(fileId);
        accessPolicy.ensureCanManageFile(titleId, empId, file);
        Long newFolderId = body.get("folderId");
        if (newFolderId != null) {
            accessPolicy.ensureCanWriteFolder(titleId, empId, newFolderId);
        }
        return ResponseEntity.ok(fileItemService.moveFile(fileId, newFolderId));
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> softDelete(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @PathVariable Long fileId
    ) {
        FileItem file = accessPolicy.loadFile(fileId);
        accessPolicy.ensureCanManageFile(titleId, empId, file);
        fileItemService.softDelete(fileId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{fileId}/restore")
    public ResponseEntity<Void> restore(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @PathVariable Long fileId
    ) {
        FileItem file = accessPolicy.loadFile(fileId);
        accessPolicy.ensureCanManageFile(titleId, empId, file);
        fileItemService.restore(fileId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{fileId}/permanent")
    public ResponseEntity<Void> permanentDelete(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId,
        @PathVariable Long fileId
    ) {
        FileItem file = accessPolicy.loadFile(fileId);
        accessPolicy.ensureCanManageFile(titleId, empId, file);
        fileItemService.permanentDelete(fileId);
        return ResponseEntity.noContent().build();
    }
}
