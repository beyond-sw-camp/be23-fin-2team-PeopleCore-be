package com.peoplecore.controller;

import com.peoplecore.dto.*;
import com.peoplecore.enums.CompanyStatus;
import com.peoplecore.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 내부 관리자용 회사/계약 관리 API
 *
 * 모든 엔드포인트는 /internal/** 경로로,
 * 내부 관리자(PeopleCore 운영팀)만 접근 가능해야 함
 */
@RestController
@RequestMapping("/internal/companies")
@RequiredArgsConstructor
public class InternalCompanyController {

    private final CompanyService companyService;

    // ════════════════════════════════════════════════════
    // 1. 회사 등록 (기본정보 + 최고관리자 계정)
    //
    // WBS: 관리자-회사등록 > 기본정보 등록
    //       관리자-회사등록 > 최고관리자 계정 생성 및 전달
    // ════════════════════════════════════════════════════

    @PostMapping
    public ResponseEntity<CompanyCreateResponse> createCompany(
            @Valid @RequestBody CompanyCreateRequest request) {
        CompanyCreateResponse response = companyService.createCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ════════════════════════════════════════════════════
    // 2. 회사 단건 조회
    // ════════════════════════════════════════════════════

    @GetMapping("/{companyId}")
    public ResponseEntity<CompanyResponse> getCompany(
            @PathVariable UUID companyId) {
        return ResponseEntity.ok(companyService.getCompany(companyId));
    }

    // ════════════════════════════════════════════════════
    // 3. 회사 목록 조회 (상태별 필터 가능)
    // ════════════════════════════════════════════════════

    @GetMapping
    public ResponseEntity<List<CompanyResponse>> getCompanies(
            @RequestParam(required = false) CompanyStatus status) {
        return ResponseEntity.ok(companyService.getCompanies(status));
    }

    // ════════════════════════════════════════════════════
    // 4. 계약 상태 변경
    //
    // WBS: 관리자-계약관리 > 계약 상태관리
    //       PENDING → ACTIVE → SUSPENDED / EXPIRED
    //       상태별 서비스 접근 제한 처리
    //       상태별 로그인 차단 처리/메시지 노출
    // ════════════════════════════════════════════════════

    @PatchMapping("/{companyId}/status")
    public ResponseEntity<CompanyResponse> updateStatus(
            @PathVariable UUID companyId,
            @Valid @RequestBody CompanyStatusUpdateRequest request) {
        return ResponseEntity.ok(companyService.updateStatus(companyId, request));
    }

    // ════════════════════════════════════════════════════
    // 5. 계약 연장
    //
    // WBS: 관리자-계약관리 > 계약 연장 처리
    //       구두/메일 계약 확정 후 연장 처리
    //       만료일 재설정 / max_employees·계약유형 변경
    //       EXPIRED → ACTIVE 자동 복구 / 알림 초기화
    // ════════════════════════════════════════════════════

    @PatchMapping("/{companyId}/contract/extend")
    public ResponseEntity<CompanyResponse> extendContract(
            @PathVariable UUID companyId,
            @Valid @RequestBody ContractExtendRequest request) {
        return ResponseEntity.ok(companyService.extendContract(companyId, request));
    }
}
