package com.peoplecore.auth.repository;

import com.peoplecore.auth.domain.LoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.awt.print.Pageable;
import java.util.List;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
    List<LoginHistory> findByEmpIdOrderByLoginAtDesc(Long empId);
}