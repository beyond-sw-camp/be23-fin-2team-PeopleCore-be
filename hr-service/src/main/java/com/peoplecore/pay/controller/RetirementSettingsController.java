package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.RetirementSettingsReqDto;
import com.peoplecore.pay.dtos.RetirementSettingsResDto;
import com.peoplecore.pay.service.RetirementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/pay/superadmin/retirement")
@RoleRequired({"HR_SUPER_ADMIN"})
public class RetirementSettingsController {

    private final RetirementService retirementService;
    @Autowired
    public RetirementSettingsController(RetirementService retirementService) {
        this.retirementService = retirementService;
    }

//    퇴직연금 설정 조회
    @GetMapping
    public ResponseEntity<RetirementSettingsResDto> getRetirementSettings(
            @RequestHeader("X-User-Company") UUID companyId){
        return ResponseEntity.ok(retirementService.getRetirementSettings(companyId));
    }

//    퇴직연금설정 저장/수정
    @PutMapping
    public ResponseEntity<RetirementSettingsResDto> saveRetirementSettings(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody @Valid RetirementSettingsReqDto reqDto){
        return ResponseEntity.ok(retirementService.saveRetirementSettings(companyId, reqDto));
    }


}
