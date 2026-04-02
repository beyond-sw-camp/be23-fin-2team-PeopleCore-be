package com.peoplecore.auth.controller;

import com.peoplecore.auth.dto.PasswordResetRequest;
import com.peoplecore.auth.dto.SmsCodeRequest;
import com.peoplecore.auth.dto.SmsVerifyRequest;
import com.peoplecore.auth.service.PasswordResetService;
import com.peoplecore.auth.service.SmsAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/password")
@RequiredArgsConstructor
public class PasswordResetController {

    private final SmsAuthService smsAuthService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/sms/send")
    public ResponseEntity<Void> sendSmsCode(@RequestBody SmsCodeRequest request) {
        smsAuthService.sendCode(request.getCompanyId(), request.getEmpName(), request.getEmpPhone());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/sms/verify")
    public ResponseEntity<Void> verifySmsCode(@RequestBody SmsVerifyRequest request) {
        smsAuthService.verify(request.getCompanyId(), request.getEmpName(), request.getEmpPhone(), request.getCode());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> resetPassword(@RequestBody PasswordResetRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok().build();
    }
}