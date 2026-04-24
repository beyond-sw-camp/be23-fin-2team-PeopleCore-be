package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.service.MySalaryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/my")
@RoleRequired({"EMPLOYEE","HR_SUPER_ADMIN","HR_ADMIN"})

public class MySalaryController {

    private final MySalaryService mySalaryService;

    public MySalaryController(MySalaryService mySalaryService) {
        this.mySalaryService = mySalaryService;
    }

    /** 내 급여 정보 조회 */
    @GetMapping("/info")
    public ResponseEntity<MySalaryInfoResDto> getMyInfo(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(mySalaryService.getMySalaryInfo(companyId, empId));
    }


    /** 연도별 급여명세서 목록 */
    @GetMapping("/stubs")
    public ResponseEntity<List<PayStubListResDto>> getStubList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam String year) {
        return ResponseEntity.ok(mySalaryService.getPayStubList(companyId, empId, year));
    }

    /** 급여명세서 상세 */
    @GetMapping("/stubs/{stubId}")
    public ResponseEntity<PayStubDetailResDto> getStubDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long stubId) {
        return ResponseEntity.ok(mySalaryService.getPayStubDetail(companyId, empId, stubId));
    }

    /** 퇴직연금 정보 */
    @GetMapping("/pension")
    public ResponseEntity<PensionInfoResDto> getPension(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(mySalaryService.getPensionInfo(companyId, empId));
    }

    /** 급여 계좌 변경 */
    @PutMapping("/account")
    public ResponseEntity<Void> updateAccount(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody @Valid AccountUpdateReqDto req) {
        mySalaryService.updateSalaryAccount(companyId, empId, req);
        return ResponseEntity.noContent().build();
    }

}
