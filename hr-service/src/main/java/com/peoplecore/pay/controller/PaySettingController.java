package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.BankResDto;
import com.peoplecore.pay.dtos.PaySettingsReqDto;
import com.peoplecore.pay.dtos.PaySettingsResDto;
import com.peoplecore.pay.service.PaySettingsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/superadmin/settings")
@RoleRequired({"HR_SUPER_ADMIN"})
public class PaySettingController {

    private final PaySettingsService paySettingsService;

    @Autowired
    public PaySettingController(PaySettingsService paySettingsService) {
        this.paySettingsService = paySettingsService;
    }


    //    은행목록조회
    @GetMapping("/banks")
    public ResponseEntity<List<BankResDto>> getBankList(){
        return ResponseEntity.ok(paySettingsService.getBankList());
    }

//    급여지급설정 조회
    @GetMapping("/payment")
    public ResponseEntity<PaySettingsResDto> getPaySettings(@RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(paySettingsService.getPaySettings(companyId));
    }

//    급여지급설정 수정
    @PutMapping("/payment")
    public ResponseEntity<PaySettingsResDto> updatePaySettings(@RequestHeader("X-User-Company") UUID companyId, @RequestBody @Valid PaySettingsReqDto reqDto) {
        return ResponseEntity.ok(paySettingsService.updatePaySettings(companyId, reqDto));
    }
}
