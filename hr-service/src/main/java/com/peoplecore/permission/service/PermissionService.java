/*
package com.peoplecore.permission.service;

import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.permission.domain.Permission;
import com.peoplecore.permission.domain.PermissionStatus;
import com.peoplecore.permission.dto.AdminUserResDto;
import com.peoplecore.permission.dto.PermissionHistoryResDto;
import com.peoplecore.permission.repository.PermissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

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
        Page<AdminUserResDto> page = permissionRepository.findAdminList(companyId, keyword, deptId, empRole, sortField, pageable);

        // SUPER_ADMIN 사원의 최신 GRANTED 이력에서 부여일 매핑
        List<Long> superAdminIds = new ArrayList<>();
        for (AdminUserResDto u : page.getContent()) {
            if (u.getEmpRole() == EmpRole.HR_SUPER_ADMIN) {
                superAdminIds.add(u.getEmpId());
            }
        }

        if (!superAdminIds.isEmpty()) {
            Map<Long, LocalDateTime> grantedMap = new HashMap<>();
            List<Permission> latestGrants = permissionRepository.findLatestByEmpIdsAndStatus(superAdminIds, PermissionStatus.GRANTED);
            for (Permission p : latestGrants) {
                grantedMap.put(p.getEmployee().getEmpId(), p.getProcessedAt());
            }
            for (AdminUserResDto dto : page.getContent()) {
                if (grantedMap.containsKey(dto.getEmpId())) {
                    dto.setGrantedAt(grantedMap.get(dto.getEmpId()));
                }
            }
        }

        return page;
    }

//    Super admin권한 부여
    public void grantSuperAdmin(Long empId, UUID companyId, Long grantorEmpId){
        Employee employee = findEmployeeWithCompanyCheck(empId, companyId);
        Employee grantor = employeeRepository.findById(grantorEmpId)
                .orElseThrow(() -> new IllegalArgumentException("수행자를 찾을 수 없습니다"));

//    사원권한 변경
    employee.updateRole(EmpRole.HR_SUPER_ADMIN);

//    권한 변경 이력 저장
        Permission permission = Permission.builder()
                .employee(employee)
                .grantor(grantor)
                .requestedRole(EmpRole.HR_SUPER_ADMIN)
                .currentRole(employee.getEmpRole())
                .status(PermissionStatus.GRANTED)
                .createdAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build();
        permissionRepository.save(permission);

}

//  super_admin권한 회수 ->admin
public void revokeSuperAdmin(Long empId, UUID companyId, Long grantorEmpId) {
    Employee employee = findEmployeeWithCompanyCheck(empId, companyId);
    Employee grantor = employeeRepository.findById(grantorEmpId)
            .orElseThrow(() -> new IllegalArgumentException("수행자를 찾을 수 없습니다"));

    // 사원 권한 변경
    employee.updateRole(EmpRole.HR_ADMIN);

    // 권한 변경 이력 저장
    Permission permission = Permission.builder()
            .employee(employee)
            .grantor(grantor)
            .empName(employee.getEmpName())
            .requestedRole(EmpRole.HR_ADMIN)
            .currentRole(employee.getEmpRole())
            .status(PermissionStatus.REVOKED)
            .createdAt(LocalDateTime.now())
            .processedAt(LocalDateTime.now())
            .build();
    permissionRepository.save(permission);
}
    // 권한 변경 이력 조회 (최신순)
    @Transactional(readOnly = true)
    public List<PermissionHistoryResDto> getHistory(UUID companyId) {
        List<Permission> history = permissionRepository.findAllByCompanyOrderByProcessedAtDesc(companyId);
        List<PermissionHistoryResDto> result = new ArrayList<>();
        for (Permission p : history) {
            result.add(PermissionHistoryResDto.fromEntity(p));
        }
        return result;
    }

private Employee findEmployeeWithCompanyCheck(Long empId, UUID companyId){
    Employee employee = employeeRepository.findById(empId).orElseThrow(()->new IllegalArgumentException("사원을 찾을 수 없습니다"));

    if(!employee.getCompany().getCompanyId().equals(companyId)){
        throw new IllegalArgumentException("같은 회사의 사원만 권한을 변경할 수 있습니다");
    }
    return employee;
    }
}
*/
