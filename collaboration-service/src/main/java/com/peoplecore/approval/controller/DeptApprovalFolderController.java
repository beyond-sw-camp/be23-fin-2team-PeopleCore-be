package com.peoplecore.approval.controller;

import com.peoplecore.approval.dto.DeptFolderCreateRequest;
import com.peoplecore.approval.dto.DeptFolderReorderRequest;
import com.peoplecore.approval.dto.DeptFolderResponse;
import com.peoplecore.approval.dto.DeptFolderUpdateRequest;
import com.peoplecore.approval.service.DeptApprovalFolderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/approval/dept-folders")
public class DeptApprovalFolderController {

    private final DeptApprovalFolderService folderService;

    @Autowired
    public DeptApprovalFolderController(DeptApprovalFolderService folderService) {
        this.folderService = folderService;
    }

    // ==================== 문서함 CRUD ====================

    /** 1. 문서함 목록 조회 */
    // TODO: @RoleRequired({"HR_SUPER_ADMIN"})
    @GetMapping
    public ResponseEntity<List<DeptFolderResponse>> getList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Department") Long deptId) {
        return ResponseEntity.ok(folderService.getList(companyId, deptId));
    }

    /** 2. 문서함 생성 */
    // TODO: @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping
    public ResponseEntity<DeptFolderResponse> create(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Department") Long deptId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody DeptFolderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(folderService.create(companyId, deptId, empId, request));
    }

    /** 3. 문서함 이름 수정 */
    // TODO: @RoleRequired({"HR_SUPER_ADMIN"})
    @PutMapping("/{id}")
    public ResponseEntity<DeptFolderResponse> update(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long id,
            @RequestBody DeptFolderUpdateRequest request) {
        return ResponseEntity.ok(folderService.update(companyId, id, request));
    }

    /** 4. 문서함 삭제 */
    // TODO: @RoleRequired({"HR_SUPER_ADMIN"})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long id) {
        folderService.delete(companyId, id);
        return ResponseEntity.noContent().build();
    }

    /** 5. 순서 일괄 변경 */
    // TODO: @RoleRequired({"HR_SUPER_ADMIN"})
    @PutMapping("/reorder")
    public ResponseEntity<List<DeptFolderResponse>> reorder(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Department") Long deptId,
            @RequestBody DeptFolderReorderRequest request) {
        return ResponseEntity.ok(folderService.reorder(companyId, deptId, request));
    }

    // ==================== 담당자 관리 ====================

    /** 6. 담당자 추가 */
    // TODO: @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping("/{id}/managers")
    public ResponseEntity<DeptFolderResponse.ManagerInfo> addManager(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long id,
            @RequestBody ManagerAddRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(folderService.addManager(companyId, id,
                        request.empId, request.empName, request.deptName));
    }

    /** 7. 담당자 삭제 */
    // TODO: @RoleRequired({"HR_SUPER_ADMIN"})
    @DeleteMapping("/{id}/managers/{empId}")
    public ResponseEntity<Void> removeManager(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long id,
            @PathVariable Long empId) {
        folderService.removeManager(companyId, id, empId);
        return ResponseEntity.noContent().build();
    }

    /** 담당자 추가 요청 Body */
    public record ManagerAddRequest(Long empId, String empName, String deptName) {}
}
