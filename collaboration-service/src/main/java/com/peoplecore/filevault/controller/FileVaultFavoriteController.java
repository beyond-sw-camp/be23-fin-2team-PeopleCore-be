package com.peoplecore.filevault.controller;

import com.peoplecore.filevault.dto.FavoriteListResponse;
import com.peoplecore.filevault.dto.FavoriteToggleRequest;
import com.peoplecore.filevault.dto.FavoriteToggleResponse;
import com.peoplecore.filevault.entity.FavoriteTargetType;
import com.peoplecore.filevault.security.FileVaultAccessPolicy;
import com.peoplecore.filevault.service.FileVaultFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/filevault/favorites")
@RequiredArgsConstructor
public class FileVaultFavoriteController {

    private final FileVaultFavoriteService favoriteService;
    private final FileVaultAccessPolicy accessPolicy;

    @GetMapping
    public ResponseEntity<FavoriteListResponse> list(
        @RequestHeader("X-User-Id") Long empId
    ) {
        return ResponseEntity.ok(favoriteService.list(empId));
    }

    @PostMapping("/toggle")
    public ResponseEntity<FavoriteToggleResponse> toggle(
        @RequestHeader("X-User-Id") Long empId,
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestBody FavoriteToggleRequest request
    ) {
        // 즐겨찾기 토글은 읽기 권한이 있는 대상에 대해서만 허용
        if (request.getTargetType() == FavoriteTargetType.FOLDER) {
            accessPolicy.ensureCanReadFolder(titleId, empId, request.getTargetId());
        } else {
            var file = accessPolicy.loadFile(request.getTargetId());
            accessPolicy.ensureCanReadFolder(titleId, empId, file.getFolderId());
        }
        return ResponseEntity.ok(
            favoriteService.toggle(empId, request.getTargetType(), request.getTargetId()));
    }
}
