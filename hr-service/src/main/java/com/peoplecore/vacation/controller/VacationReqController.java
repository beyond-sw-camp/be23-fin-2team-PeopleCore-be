package com.peoplecore.vacation.controller;

import com.peoplecore.vacation.dto.VacationSubmitRequest;
import com.peoplecore.vacation.service.VacationReqService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 사원용 휴가 신청 컨트롤러 — 헤더로 사원 분리 */
@RestController
@RequestMapping("/attendance/vacation")
public class VacationReqController {

    private final VacationReqService vacationReqService;

    @Autowired
    public VacationReqController(VacationReqService vacationReqService) {
        this.vacationReqService = vacationReqService;
    }

    /** "확인" 클릭 → vacReqId 반환. 부서명은 hr-service 가 deptId 로 조회 후 스냅샷 */
    @PostMapping
    public ResponseEntity<Long> submit(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Name") String empName,
            @RequestHeader(value = "X-User-Department", required = false) Long deptId,
            @RequestHeader(value = "X-User-Grade", required = false) String empGrade,
            @RequestHeader(value = "X-User-Title", required = false) String empTitle,
            @RequestBody VacationSubmitRequest request
    ) {
        Long vacReqId = vacationReqService.submit(
                companyId, empId, empName, deptId, empGrade, empTitle, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(vacReqId);
    }
}
