package com.peoplecore.employee.controller;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.EmpType;
import com.peoplecore.employee.domain.EmployeeSortField;
import com.peoplecore.employee.dto.EmployeeKardResponseDto;
import com.peoplecore.employee.dto.EmployeeListDto;
import com.peoplecore.employee.service.EmployeeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/employee")
public class EmployeeController {
//    사원 목록조회(등록)

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }


//    1. 목록 조회, 필터, page
    @GetMapping
    public ResponseEntity<Page<EmployeeListDto>> getEmployee(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) EmpType empType,
            @RequestParam(required = false) EmpStatus empStatus,
            @RequestParam(required = false) EmployeeSortField sortField,
            Pageable pageable){
        return ResponseEntity.ok(employeeService.getEmployee(keyword,deptId,empType,empStatus, sortField, pageable));
    }


//    2.상단 카드(전체/재직/휴직/이번달 입사)
    @GetMapping("/kard")
    public ResponseEntity<EmployeeKardResponseDto>getKard(@RequestHeader("X-User-Company") UUID companyId){
        return ResponseEntity.ok(employeeService.getKard(companyId));

    }
//
////    3. 신규등록
//    @PostMapping
//
////    4. 상세 조희
//    @GetMapping("{empId}")
//
////    5. 정보 수정
//    @PutMapping("{empId}")
//
////    6. 삭제
//    @DeleteMapping("{empId}")
//





}
