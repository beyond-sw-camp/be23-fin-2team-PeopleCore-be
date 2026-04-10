package com.peoplecore.vacation.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.vacation.dto.VacationGrantBasisDto;
import com.peoplecore.vacation.dto.VacationRuleCreateRequest;
import com.peoplecore.vacation.dto.VacationRuleResponse;
import com.peoplecore.vacation.service.VacationPolicyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/attendence")
@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
public class VacationPolicyController {

    private final VacationPolicyService vacationPolicyService;

    @Autowired
    public VacationPolicyController(VacationPolicyService vacationPolicyService) {
        this.vacationPolicyService = vacationPolicyService;
    }

    /* =============== 연차 지급 기준 =================*/

    /* 연차 지급 기준 조회 */
    @GetMapping("/leave-grant-basis")
    public ResponseEntity<VacationGrantBasisDto> getVacationGrantBasis(@RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(vacationPolicyService.getVacationGrantBasis(companyId));
    }

    /*연자 지급 기준 변경 */
    @PutMapping("/leave-grant-basis")
    public ResponseEntity<VacationGrantBasisDto> updateVacationGrantBasis(@RequestHeader("X-User-Company") UUID companyId, @RequestBody @Valid VacationGrantBasisDto dto) {
        return ResponseEntity.ok(vacationPolicyService.updateVacationGrantBasis(companyId, dto));
    }


    // ==================== 연차 발생 규칙 ====================

    /*연차 발생 규칙 전체 조회 */
    @GetMapping("/leave-rules")
    public ResponseEntity<List<VacationRuleResponse>> getLeaveRules(
            @RequestHeader("X-User-Company") String companyId) {
        return ResponseEntity.ok(
                vacationPolicyService.getVacationRules(UUID.fromString(companyId)));
    }

    /* 연차 발생 규칙 추가 */
    @PostMapping("/leave-rules")
    public ResponseEntity<VacationRuleResponse> createLeaveRule(
            @RequestHeader("X-User-Company") String companyId,
            @RequestHeader("X-User-Id") String empId,
            @RequestBody @Valid VacationRuleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vacationPolicyService.createVacationRule(
                        UUID.fromString(companyId),
                        Long.parseLong(empId),
                        request));
    }

    /* 연차 발생 규칙 수정 */
    @PutMapping("/leave-rules/{id}")
    public ResponseEntity<VacationRuleResponse> updateLeaveRule(
            @PathVariable Long id,
            @RequestBody @Valid VacationRuleCreateRequest request) {
        return ResponseEntity.ok(
                vacationPolicyService.updateLeaveRule(id, request));
    }

    /*연차 발생 규칙 삭제 */
    @DeleteMapping("/leave-rules/{id}")
    public ResponseEntity<Void> deleteLeaveRule(@PathVariable Long id) {
        vacationPolicyService.deleteVacationRule(id);
        return ResponseEntity.noContent().build();
    }
}
