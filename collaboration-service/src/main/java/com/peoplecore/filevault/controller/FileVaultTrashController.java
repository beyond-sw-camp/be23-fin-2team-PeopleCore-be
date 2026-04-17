package com.peoplecore.filevault.controller;

import com.peoplecore.filevault.dto.TrashResponse;
import com.peoplecore.filevault.service.FileVaultTrashService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/filevault/trash")
@RequiredArgsConstructor
public class FileVaultTrashController {

    private final FileVaultTrashService trashService;

    @GetMapping
    public ResponseEntity<TrashResponse> listTrash(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId
    ) {
        return ResponseEntity.ok(trashService.listTrash(companyId, titleId, empId));
    }

    @DeleteMapping
    public ResponseEntity<Void> emptyTrash(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader(value = "X-User-Title", required = false) Long titleId,
        @RequestHeader("X-User-Id") Long empId
    ) {
        trashService.emptyTrash(companyId, titleId, empId);
        return ResponseEntity.noContent().build();
    }
}
