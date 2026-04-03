package com.peoplecore.hrorder.controller;

import com.peoplecore.hrorder.service.HrOrderService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/employee")
public class HrOrderController {
    private final HrOrderService hrOrderService;

    public HrOrderController(HrOrderService hrOrderService) {
        this.hrOrderService = hrOrderService;
    }




}
