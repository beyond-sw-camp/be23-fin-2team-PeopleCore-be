package com.peoplecore.hrorder.service;

import com.peoplecore.hrorder.repository.HrOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class HrOrderService {

    private final HrOrderRepository hrOrderRepository;

    public HrOrderService(HrOrderRepository hrOrderRepository) {
        this.hrOrderRepository = hrOrderRepository;
    }
}