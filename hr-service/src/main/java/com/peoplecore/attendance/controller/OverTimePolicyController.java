package com.peoplecore.attendance.controller;

import com.peoplecore.attendance.dto.OverTimePolicyReqDto;
import com.peoplecore.attendance.dto.OverTimePolicyResDto;
import com.peoplecore.attendance.service.OverTimePolicyService;
import com.peoplecore.auth.RoleRequired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/overtime/policy")
public class OverTimePolicyController {

    private final OverTimePolicyService overTimePolicyService;

    @Autowired
    public OverTimePolicyController(OverTimePolicyService overTimePolicyService) {
        this.overTimePolicyService = overTimePolicyService;
    }

    /* 정책 조회 — HR_ADMIN 도 조회 가능 (근태현황 화면에서 주간 최대시간/경고 표시에 사용) */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping
    public ResponseEntity<OverTimePolicyResDto> getOverTimePolicy(@RequestHeader("X-User-Company") UUID companyId) {

        return ResponseEntity.status(HttpStatus.OK).body(overTimePolicyService.getOverTimePolicy(companyId));
    }


    /* 정책 수정/생성 — SUPER_ADMIN 만 */
    @RoleRequired("HR_SUPER_ADMIN")
    @PutMapping
    public ResponseEntity<OverTimePolicyResDto> createOverTimePolicy(@RequestHeader("X-User-Company") UUID companyId, @RequestHeader("X-User-Id") Long empId, @RequestHeader("X-User-Name") String empName, @RequestBody OverTimePolicyReqDto dto) {
        return ResponseEntity.ok(overTimePolicyService.createOverTimePolicy(companyId, empId, empName, dto));
    }


}
