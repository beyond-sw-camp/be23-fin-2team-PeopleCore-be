package com.peoplecore.company.controller;

import com.peoplecore.attendance.dto.CompanyAllowedIpReqDto;
import com.peoplecore.attendance.dto.CompanyAllowedIpResDto;
import com.peoplecore.attendance.util.ClientIpExtractor;
import com.peoplecore.auth.RoleRequired;
import com.peoplecore.company.service.CompanyAllowedIpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
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
    private final ClientIpExtractor clientIpExtractor;

    @Autowired
    public CompanyAllowedIpController(CompanyAllowedIpService companyAllowedIpService,
                                      ClientIpExtractor clientIpExtractor) {
        this.companyAllowedIpService = companyAllowedIpService;
        this.clientIpExtractor = clientIpExtractor;
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

    /** 등록 모달용 — 호출자가 백엔드에서 보는 자기 IP 그대로 반환 (출퇴근 체크 IP 매칭과 동일 추출 로직) */
    @GetMapping("/my-ip")
    public ResponseEntity<Map<String, String>> getMyIp(HttpServletRequest request) {
        return ResponseEntity.ok(Map.of("ip", clientIpExtractor.extract(request)));
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