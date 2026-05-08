package com.peoplecore.auth.controller;

import com.peoplecore.auth.dto.PersonalEmailSendRequest;
import com.peoplecore.auth.dto.PersonalEmailVerifyRequest;
import com.peoplecore.auth.service.PersonalEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인된 사원이 본인 외부 이메일을 변경하는 엔드포인트.
 * 새 메일로 인증코드 발송 후, 코드 검증에 성공하면 Employee.empPersonalEmail 을 갱신한다.
 */
@RestController
@RequestMapping("/auth/me/personal-email")
@RequiredArgsConstructor
public class PersonalEmailController {

    private final PersonalEmailService personalEmailService;

    @PostMapping("/send")
    public ResponseEntity<Void> sendCode(
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody PersonalEmailSendRequest request
    ) {
        personalEmailService.sendChangeCode(empId, request.getNewEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verifyAndUpdate(
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody PersonalEmailVerifyRequest request
    ) {
        personalEmailService.verifyAndUpdate(empId, request.getNewEmail(), request.getCode());
        return ResponseEntity.ok().build();
    }
}
