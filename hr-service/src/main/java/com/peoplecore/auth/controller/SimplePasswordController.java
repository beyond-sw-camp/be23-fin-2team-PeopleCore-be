package com.peoplecore.auth.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.auth.dto.SimplePasswordChangeRequest;
import com.peoplecore.auth.dto.SimplePasswordRemoveRequest;
import com.peoplecore.auth.dto.SimplePasswordSetRequest;
import com.peoplecore.auth.dto.SimplePasswordStatusResponse;
import com.peoplecore.auth.service.SimplePasswordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/simple-password")
@RoleRequired({"EMPLOYEE", "HR_ADMIN", "HR_SUPER_ADMIN"})
public class SimplePasswordController {

    private final SimplePasswordService simplePasswordService;

    @Autowired
    public SimplePasswordController(SimplePasswordService simplePasswordService) {
        this.simplePasswordService = simplePasswordService;
    }


    @GetMapping("/status")
    public ResponseEntity<SimplePasswordStatusResponse> status(@RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(simplePasswordService.status(empId));
    }

    @PostMapping
    public ResponseEntity<Void> set(@RequestHeader("X-User-Id") Long empId,
                                    @RequestBody SimplePasswordSetRequest req) {
        simplePasswordService.set(empId, req.getLoginPassword(), req.getNewPin());
        return ResponseEntity.ok().build();
    }

    @PutMapping
    public ResponseEntity<Void> change(@RequestHeader("X-User-Id") Long empId,
                                       @RequestBody SimplePasswordChangeRequest req) {
        simplePasswordService.change(empId, req.getCurrentPin(), req.getNewPin());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(@RequestHeader("X-User-Id") Long empId,
                                       @RequestBody SimplePasswordRemoveRequest req) {
        simplePasswordService.remove(empId, req.getLoginPassword());
        return ResponseEntity.ok().build();
    }
}

