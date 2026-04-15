package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.SeverancePays;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeveranceRepository extends JpaRepository<SeverancePays, Long> {
}
