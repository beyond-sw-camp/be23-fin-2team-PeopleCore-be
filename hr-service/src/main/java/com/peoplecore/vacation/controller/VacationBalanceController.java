package com.peoplecore.vacation.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.vacation.dto.VacationBalanceResponse;
import com.peoplecore.vacation.dto.VacationGrantRequest;
import com.peoplecore.vacation.service.VacationBalanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/* 휴가 잔여 Controller - 사원 조회 + 관리자 부여 */
@RestController
@RequestMapping("/vacation/balances")
public class VacationBalanceController {

    private final VacationBalanceService vacationBalanceService;

    @Autowired
    public VacationBalanceController(VacationBalanceService vacationBalanceService) {
        this.vacationBalanceService = vacationBalanceService;
    }

    /* 내 잔여 목록 - year 생략 시 올해. 로그인 사원 누구나 */
    @GetMapping("/me")
    public ResponseEntity<List<VacationBalanceResponse>> listMine(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(vacationBalanceService.listMine(companyId, empId, year));
    }

    /* 관리자 일괄 부여 - 다수 사원 선택. managerId 는 X-User-Id 에서 추출 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PostMapping("/grant")
    public ResponseEntity<Void> grant(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerId,
            @RequestBody VacationGrantRequest request) {
        vacationBalanceService.grantBulk(companyId, managerId, request);
        return ResponseEntity.ok().build();
    }
}