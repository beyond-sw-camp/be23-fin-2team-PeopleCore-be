package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.InsuranceJobTypesReqDto;
import com.peoplecore.pay.dtos.InsuranceJobTypesResDto;
import com.peoplecore.pay.dtos.InsuranceRatesEmployerReqDto;
import com.peoplecore.pay.dtos.InsuranceRatesResDto;
import com.peoplecore.pay.service.InsuranceJobTypesService;
import com.peoplecore.pay.service.InsuranceRatesService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/superadmin/insurance")
@RoleRequired({"HR_SUPER_ADMIN"})
public class InsuranceRatesController {

    private final InsuranceRatesService insuranceRatesService;
    private final InsuranceJobTypesService insuranceJobTypesService;

    @Autowired
    public InsuranceRatesController(InsuranceRatesService insuranceRatesService, InsuranceJobTypesService insuranceJobTypesService) {
        this.insuranceRatesService = insuranceRatesService;
        this.insuranceJobTypesService = insuranceJobTypesService;
    }

    // ═══════════════════════════════════════════
    //  공통 보험요율 (국민연금/건강/장기요양/고용)
    // ═══════════════════════════════════════════

    //    현재연도 보험요율 조회
    @GetMapping("/rates")
    public ResponseEntity<InsuranceRatesResDto> getCurrentRates(@RequestHeader("X-User-Company") UUID companyId){
        return ResponseEntity.ok(insuranceRatesService.getCurrentRates(companyId));
    }

//    특정연도 보험요율 조회
    @GetMapping("/rates/{year}")
    public ResponseEntity<InsuranceRatesResDto> getRatesByYear(@RequestHeader("X-User-Company") UUID companyId,
                                                               @PathVariable Integer year){
        return ResponseEntity.ok(insuranceRatesService.getRatesByYear(companyId,year));
    }

//    고용보험 사업주 요율 수정
    @PutMapping("/rates/employer")
    public ResponseEntity<InsuranceRatesResDto> updateEmployerRate(@RequestHeader("X-User-Company") UUID companyId,
                                                                   @RequestBody @Valid InsuranceRatesEmployerReqDto reqDto) {
        return ResponseEntity.ok(insuranceRatesService.updateEmployerRate(companyId, reqDto));
    }

    // ═══════════════════════════════════════════
    //  산재보험 업종 관리
    // ═══════════════════════════════════════════

//   산재보험 업종 목록 조회
    @GetMapping("/jobtypes")
    public ResponseEntity<List<InsuranceJobTypesResDto>> getJobTypes(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(insuranceJobTypesService.getJobTypes(companyId));
    }

//    산재보험 업종 추가
    @PostMapping("/jobtypes")
    public ResponseEntity<InsuranceJobTypesResDto> createJobType(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody @Valid InsuranceJobTypesReqDto reqDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(insuranceJobTypesService.createJobType(companyId, reqDto));
    }




}
