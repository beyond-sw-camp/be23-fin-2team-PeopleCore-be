package com.peoplecore.hrorder.repository;

import com.peoplecore.hrorder.domain.HrOrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HrOrderDetailRepository extends JpaRepository<HrOrderDetail, Long> {
    List<HrOrderDetail> findByHrOrder_OrderId(Long orderId);

    void deleteByHrOrder_OrderId(Long orderId);
}
