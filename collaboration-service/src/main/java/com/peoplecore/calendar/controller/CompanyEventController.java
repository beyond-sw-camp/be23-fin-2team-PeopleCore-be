package com.peoplecore.calendar.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.calendar.dtos.CompanyEventReqDto;
import com.peoplecore.calendar.dtos.CompanyEventResDto;
import com.peoplecore.calendar.service.CompanyEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/calendar/company-events")
@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
public class CompanyEventController {

    private final CompanyEventService companyEventService;

    @Autowired
    public CompanyEventController(CompanyEventService companyEventService) {
        this.companyEventService = companyEventService;
    }

//    전사일정 추가
    @PostMapping
    public ResponseEntity<CompanyEventResDto> createCompanyEvent(
            @RequestHeader("X-User-Company")UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody CompanyEventReqDto reqDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(companyEventService.createCompanyEvent(companyId, empId, reqDto));
    }


//    전사일정 수정
    @PutMapping("/{eventsId}")
    public ResponseEntity<CompanyEventResDto> updateCompanyEvent(
            @RequestHeader("X-User-Company")UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long eventsId,
            @RequestBody CompanyEventReqDto reqDto){
        return ResponseEntity.ok(companyEventService.updateCompanyEvent(companyId, empId, eventsId, reqDto));
    }


//    전사일정 삭제
    @DeleteMapping("/{eventsId}")
    public ResponseEntity<Void> deleteCompanyEvent(
            @RequestHeader("X-User-Company")UUID companyId,
            @PathVariable Long eventsId){
        companyEventService.deleteCompanyEvent(companyId, eventsId);
        return ResponseEntity.noContent().build();
    }


}