package com.peoplecore.auth.repository;

import com.peoplecore.auth.domain.FaceRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FaceRegistrationRepository extends JpaRepository<FaceRegistration, Long> {
    Optional<FaceRegistration> findByEmpId(Long empId);
    boolean existsByEmpId(Long empId);
}
