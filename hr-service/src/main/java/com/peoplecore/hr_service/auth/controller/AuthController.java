package com.peoplecore.hr_service.auth.controller;

import com.peoplecore.hr_service.auth.dto.LoginRequest;
import com.peoplecore.hr_service.auth.dto.LoginResponse;
import com.peoplecore.hr_service.auth.dto.TokenRefreshRequest;
import com.peoplecore.hr_service.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
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
}