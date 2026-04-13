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

@RoleRequired("HR_SUPER_ADMIN")
@RestController
@RequestMapping("/overtime/policy")
public class OverTimePolicyController {

    private final OverTimePolicyService overTimePolicyService;

    @Autowired
    public OverTimePolicyController(OverTimePolicyService overTimePolicyService) {
        this.overTimePolicyService = overTimePolicyService;
    }

    /*정책 조회*/
    @GetMapping
    public ResponseEntity<OverTimePolicyResDto> getOverTimePolicy(@RequestHeader("X-User-Company") UUID companyId) {

        return ResponseEntity.status(HttpStatus.OK).body(overTimePolicyService.getOverTimePolicy(companyId));
    }


    /*정책 수정, 생성*/
    @PutMapping
    public ResponseEntity<OverTimePolicyResDto> createOverTimePolicy(@RequestHeader("X-User-Company") UUID companyId, @RequestHeader("X-User-Id") Long empId, @RequestHeader("X-User-Name") String empName, @RequestBody OverTimePolicyReqDto dto) {
        return ResponseEntity.ok(overTimePolicyService.createOverTimePolicy(companyId, empId, empName, dto));
    }


}
