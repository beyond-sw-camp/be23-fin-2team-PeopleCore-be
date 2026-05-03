package com.peoplecore.auth.controller;

import com.peoplecore.auth.dto.HrAdminPinDtos;
import com.peoplecore.auth.jwt.JwtProvider;
import com.peoplecore.auth.service.HrAdminPinService;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import io.jsonwebtoken.Claims;
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
    private final JwtProvider jwtProvider;

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

    /**
     * 인사통합 PIN 세션 연장 — 기존 잔여 시간 + 15분의 새 토큰 발급.
     * 게이트웨이가 X-HR-Admin-Token 을 검증한 결과(X-HR-Admin-Scope = "true") 인 경우에만 발급.
     */
    @PostMapping("/extend")
    public ResponseEntity<HrAdminPinDtos.VerifyResponse> extend(
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader(value = "X-HR-Admin-Scope", required = false) String hrAdminScope,
            @RequestHeader(value = "X-HR-Admin-Token", required = false) String hrAdminToken
    ) {
        if (!"true".equals(hrAdminScope) || hrAdminToken == null || hrAdminToken.isBlank()) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        long remainingSeconds;
        try {
            Claims claims = jwtProvider.parseAccessToken(hrAdminToken);
            long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            remainingSeconds = Math.max(0L, remainingMs / 1000L);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return ResponseEntity.ok(hrAdminPinService.extendSession(empId, remainingSeconds));
    }
}
