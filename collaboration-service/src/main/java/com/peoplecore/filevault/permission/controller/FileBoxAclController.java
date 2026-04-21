package com.peoplecore.filevault.permission.controller;

import com.peoplecore.filevault.permission.dto.FileBoxAclAddRequest;
import com.peoplecore.filevault.permission.dto.FileBoxAclEntryResponse;
import com.peoplecore.filevault.permission.dto.FileBoxAclResponse;
import com.peoplecore.filevault.permission.dto.FileBoxAclUpdateRequest;
import com.peoplecore.filevault.permission.dto.MyFileBoxAclResponse;
import com.peoplecore.filevault.permission.service.FileBoxAclService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 파일함 단위 ACL (Tier-2) API.
 *
 * <ul>
 *   <li>GET /folders/:folderId/acl — Owner + 멤버 전체</li>
 *   <li>GET /folders/:folderId/acl/me — 내 4-플래그</li>
 *   <li>POST /folders/:folderId/acl — 멤버 추가 (Owner only)</li>
 *   <li>PATCH /folders/:folderId/acl/:empId — 플래그 갱신 (Owner only)</li>
 *   <li>DELETE /folders/:folderId/acl/:empId — 멤버 제거 (Owner only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/filevault/folders/{folderId}/acl")
@RequiredArgsConstructor
public class FileBoxAclController {

    private final FileBoxAclService service;

    @GetMapping
    public ResponseEntity<FileBoxAclResponse> get(@PathVariable Long folderId) {
        return ResponseEntity.ok(service.get(folderId));
    }

    @GetMapping("/me")
    public ResponseEntity<MyFileBoxAclResponse> me(
        @PathVariable Long folderId,
        @RequestHeader("X-User-Id") Long empId
    ) {
        return ResponseEntity.ok(service.me(folderId, empId));
    }

    @PostMapping
    public ResponseEntity<FileBoxAclEntryResponse> add(
        @PathVariable Long folderId,
        @RequestHeader("X-User-Id") Long ownerEmpId,
        @Valid @RequestBody FileBoxAclAddRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.add(folderId, ownerEmpId, request));
    }

    @PatchMapping("/{empId}")
    public ResponseEntity<FileBoxAclEntryResponse> update(
        @PathVariable Long folderId,
        @PathVariable Long empId,
        @RequestHeader("X-User-Id") Long ownerEmpId,
        @Valid @RequestBody FileBoxAclUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(folderId, empId, ownerEmpId, request));
    }

    @DeleteMapping("/{empId}")
    public ResponseEntity<Void> remove(
        @PathVariable Long folderId,
        @PathVariable Long empId,
        @RequestHeader("X-User-Id") Long ownerEmpId
    ) {
        service.remove(folderId, empId, ownerEmpId);
        return ResponseEntity.noContent().build();
    }
}
