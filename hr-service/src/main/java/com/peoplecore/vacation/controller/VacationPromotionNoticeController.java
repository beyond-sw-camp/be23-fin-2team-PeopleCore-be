package com.peoplecore.vacation.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.vacation.dto.VacationPromotionNoticeResponse;
import com.peoplecore.vacation.service.VacationPromotionNoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/* 연차 촉진 통지 이력 Controller - 관리자/사원 조회 */
@RestController
@RequestMapping("/vacation/promotion-notices")
public class VacationPromotionNoticeController {

    private final VacationPromotionNoticeService vacationPromotionNoticeService;

    @Autowired
    public VacationPromotionNoticeController(VacationPromotionNoticeService vacationPromotionNoticeService) {
        this.vacationPromotionNoticeService = vacationPromotionNoticeService;
    }

    /* 관리자 회사 통지 이력 (페이지) - year 필터 선택 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping
    public ResponseEntity<Page<VacationPromotionNoticeResponse>> listForCompany(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false) Integer year,
            @PageableDefault(size = 20, sort = "noticeSentAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(vacationPromotionNoticeService.listForCompany(companyId, year, pageable));
    }

    /* 내 통지 이력 - year 생략 시 올해 */
    @GetMapping("/me")
    public ResponseEntity<List<VacationPromotionNoticeResponse>> listMine(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(vacationPromotionNoticeService.listMine(companyId, empId, year));
    }
}