package com.peoplecore.auth.controller;

import com.peoplecore.auth.dto.EmailFindResponse;
import com.peoplecore.auth.dto.SmsCodeRequest;
import com.peoplecore.auth.dto.SmsVerifyRequest;
import com.peoplecore.auth.service.EmailFindService;
import com.peoplecore.auth.service.SmsAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/email")
@RequiredArgsConstructor
public class EmailFindController {

    private final SmsAuthService smsAuthService;
    private final EmailFindService emailFindService;

    @PostMapping("/sms/send")
    public ResponseEntity<Void> sendSmsCode(@RequestBody SmsCodeRequest request) {
        smsAuthService.sendCode(request.getCompanyId(), request.getEmpName(), request.getEmpPhone());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/sms/verify")
    public ResponseEntity<EmailFindResponse> findEmail(@RequestBody SmsVerifyRequest request) {
        EmailFindResponse response = emailFindService.findEmail(request);
        return ResponseEntity.ok(response);
    }
}
