package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayrollDetails;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollDetailsRepository extends JpaRepository<PayrollDetails, Long> {
    boolean existsByPayItemId(Long payItemId);
}
