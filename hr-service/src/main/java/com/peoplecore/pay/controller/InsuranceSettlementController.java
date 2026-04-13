package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.InsuranceSettlementApplyReqDto;
import com.peoplecore.pay.dtos.InsuranceSettlementCalcReqDto;
import com.peoplecore.pay.dtos.InsuranceSettlementDetailResDto;
import com.peoplecore.pay.dtos.InsuranceSettlementSummaryResDto;
import com.peoplecore.pay.service.InsuranceSettlementService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/pay/insurance")
@RoleRequired({"HR_SUPER_ADMIN","HR_ADMIN"})
public class InsuranceSettlementController {

    private final InsuranceSettlementService insuranceSettlementService;

    public InsuranceSettlementController(InsuranceSettlementService insuranceSettlementService) {
        this.insuranceSettlementService = insuranceSettlementService;
    }


//    정산보험료 목록조회
    @GetMapping
    public ResponseEntity<InsuranceSettlementSummaryResDto> getSettlementList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam String fromYearMonth,
            @RequestParam String toYearMonth,
            @PageableDefault(size = 10)Pageable pageable) {

        return ResponseEntity.ok(insuranceSettlementService.getSettlementList(companyId, fromYearMonth, toYearMonth, pageable));
    }

//    보험료 산정(정산기간 기반)
    @PostMapping("/calculate")
    public ResponseEntity<InsuranceSettlementSummaryResDto> calculateSettlement(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody @Valid InsuranceSettlementCalcReqDto reqDto,
            @PageableDefault(size = 10) Pageable pageable) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                insuranceSettlementService.calculateSettlement(companyId, reqDto, pageable));
    }

//    사원별 보험료 상세
    @GetMapping("/{settlementId}")
    public ResponseEntity<InsuranceSettlementDetailResDto> getSettlementDetail(@RequestHeader("X-User-Company")UUID companyId, @PathVariable Long settlementId){

        return ResponseEntity.ok(insuranceSettlementService.getSettlementDetail(companyId, settlementId));
    }


//    정산보험료 급여대장에 반형
    @PostMapping("/apply-to-payroll")
    public ResponseEntity<Void> applyToPayroll(@RequestHeader("X-User-Company")UUID companyId, @RequestBody @Valid InsuranceSettlementApplyReqDto reqDto){

        insuranceSettlementService.applyToPayroll(companyId, reqDto);
        return ResponseEntity.ok().build();
    }
}
