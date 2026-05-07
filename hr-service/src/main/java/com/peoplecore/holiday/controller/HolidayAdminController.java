package com.peoplecore.holiday.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.holiday.dtos.HolidayReqDto;
import com.peoplecore.holiday.dtos.HolidayResDto;
import com.peoplecore.holiday.service.HolidayAdminService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/holiday/admin")
public class HolidayAdminController {

    private final HolidayAdminService holidayAdminService;

    @Autowired
    public HolidayAdminController(HolidayAdminService holidayAdminService) {
        this.holidayAdminService = holidayAdminService;
    }


    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping
    public ResponseEntity<List<HolidayResDto>> list(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam("year") int year,
            @RequestParam(value = "type", required = false, defaultValue = "ALL") String type) {
        return ResponseEntity.ok(holidayAdminService.list(companyId, year, type));
    }


    @RoleRequired("HR_SUPER_ADMIN")
    @PostMapping
    public ResponseEntity<HolidayResDto> create(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @Valid @RequestBody HolidayReqDto reqDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(holidayAdminService.create(companyId, empId, reqDto));
    }


    @RoleRequired("HR_SUPER_ADMIN")
    @PutMapping("/{holidayId}")
    public ResponseEntity<HolidayResDto> update(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long holidayId,
            @Valid @RequestBody HolidayReqDto reqDto) {
        return ResponseEntity.ok(holidayAdminService.update(companyId, empId, holidayId, reqDto));
    }


    @RoleRequired("HR_SUPER_ADMIN")
    @DeleteMapping("/{holidayId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long holidayId) {
        holidayAdminService.delete(companyId, holidayId);
        return ResponseEntity.noContent().build();
    }
}
