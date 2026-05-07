package com.peoplecore.auth.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.auth.dto.LoginRequest;
import com.peoplecore.auth.dto.LoginResponse;
import com.peoplecore.auth.dto.TokenRefreshRequest;
import com.peoplecore.auth.dto.VerifyPasswordRequest;
import com.peoplecore.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request,
                                               HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(authService.login(request, httpServletRequest));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("X-User-Id") Long empId) {
        authService.logout(empId);
        return ResponseEntity.ok().build();
    }

    // 현재 유저 비밀번호 재확인
    @PostMapping("/verify-password")
    public ResponseEntity<Map<String, Boolean>> verifyPassword(
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody VerifyPasswordRequest request) {
        boolean ok = authService.verifyPassword(empId, request.getPassword());
        return ResponseEntity.ok(Map.of("valid", ok));
    }
}