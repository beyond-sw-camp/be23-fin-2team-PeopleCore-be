package com.peoplecore.attendance.controller;

import com.netflix.discovery.converters.Auto;
import com.peoplecore.attendance.dto.*;
import com.peoplecore.attendance.service.WorkGroupService;
import com.peoplecore.auth.RoleRequired;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequestMapping("/workgroup")
@RestController
@RoleRequired("HR_SUPER_ADMIN")
public class WorkGroupController {
    private final WorkGroupService workGroupService;

    @Autowired
    public WorkGroupController(WorkGroupService workGroupService) {
        this.workGroupService = workGroupService;
    }

    /*근무 그룹 내 목록 조회 */
    @GetMapping
    public ResponseEntity<List<WorkGroupResDto>> getWorkGroups(@RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(workGroupService.getWorkGroups(companyId));
    }

    /*근무 그룹 상세 조회 */
    @GetMapping("/{workGroupId}")
    public ResponseEntity<WorkGroupDetailResDto> getWorkGroup(@PathVariable Long workGroupId) {
        return ResponseEntity.ok(workGroupService.getWorkGroup(workGroupId));
    }

    /* 근무 그룹 소속 사원 목록 조회 */
    @GetMapping("/employees/{workGroupId}")
    public ResponseEntity<Page<WorkGroupMemberResDto>> getEmployees(@PathVariable Long workGroupId, Pageable pageable) {
        return ResponseEntity.ok(workGroupService.getEmployees(workGroupId, pageable));
    }

    /* 근무 그룹 생성*/
    @PostMapping
    public ResponseEntity<WorkGroupDetailResDto> createWorkGroup(@RequestHeader("X-User-Company") UUID companyId, @RequestHeader("X-User-Id") Long empId, @RequestHeader("X-User-Name") String empName, @RequestBody WorkGroupReqDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workGroupService.createWorkGroup(companyId, empId, empName, dto));
    }


    /*근무 그룹 수정 */
    @PutMapping("/{workGroupId}")
    public ResponseEntity<WorkGroupDetailResDto> updateWorkGroup(@PathVariable Long workGroupId, @RequestBody WorkGroupReqDto dto) {
        return ResponseEntity.ok(workGroupService.updateWorkGroup(workGroupId, dto));
    }

    /* 근무 그룹 삭제 */
    @DeleteMapping("/{workGroupId}")
    public ResponseEntity<Void> deleteWorkGroup(@PathVariable Long workGroupId) {
        workGroupService.deleteWorkGroup(workGroupId);
        return ResponseEntity.ok().build();
    }


    /*근무 그룹 소속 사원 이관 */
    @PutMapping("/member/transfer/{sourceWorkGroupId}")
    public ResponseEntity<WorkGroupTransferResDto> transferMember(@PathVariable Long sourceWorkGroupId, @Valid @RequestBody WorkGroupTransferReqDto dto) {

        return ResponseEntity.ok(workGroupService.transferEmp(sourceWorkGroupId, dto));
    }

    /* 사원 생성용 근무 그룹 조회 api*/
    @GetMapping("/options")
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    public ResponseEntity<List<WorkGroupOptionResDto>> getWorkGroupOptions(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(workGroupService.getWorkGroupOptions(companyId));
    }
}
