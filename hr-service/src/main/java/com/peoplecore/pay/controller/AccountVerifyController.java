package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.AccountVerifyReqDto;
import com.peoplecore.pay.dtos.AccountVerifyResDto;
import com.peoplecore.pay.service.AccountVerifyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pay/account")
@RoleRequired({"HR_SUPER_ADMIN","HR_ADMIN"})
public class AccountVerifyController {

    private final AccountVerifyService accountVerifyService;

    @Autowired
    public AccountVerifyController(AccountVerifyService accountVerifyService) {
        this.accountVerifyService = accountVerifyService;
    }

    //    계좌 실명 검증
    @PostMapping("/verify")
    public ResponseEntity<AccountVerifyResDto> verify(@RequestBody @Valid AccountVerifyReqDto req) {
        return ResponseEntity.ok(accountVerifyService.verifyAndIssueToken(req));
    }


}
