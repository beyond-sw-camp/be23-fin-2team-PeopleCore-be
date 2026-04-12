package com.peoplecore.permission.repository;

import com.peoplecore.permission.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long>,PermissionRepositoryCustom {

}
