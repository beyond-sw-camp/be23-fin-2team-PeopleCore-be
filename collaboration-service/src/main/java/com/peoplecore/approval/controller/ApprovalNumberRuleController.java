package com.peoplecore.approval.controller;

import com.peoplecore.approval.dto.NumberRuleResponse;
import com.peoplecore.approval.dto.NumberRuleUpdateRequest;
import com.peoplecore.approval.service.ApprovalNumberRuleService;
import com.peoplecore.auth.RoleRequired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequestMapping("/approval/number-rule")
@RestController
public class ApprovalNumberRuleController {
    private final ApprovalNumberRuleService numberRuleService;

    @Autowired
    public ApprovalNumberRuleController(ApprovalNumberRuleService numberRuleService) {
        this.numberRuleService = numberRuleService;
    }

    /*채번 규칙 조회 */
    @GetMapping
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    public ResponseEntity<NumberRuleResponse> getNumberRule(@RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(numberRuleService.getNumberRule(companyId));
    }

    /*채번 규칙 수정 */
    @PutMapping
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    public ResponseEntity<Void> updateNumberRule(@RequestHeader("X-User-Company") UUID companyId,
                                                 @RequestHeader("X-User-Id") Long empId,
                                                 @RequestBody NumberRuleUpdateRequest request) {
        numberRuleService.updateNumberRule(companyId, empId, request);
        return ResponseEntity.ok().build();
    }

}
