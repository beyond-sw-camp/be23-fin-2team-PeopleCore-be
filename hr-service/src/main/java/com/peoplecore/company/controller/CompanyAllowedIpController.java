package com.peoplecore.company.controller;

import com.peoplecore.attendance.dto.CompanyAllowedIpReqDto;
import com.peoplecore.attendance.dto.CompanyAllowedIpResDto;
import com.peoplecore.auth.RoleRequired;
import com.peoplecore.company.service.CompanyAllowedIpService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 회사 허용 IP 관리 API (인사 담당자 전용).
 * 근무지 외 근태체크 판정 기준 데이터.
 */
@RestController
@RequestMapping("/company/allowed-ips")
@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
public class CompanyAllowedIpController {

    private final CompanyAllowedIpService companyAllowedIpService;

    @Autowired
    public CompanyAllowedIpController(CompanyAllowedIpService companyAllowedIpService) {
        this.companyAllowedIpService = companyAllowedIpService;
    }

    /** 허용 IP 등록 */
    @PostMapping
    public ResponseEntity<CompanyAllowedIpResDto> create(
            @RequestHeader("X-User-Company") UUID companyId,
            @Valid @RequestBody CompanyAllowedIpReqDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyAllowedIpService.create(companyId, dto));
    }

    /** 회사별 허용 IP 전체 목록 (활성/비활성 모두) */
    @GetMapping
    public ResponseEntity<List<CompanyAllowedIpResDto>> list(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(companyAllowedIpService.list(companyId));
    }

    /** 허용 IP 수정 (대역/라벨/활성 일괄) */
    @PutMapping("/{id}")
    public ResponseEntity<CompanyAllowedIpResDto> update(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long id,
            @Valid @RequestBody CompanyAllowedIpReqDto dto) {
        return ResponseEntity.ok(companyAllowedIpService.update(companyId, id, dto));
    }

    /** 활성/비활성 토글 */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<CompanyAllowedIpResDto> toggle(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long id) {
        return ResponseEntity.ok(companyAllowedIpService.toggle(companyId, id));
    }

    /** 허용 IP 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long id) {
        companyAllowedIpService.delete(companyId, id);
        return ResponseEntity.noContent().build();
    }
}