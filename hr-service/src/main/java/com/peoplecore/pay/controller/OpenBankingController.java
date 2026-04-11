package com.peoplecore.pay.controller;

import com.peoplecore.pay.dtos.VerifyRequest;
import com.peoplecore.pay.dtos.VerifyResponse;
import com.peoplecore.pay.service.OpenBankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class OpenBankingController {

    private final OpenBankingService openBankingService;

    @PostMapping("/verify")
    public VerifyResponse verify(@RequestBody VerifyRequest request) {
        return openBankingService.verifyAccount(request);
    }
}
