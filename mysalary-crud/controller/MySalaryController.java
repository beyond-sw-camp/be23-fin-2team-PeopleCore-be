package com.peoplecore.pay.controller;

import com.peoplecore.pay.dto.*;
import com.peoplecore.pay.service.MySalaryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.List;
import java.util.UUID;

/**
 * 내 급여 조회 컨트롤러 (사원 전체 접근 가능)
 *
 * 모든 사원이 본인의 급여 정보를 조회할 수 있는 API
 * - @RoleRequired 미적용 (전 사원 접근 가능)
 * - X-User-Id 헤더로 본인 식별
 *
 * 파일 위치: pay/controller/MySalaryController.java
 *
 * API 목록:
 * GET  /pay/my/info              - 내 급여 정보 (사원정보 + 연봉 + 수당 + 계좌)
 * GET  /pay/my/stubs             - 급(상)여명세서 목록 (연도별)
 * GET  /pay/my/stubs/{stubId}    - 급여명세서 상세 (지급/공제 항목)
 * POST /pay/my/severance         - 예상 퇴직금 산정
 * GET  /pay/my/pension           - DB/DC 퇴직연금 적립금 조회
 * PUT  /pay/my/account           - 급여 계좌 변경
 */
@RestController
@RequestMapping("/pay/my")
public class MySalaryController {

    @Autowired
    private MySalaryService mySalaryService;

    /**
     * 내 급여 정보 조회
     * 사원 기본 정보 + 연봉/월급 + 고정수당 + 계좌 정보
     */
    @GetMapping("/info")
    public ResponseEntity<MySalaryInfoResDto> getMySalaryInfo(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {

        return ResponseEntity.ok(mySalaryService.getMySalaryInfo(companyId, empId));
    }

    /**
     * 급(상)여명세서 목록 조회 (연도별)
     * @param year 조회 연도 (기본값: 현재 연도)
     */
    @GetMapping("/stubs")
    public ResponseEntity<List<PayStubListResDto>> getPayStubList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam(value = "year", required = false) String year) {

        if (year == null || year.isBlank()) {
            year = String.valueOf(Year.now().getValue());
        }

        return ResponseEntity.ok(mySalaryService.getPayStubList(companyId, empId, year));
    }

    /**
     * 급여명세서 상세 조회
     * 지급항목 / 공제항목 분류 + 총액 + PDF URL
     */
    @GetMapping("/stubs/{stubId}")
    public ResponseEntity<PayStubDetailResDto> getPayStubDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long stubId) {

        return ResponseEntity.ok(mySalaryService.getPayStubDetail(companyId, empId, stubId));
    }

    /**
     * 예상 퇴직금 산정 (근속기준)
     * 예상 퇴사일 기준으로 퇴직금 계산
     */
    @PostMapping("/severance")
    public ResponseEntity<SeveranceEstimateResDto> estimateSeverance(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody @Valid SeveranceEstimateReqDto request) {

        return ResponseEntity.ok(mySalaryService.estimateSeverance(companyId, empId, request));
    }

    /**
     * DB/DC 퇴직연금 적립금 조회
     * 회사 퇴직연금 설정 + 사원 계좌 + 누적 적립금
     */
    @GetMapping("/pension")
    public ResponseEntity<PensionInfoResDto> getPensionInfo(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {

        return ResponseEntity.ok(mySalaryService.getPensionInfo(companyId, empId));
    }

    /**
     * 급여 계좌 변경
     */
    @PutMapping("/account")
    public ResponseEntity<MySalaryInfoResDto.AccountDto> updateSalaryAccount(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody @Valid AccountUpdateReqDto request) {

        return ResponseEntity.ok(mySalaryService.updateSalaryAccount(
                companyId, empId,
                request.getBankName(),
                request.getAccountNumber(),
                request.getAccountHolder()));
    }
}
