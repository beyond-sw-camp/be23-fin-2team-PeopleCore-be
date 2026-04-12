package com.peoplecore.permission.service;

import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.permission.domain.Permission;
import com.peoplecore.permission.domain.PermissionStatus;
import com.peoplecore.permission.dto.AdminUserResDto;
import com.peoplecore.permission.repository.PermissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final EmployeeRepository employeeRepository;

    public PermissionService(PermissionRepository permissionRepository, EmployeeRepository employeeRepository) {
        this.permissionRepository = permissionRepository;
        this.employeeRepository = employeeRepository;
    }

    // 관리자 목록 조회 (페이징, 검색, 필터, 정렬)
    @Transactional(readOnly = true)
    public Page<AdminUserResDto> getAdminList(UUID companyId, String keyword, Long deptId, EmpRole empRole, String sortField, Pageable pageable) {
        return permissionRepository.findAdminList(companyId, keyword, deptId, empRole, sortField, pageable);
    }

//    Super admin권한 부여
    public void grantSuperAdmin(Long empId, UUID companyId){
        Employee employee = findEmployeeWithCompanyCheck(empId, companyId);

//    사원권한 변경
    employee.updateRole(EmpRole.HR_SUPER_ADMIN);

//    권한 변경 이력 저장
        Permission permission = Permission.builder()
                .employee(employee)
                .requestedRole(EmpRole.HR_SUPER_ADMIN)
                .currentRole(employee.getEmpRole())
                .status(PermissionStatus.GRANTED)
                .createdAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build();
        permissionRepository.save(permission);

}

//  super_admin권한 회수 ->admin
public void revokeSuperAdmin(Long empId, UUID companyId) {
    Employee employee = findEmployeeWithCompanyCheck(empId, companyId);

    // 사원 권한 변경
    employee.updateRole(EmpRole.HR_ADMIN);

    // 권한 변경 이력 저장
    Permission permission = Permission.builder()
            .employee(employee)
            .empName(employee.getEmpName())
            .requestedRole(EmpRole.HR_ADMIN)
            .currentRole(employee.getEmpRole())
            .status(PermissionStatus.REVOKED)
            .createdAt(LocalDateTime.now())
            .processedAt(LocalDateTime.now())
            .build();
    permissionRepository.save(permission);
}
private Employee findEmployeeWithCompanyCheck(Long empId, UUID companyId){
    Employee employee = employeeRepository.findById(empId).orElseThrow(()->new IllegalArgumentException("사원을 찾을 수 없습니다"));

    if(!employee.getCompany().getCompanyId().equals(companyId)){
        throw new IllegalArgumentException("같은 회사의 사원만 권한을 변경할 수 있습니다");
    }
    return employee;
    }
}

