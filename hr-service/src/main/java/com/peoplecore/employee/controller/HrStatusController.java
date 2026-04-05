package com.peoplecore.employee.controller;

import com.peoplecore.employee.dto.DeptWorkforceDto;
import com.peoplecore.employee.dto.ExpiringContractDto;
import com.peoplecore.employee.dto.MonthlyTrendDto;
import com.peoplecore.employee.dto.WorkforceSummaryDto;
import com.peoplecore.employee.service.HrStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hr-status")
public class HrStatusController {

    private final HrStatusService hrStatusService;

    public HrStatusController(HrStatusService hrStatusService) {
        this.hrStatusService = hrStatusService;
    }

//    1. 상단 카드 (전체/이번달입사/이번달퇴사/계약만료예정)
    @GetMapping("/summary")
    public ResponseEntity<WorkforceSummaryDto> getSummary(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(hrStatusService.getSummary(companyId));
    }

//    2. 부서별 인원 + 직급별 분포 + 평균 재직연수 (deptId로 필터)
    @GetMapping("/by-dept")
    public ResponseEntity<List<DeptWorkforceDto>> getByDept(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false) Long deptId) {
        return ResponseEntity.ok(hrStatusService.getByDept(companyId, deptId));
    }
//
////    3. 월별 입퇴사 현황
//    @GetMapping("/trend")
//    public ResponseEntity<List<MonthlyTrendDto>> getTrend(
//            @RequestHeader("X-User-Company") UUID companyId) {
//        return ResponseEntity.ok(hrStatusService.getTrend(companyId));
//    }
//
////    4. 계약 만료 예정자 목록
//    @GetMapping("/expiring")
//    public ResponseEntity<List<ExpiringContractDto>> getExpiring(
//            @RequestHeader("X-User-Company") UUID companyId) {
//        return ResponseEntity.ok(hrStatusService.getExpiring(companyId));
//    }
}
