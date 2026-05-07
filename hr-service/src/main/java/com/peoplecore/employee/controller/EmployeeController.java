package com.peoplecore.employee.controller;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.EmpType;
import com.peoplecore.employee.domain.EmployeeSortField;
import com.peoplecore.employee.dto.EmpDetailResponseDto;
import com.peoplecore.employee.dto.EmployeeCreateRequestDto;
import com.peoplecore.employee.dto.EmployeeCardResponseDto;
import com.peoplecore.employee.dto.EmployeeListDto;
import com.peoplecore.employee.dto.EmployeeUpdateRequestDto;
import com.peoplecore.employee.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
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
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) EmpType empType,
            @RequestParam(required = false) EmpStatus empStatus,
            @RequestParam(required = false) EmployeeSortField sortField,
            @RequestParam(required = false) Sort.Direction sortDirection,
            Pageable pageable){
        return ResponseEntity.ok(employeeService.getEmployee(companyId, keyword,deptId,empType,empStatus, sortField, sortDirection, pageable));
    }


//    2.상단 카드(전체/재직/휴직/이번달 입사)
    @GetMapping("/card")
    public ResponseEntity<EmployeeCardResponseDto>getCard(@RequestHeader("X-User-Company") UUID companyId){
        return ResponseEntity.ok(employeeService.getCard(companyId));

    }

////    3. 신규등록
    @PostMapping
    public ResponseEntity<Long> createEmployee(@RequestHeader("X-User-Company")UUID companyId,
                                              @Valid @ModelAttribute EmployeeCreateRequestDto responseDto,
                                              @RequestPart(required = false) List<MultipartFile> files,
                                               @RequestPart(required = false) MultipartFile profileImage){
        return ResponseEntity.ok(employeeService.createEmployee(companyId, responseDto,files,profileImage));
    }

//    3-1. 사번 미리보기 (락 x)
    @GetMapping("/preview-empnum")
    public ResponseEntity<String> previewEmpNum(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hireDate){
        return ResponseEntity.ok(employeeService.previewEmpNum(companyId, hireDate));
    }




//    4. 상세 조희
    @GetMapping("/{empId}")
    public ResponseEntity<EmpDetailResponseDto>getEmpDetail(@RequestHeader("X-User-Company")UUID companyId,@PathVariable Long empId){
        return ResponseEntity.ok(employeeService.getEmployeeDetail(companyId,empId));
    }

//    5. 정보 수정
    @PutMapping("/{empId}")
    public ResponseEntity<EmpDetailResponseDto> updateEmployee(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId,
            @Valid @ModelAttribute EmployeeUpdateRequestDto requestDto,
            @RequestPart(required = false) MultipartFile profileImage,
            @RequestPart(required = false) List<MultipartFile> newFiles,
            @RequestParam(required = false) List<Long> deleteFileIds) {
        return ResponseEntity.ok(employeeService.updateEmployee(companyId, empId, requestDto, profileImage, newFiles, deleteFileIds));
    }

//    5-1. 인사 서류 다운로드
    @GetMapping("/{empId}/files/{fileId}")
    public ResponseEntity<Resource> downloadEmployeeFile(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId,
            @PathVariable Long fileId) {
        return employeeService.downloadEmployeeFile(companyId, empId, fileId);
    }

//    6. 삭제
    @DeleteMapping("/{empId}")
    public ResponseEntity<Void>deleteEmployee(
            @RequestHeader("X-User-Company")UUID companyId,
            @PathVariable Long empId){
        employeeService.deleteEmployee(companyId,empId);
        return ResponseEntity.ok().build();
    }






}
