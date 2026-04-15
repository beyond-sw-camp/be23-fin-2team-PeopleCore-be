package com.peoplecore.auth.controller;

import com.peoplecore.auth.dto.HrAdminPinDtos;
import com.peoplecore.auth.service.HrAdminPinService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 인사통합 PIN 관리 엔드포인트.
 * 자체적으로 HR_SUPER_ADMIN 검증을 수행하므로
 * {@code @RoleRequired} 를 달지 않아 PIN 스코프 게이트 순환을 회피한다.
 */
@RestController
@RequestMapping("/auth/hr-admin-pin")
@RequiredArgsConstructor
public class HrAdminPinController {

    private final HrAdminPinService hrAdminPinService;

    @GetMapping("/status")
    public ResponseEntity<HrAdminPinDtos.StatusResponse> status(
            @RequestHeader("X-User-Id") Long empId
    ) {
        return ResponseEntity.ok(hrAdminPinService.getStatus(empId));
    }

    @PostMapping
    public ResponseEntity<Void> set(
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody HrAdminPinDtos.SetRequest req
    ) {
        hrAdminPinService.setPin(empId, req);
        return ResponseEntity.ok().build();
    }

    @PutMapping
    public ResponseEntity<Void> change(
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody HrAdminPinDtos.ChangeRequest req
    ) {
        hrAdminPinService.changePin(empId, req);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody HrAdminPinDtos.DeleteRequest req
    ) {
        hrAdminPinService.deletePin(empId, req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<HrAdminPinDtos.VerifyResponse> verify(
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody HrAdminPinDtos.VerifyRequest req
    ) {
        return ResponseEntity.ok(hrAdminPinService.verifyPin(empId, req));
    }
}
