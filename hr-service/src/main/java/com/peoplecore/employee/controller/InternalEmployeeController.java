package com.peoplecore.employee.controller;

import com.peoplecore.client.dto.InternalEmployeeResDto;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/employee")
public class InternalEmployeeController {

    private final EmployeeRepository employeeRepository;

    @Autowired
    public InternalEmployeeController(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

//    단건조회
    @GetMapping("/{empId}")
    public ResponseEntity<InternalEmployeeResDto> getEmployee(@PathVariable Long empId){
        Employee employee = employeeRepository.findById(empId).orElseThrow(()-> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        return ResponseEntity.ok(InternalEmployeeResDto.fromEntity(employee));
    }

//    다건조회 (캘린더목록 : 타인정보 일괄 조회용)
    @GetMapping("/bulk")
    public ResponseEntity<List<InternalEmployeeResDto>> getEmployees(@RequestParam List<Long> empIds){
        List<Employee> employees = employeeRepository.findByEmpIdsWithDeptAndGrade(empIds);
        List<InternalEmployeeResDto> result = employees.stream().map(InternalEmployeeResDto::fromEntity).toList();

        return ResponseEntity.ok(result);
    }

}
