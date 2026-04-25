package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.EmpType;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.service.EmpSalaryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/pay/admin/employees")
@RoleRequired({"HR_SUPER_ADMIN","HR_ADMIN"})
public class EmpSalaryController {

    private final EmpSalaryService empSalaryService;

    @Autowired
    public EmpSalaryController(EmpSalaryService empSalaryService) {
        this.empSalaryService = empSalaryService;
    }


    //    사원 급여 목록
    @GetMapping
    public ResponseEntity<Page<EmpSalaryResDto>> getEmpSalaryList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) EmpType empType,
            @RequestParam(required = false) EmpStatus empStatus,
            @RequestParam(required = false) Integer year,
            @PageableDefault(size = 10, sort = {"empName"}) Pageable pageable) {

        return ResponseEntity.ok(empSalaryService.getEmpSalaryList(
                companyId, keyword, deptId, empType, empStatus, year, pageable
        ));
    }

//    급여상세
    @GetMapping("/{empId}")
    public ResponseEntity<EmpSalaryDetailResDto> getEmpSalaryDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId,
            @RequestParam(required = false) Integer year
            ){
        return ResponseEntity.ok(empSalaryService.getEmpSalaryDetail(companyId, empId, year));
}

//    계좌 변경
    @PutMapping("/{empId}/account")
    public ResponseEntity<Void> updateEmpAccount(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId,
            @RequestBody @Valid EmpAccountReqDto reqDto
            ) {

        empSalaryService.updateEmpAccount(companyId, empId, reqDto);
        return ResponseEntity.ok().build();
    }

//    퇴직연금계좌 변경
    @PutMapping("/{empId}/retirement-account")
    public ResponseEntity<Void> updateRetirementAccount(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId,
            @RequestBody @Valid EmpRetirementAccountReqDto reqDto
    ){

        empSalaryService.updateRetirementAccount(companyId, empId, reqDto);
        return ResponseEntity.ok().build();
    }

//     퇴직연금 유형 변경(회사 퇴직연금설정이 DB_DC일때)
    @PutMapping("/{empId}/retirement-type")
    public ResponseEntity<Void> updateRetirementType(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId,
            @Valid @RequestBody RetirementTypeUpdateReqDto request) {

        empSalaryService.updateRetirementType(companyId, empId, request);
        return ResponseEntity.ok().build();
    }


//    월급여 예상지급공제
    @GetMapping("/expected-deductions")
    public ResponseEntity<ExpectedDeductionSummaryResDto> getExpectedDeductions(
            @RequestHeader("X-User-Company") UUID companyId){
        return ResponseEntity.ok(empSalaryService.getExpectedDeductions(companyId));
    }













}
