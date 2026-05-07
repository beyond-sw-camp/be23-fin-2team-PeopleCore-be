package com.peoplecore.auth.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.auth.dto.LoginHistoryDto;
import com.peoplecore.auth.service.LoginHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth/login-history")
@RoleRequired({"EMPLOYEE", "HR_ADMIN", "HR_SUPER_ADMIN"})
public class LoginHistoryController {
    private final LoginHistoryService loginHistoryService;

    @Autowired
    public LoginHistoryController(LoginHistoryService loginHistoryService) {
        this.loginHistoryService = loginHistoryService;
    }


    @GetMapping
    public ResponseEntity<List<LoginHistoryDto>> list(
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(loginHistoryService.list(empId, Math.min(limit, 100)));
    }
}
