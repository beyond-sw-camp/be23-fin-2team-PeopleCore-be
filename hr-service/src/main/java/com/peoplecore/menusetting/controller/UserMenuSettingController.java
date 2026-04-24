package com.peoplecore.menusetting.controller;

import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.menusetting.dto.UserMenuItemResponse;
import com.peoplecore.menusetting.dto.UserMenuSettingUpdateRequest;
import com.peoplecore.menusetting.service.UserMenuSettingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 사용자 사이드바 메뉴 설정 API
 * - 인증은 Gateway 에서 처리 (X-User-Id / X-User-Role 헤더 주입)
 * - 모든 사원이 자기 설정은 자유롭게 조회/수정 가능하므로 @RoleRequired 불필요
 */
@RestController
@RequestMapping("/menu-settings")
public class UserMenuSettingController {

    @Autowired
    private UserMenuSettingService service;

    /** 내 사이드바 설정 조회 (최초 조회 시 기본값 자동 생성) */
    @GetMapping("/me")
    public ResponseEntity<List<UserMenuItemResponse>> getMySettings(
            @RequestHeader("X-User-Id") String empId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(
                service.getMySettings(Long.valueOf(empId), EmpRole.valueOf(role))
        );
    }

    /** 내 사이드바 설정 일괄 저장 (토글 + 순서) */
    @PutMapping("/me")
    public ResponseEntity<List<UserMenuItemResponse>> updateMySettings(
            @RequestHeader("X-User-Id") String empId,
            @RequestHeader("X-User-Role") String role,
            @RequestBody @Valid UserMenuSettingUpdateRequest request) {
        return ResponseEntity.ok(
                service.updateMySettings(Long.valueOf(empId), EmpRole.valueOf(role), request)
        );
    }
}
