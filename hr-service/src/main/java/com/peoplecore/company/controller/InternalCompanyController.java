package com.peoplecore.company.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.domain.ContractType;
import com.peoplecore.company.dtos.CompanyCreateReqDto;
import com.peoplecore.company.dtos.CompanyResDto;
import com.peoplecore.company.dtos.CopilotContextResDto;
import com.peoplecore.company.dtos.CopilotContextUpdateReqDto;
import com.peoplecore.company.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/companies")
public class InternalCompanyController {

    private final CompanyService companyService;
    @Autowired
    public InternalCompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

//    회사등록
    @PostMapping
    public ResponseEntity<CompanyResDto> createCompany(@RequestBody @Valid CompanyCreateReqDto reqDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.createCompany(reqDto));
    }

    // 회사 단건 조회
    @GetMapping("/{companyId}")
    public ResponseEntity<CompanyResDto> getCompany(@PathVariable UUID companyId) {
        return ResponseEntity.ok(companyService.getCompany(companyId));
    }

    // 회사 목록 조회
    @GetMapping
    public ResponseEntity<List<CompanyResDto>> getCompanies(
            @RequestParam(required = false) CompanyStatus status) {
        return ResponseEntity.ok(companyService.getCompanies(status));
    }

    // 회사 상태 변경
    @PatchMapping("/{companyId}/status")
    public ResponseEntity<CompanyResDto> updateStatus(
            @PathVariable UUID companyId,
            @RequestParam CompanyStatus status) {
        return ResponseEntity.ok(companyService.updateStatus(companyId, status));
    }

    // 계약 연장
    @PatchMapping("/{companyId}/contract/extend")
    public ResponseEntity<CompanyResDto> extendContract(
            @PathVariable UUID companyId,
            @RequestParam LocalDate newEndDate,
            @RequestParam(required = false) Integer maxEmployees,
            @RequestParam(required = false) ContractType contractType) {
        return ResponseEntity.ok(companyService.extendContract(companyId, newEndDate, maxEmployees, contractType));
    }

    // AI Copilot 컨텍스트 조회 (회사 개요 + 용어 사전 + 운영 정책)
    @RoleRequired({"HR_ADMIN", "HR_SUPER_ADMIN"})
    @GetMapping("/{companyId}/copilot-context")
    public ResponseEntity<CopilotContextResDto> getCopilotContext(@PathVariable UUID companyId) {
        return ResponseEntity.ok(companyService.getCopilotContext(companyId));
    }

    // AI Copilot 컨텍스트 수정 (슈퍼관리자만)
    @RoleRequired({"HR_SUPER_ADMIN"})
    @PutMapping("/{companyId}/copilot-context")
    public ResponseEntity<CopilotContextResDto> updateCopilotContext(
            @PathVariable UUID companyId,
            @RequestBody @Valid CopilotContextUpdateReqDto reqDto) {
        return ResponseEntity.ok(companyService.updateCopilotContext(companyId, reqDto));
    }
}
