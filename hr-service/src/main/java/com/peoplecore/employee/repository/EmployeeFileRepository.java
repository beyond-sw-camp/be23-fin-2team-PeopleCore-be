package com.peoplecore.employee.repository;

import com.peoplecore.employee.domain.EmployeeFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeFileRepository extends JpaRepository<EmployeeFile, Long> {
}
