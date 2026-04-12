package com.peoplecore.permission.repository;

import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.permission.dto.AdminUserResDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PermissionRepositoryCustom {

//    관리자 목록조회(검색,필터,정렬,페이징)
    Page<AdminUserResDto>findAdminList(UUID companyId, String keyword, Long deptId, EmpRole empRole, String sortField, Pageable pageable);
}
