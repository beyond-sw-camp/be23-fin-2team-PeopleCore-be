package com.peoplecore.department.controller;

import com.peoplecore.department.domain.Department;
import com.peoplecore.department.domain.UseStatus;
import com.peoplecore.department.dto.InternalDeptResponseDto;
import com.peoplecore.department.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/dept")
public class InternalDeptController {
    private final DepartmentRepository departmentRepository;

    @Autowired
    public InternalDeptController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @GetMapping("/{deptId}")
    public ResponseEntity<InternalDeptResponseDto> getDept(@PathVariable Long deptId) {
        Department department = departmentRepository.findByDeptIdAndIsUse(deptId, UseStatus.Y).orElseThrow(() -> new RuntimeException("부서를 찾을 수 없습니다."));
        return ResponseEntity.ok(InternalDeptResponseDto.from(department));

    }
}
