package com.peoplecore.hrorder.repository;

import com.peoplecore.hrorder.domain.HrOrder;
import com.peoplecore.hrorder.domain.OrderStatus;
import com.peoplecore.hrorder.domain.OrderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface HrOrderRepositoryCustom {

    Page<HrOrder> findAllWithFilter(UUID companyId, String keyword, OrderType orderType, OrderStatus status, Pageable pageable);
}
