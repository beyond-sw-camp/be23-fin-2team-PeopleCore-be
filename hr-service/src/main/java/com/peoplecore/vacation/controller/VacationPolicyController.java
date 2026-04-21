package com.peoplecore.vacation.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.vacation.dto.VacationAdvanceUsePolicyDto;
import com.peoplecore.vacation.dto.VacationGrantBasisDto;
import com.peoplecore.vacation.dto.VacationPromotionPolicyDto;
import com.peoplecore.vacation.dto.VacationRuleCreateRequest;
import com.peoplecore.vacation.dto.VacationRuleResponse;
import com.peoplecore.vacation.service.VacationPolicyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/* 연차 정책 Controller - 발생 기준 / 발생 규칙 / 촉진 정책 통합 */
/* 조회는 HR_ADMIN + HR_SUPER_ADMIN, 수정은 HR_SUPER_ADMIN 전용 */
@RestController
@RequestMapping("/vacation/policy")
public class VacationPolicyController {

    private final VacationPolicyService vacationPolicyService;

    @Autowired
    public VacationPolicyController(VacationPolicyService vacationPolicyService) {
        this.vacationPolicyService = vacationPolicyService;
    }

    /* 연차 지급 기준 조회 - HIRE(입사일) / FISCAL(회계연도) */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/grant-basis")
    public ResponseEntity<VacationGrantBasisDto> getGrantBasis(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(vacationPolicyService.getVacationGrantBasis(companyId));
    }

    /* 연차 지급 기준 변경 - FISCAL 선택 시 fiscalYearStart(mm-dd) 필수 (엔티티 검증) */
    @RoleRequired("HR_SUPER_ADMIN")
    @PutMapping("/grant-basis")
    public ResponseEntity<VacationGrantBasisDto> updateGrantBasis(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody VacationGrantBasisDto dto) {
        return ResponseEntity.ok(vacationPolicyService.updateVacationGrantBasis(companyId, dto));
    }

    /* 연차 발생 규칙 전체 조회 - 정책 + 규칙 fetch join */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/rules")
    public ResponseEntity<List<VacationRuleResponse>> getRules(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(vacationPolicyService.getVacationRules(companyId));
    }

    /* 연차 발생 규칙 추가 - X-User-Id 로 생성자 기록 */
    @RoleRequired("HR_SUPER_ADMIN")
    @PostMapping("/rules")
    public ResponseEntity<VacationRuleResponse> createRule(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody VacationRuleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vacationPolicyService.createVacationRule(companyId, empId, request));
    }

    /* 연차 발생 규칙 수정 */
    @RoleRequired("HR_SUPER_ADMIN")
    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<VacationRuleResponse> updateRule(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long ruleId,
            @RequestBody VacationRuleCreateRequest request) {
        return ResponseEntity.ok(vacationPolicyService.updateLeaveRule(companyId,ruleId, request));
    }

    /* 연차 발생 규칙 삭제 */
    @RoleRequired("HR_SUPER_ADMIN")
    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(@RequestHeader("X-User-Company") UUID companyId,@PathVariable Long ruleId) {
        vacationPolicyService.deleteVacationRule(companyId,ruleId);
        return ResponseEntity.noContent().build();
    }

    /* 연차 촉진 정책 조회 - isActive + 1차/2차 통지 시기 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/promotion")
    public ResponseEntity<VacationPromotionPolicyDto> getPromotion(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(vacationPolicyService.getPromotionPolicy(companyId));
    }

    /* 연차 촉진 정책 변경 */
    @RoleRequired("HR_SUPER_ADMIN")
    @PutMapping("/promotion")
    public ResponseEntity<Void> updatePromotion(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody VacationPromotionPolicyDto dto) {
        vacationPolicyService.updatePromotionPolicy(companyId, dto);
        return ResponseEntity.noContent().build();
    }

    /* 미리쓰기 허용 정책 조회 - 연차/월차 잔여 부족 시 신청 허용 토글 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/advance-use")
    public ResponseEntity<VacationAdvanceUsePolicyDto> getAdvanceUse(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(vacationPolicyService.getAdvanceUsePolicy(companyId));
    }

    /* 미리쓰기 허용 정책 변경 */
    @RoleRequired("HR_SUPER_ADMIN")
    @PutMapping("/advance-use")
    public ResponseEntity<Void> updateAdvanceUse(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody VacationAdvanceUsePolicyDto dto) {
        vacationPolicyService.updateAdvanceUsePolicy(companyId, dto);
        return ResponseEntity.noContent().build();
    }
}