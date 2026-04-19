package com.peoplecore.auth.controller;

import com.peoplecore.auth.dto.*;
import com.peoplecore.auth.service.FaceAuthService;
import com.peoplecore.auth.service.FaceRecognitionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/auth/face")
@RequiredArgsConstructor
public class FaceAuthController {

    private final FaceRecognitionClient faceRecognitionClient;
    private final FaceAuthService faceAuthService;

    @GetMapping("/health")
    public ResponseEntity<FaceHealthResponse> healthCheck() {
        return ResponseEntity.ok(faceRecognitionClient.healthCheck());
    }

    @PostMapping("/extract")
    public ResponseEntity<FaceExtractResponse> extractEmbedding(@RequestBody FaceExtractRequest request) {
        return ResponseEntity.ok(faceRecognitionClient.extractEmbedding(request));
    }

    @PostMapping("/validate")
    public ResponseEntity<FaceValidateResponse> validateFace(@RequestBody FaceExtractRequest request) {
        return ResponseEntity.ok(faceAuthService.validateFace(request.getImage()));
    }

    @PostMapping("/register")
    public ResponseEntity<FaceRegisterResponse> registerFace(@RequestBody FaceRegisterRequest request) {
        return ResponseEntity.ok(faceAuthService.registerFace(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> faceLogin(@RequestBody FaceLoginRequest request) {
        return ResponseEntity.ok(faceAuthService.faceLogin(request));
    }

    @DeleteMapping("/unregister/{empId}")
    public ResponseEntity<Void> unregisterFace(@PathVariable Long empId) {
        faceAuthService.unregisterFace(empId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/employees/unregistered")
    public ResponseEntity<List<FaceEmployeeResponse>> getUnregisteredEmployees(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(faceAuthService.getUnregisteredEmployees(companyId));
    }

    @GetMapping("/employees/registered")
    public ResponseEntity<List<FaceEmployeeResponse>> getRegisteredEmployees(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(faceAuthService.getRegisteredEmployees(companyId));
    }
}
