package com.peoplecore.hr_service.department.service;

import com.peoplecore.common.exception.CustomException;
import com.peoplecore.common.exception.ErrorCode;
import com.peoplecore.hr_service.department.domain.Department;
import com.peoplecore.hr_service.department.domain.UseStatus;
import com.peoplecore.hr_service.department.dto.DepartmentCreateRequest;
import com.peoplecore.hr_service.department.dto.DepartmentResponse;
import com.peoplecore.hr_service.department.dto.DepartmentUpdateRequest;
import com.peoplecore.hr_service.department.repository.DepartmentRepository;
import com.peoplecore.hr_service.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    /**
     * 조직도 트리 조회 — 최상위 부서부터 재귀적으로 하위 부서를 포함
     */
    public List<DepartmentResponse> getOrgTree(UUID companyId) {
        List<Department> allDepts = departmentRepository
                .findByCompanyIdAndIsUseOrderByDeptNameAsc(companyId, UseStatus.Y);

        // 최상위 부서 (parentDeptId == null)
        List<Department> roots = allDepts.stream()
                .filter(d -> d.getParentDeptId() == null)
                .toList();

        return roots.stream()
                .map(root -> buildTree(root, allDepts, companyId))
                .toList();
    }

    /**
     * 전체 부서 플랫 리스트 조회
     */
    public List<DepartmentResponse> getAllDepartments(UUID companyId) {
        return departmentRepository
                .findByCompanyIdAndIsUseOrderByDeptNameAsc(companyId, UseStatus.Y)
                .stream()
                .map(dept -> DepartmentResponse.from(dept, countMembers(companyId, dept.getId())))
                .toList();
    }

    /**
     * 부서 단건 조회
     */
    public DepartmentResponse getDepartment(UUID companyId, Long deptId) {
        Department dept = findDepartmentOrThrow(companyId, deptId);
        long memberCount = countMembers(companyId, deptId);
        return DepartmentResponse.from(dept, memberCount);
    }

    /**
     * 부서 등록
     */
    @Transactional
    public DepartmentResponse createDepartment(UUID companyId, DepartmentCreateRequest request) {
        // 부서명 중복 검사
        if (departmentRepository.existsByCompanyIdAndDeptName(companyId, request.getDeptName())) {
            throw new CustomException(ErrorCode.DEPARTMENT_NAME_DUPLICATE);
        }
        // 부서코드 중복 검사
        if (departmentRepository.existsByCompanyIdAndDeptCode(companyId, request.getDeptCode())) {
            throw new CustomException(ErrorCode.DEPARTMENT_CODE_DUPLICATE);
        }
        // 상위부서 존재 검사
        if (request.getParentDeptId() != null) {
            findDepartmentOrThrow(companyId, request.getParentDeptId());
        }

        Department dept = Department.builder()
                .companyId(companyId)
                .parentDeptId(request.getParentDeptId())
                .deptName(request.getDeptName())
                .deptCode(request.getDeptCode().toUpperCase())
                .build();

        Department saved = departmentRepository.save(dept);
        return DepartmentResponse.from(saved, 0);
    }

    /**
     * 부서 수정 (이름, 코드, 상위부서 변경)
     */
    @Transactional
    public DepartmentResponse updateDepartment(UUID companyId, Long deptId, DepartmentUpdateRequest request) {
        Department dept = findDepartmentOrThrow(companyId, deptId);

        if (request.getDeptName() != null && !request.getDeptName().equals(dept.getDeptName())) {
            if (departmentRepository.existsByCompanyIdAndDeptName(companyId, request.getDeptName())) {
                throw new CustomException(ErrorCode.DEPARTMENT_NAME_DUPLICATE);
            }
            dept.updateName(request.getDeptName());
        }

        if (request.getDeptCode() != null && !request.getDeptCode().equals(dept.getDeptCode())) {
            if (departmentRepository.existsByCompanyIdAndDeptCode(companyId, request.getDeptCode().toUpperCase())) {
                throw new CustomException(ErrorCode.DEPARTMENT_CODE_DUPLICATE);
            }
            dept.updateCode(request.getDeptCode().toUpperCase());
        }

        if (request.getParentDeptId() != null) {
            if (request.getParentDeptId().equals(deptId)) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }
            findDepartmentOrThrow(companyId, request.getParentDeptId());
            dept.updateParent(request.getParentDeptId());
        }

        long memberCount = countMembers(companyId, deptId);
        return DepartmentResponse.from(dept, memberCount);
    }

    /**
     * 부서 삭제 (소속 인원/하위 부서 확인 후 비활성화)
     */
    @Transactional
    public void deleteDepartment(UUID companyId, Long deptId) {
        Department dept = findDepartmentOrThrow(companyId, deptId);

        long memberCount = countMembers(companyId, deptId);
        if (memberCount > 0) {
            throw new CustomException(ErrorCode.DEPARTMENT_HAS_MEMBERS);
        }

        if (departmentRepository.existsByParentDeptIdAndIsUse(deptId, UseStatus.Y)) {
            throw new CustomException(ErrorCode.DEPARTMENT_HAS_CHILDREN);
        }

        dept.deactivate();
    }

    // ── private helpers ──

    private Department findDepartmentOrThrow(UUID companyId, Long deptId) {
        return departmentRepository.findByIdAndCompanyId(deptId, companyId)
                .filter(d -> d.getIsUse() == UseStatus.Y)
                .orElseThrow(() -> new CustomException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    private long countMembers(UUID companyId, Long deptId) {
        return employeeRepository.countByCompanyIdAndDeptId(companyId, deptId);
    }

    private DepartmentResponse buildTree(Department dept, List<Department> allDepts, UUID companyId) {
        List<DepartmentResponse> children = allDepts.stream()
                .filter(d -> dept.getId().equals(d.getParentDeptId()))
                .map(child -> buildTree(child, allDepts, companyId))
                .toList();

        long memberCount = countMembers(companyId, dept.getId());
        return DepartmentResponse.withChildren(dept, memberCount, children);
    }
}
