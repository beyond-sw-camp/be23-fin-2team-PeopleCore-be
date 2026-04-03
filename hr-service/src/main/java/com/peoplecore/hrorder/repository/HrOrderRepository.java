package com.peoplecore.hrorder.repository;

import com.peoplecore.hrorder.domain.HrOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HrOrderRepository extends JpaRepository<HrOrder, Long> {

}
