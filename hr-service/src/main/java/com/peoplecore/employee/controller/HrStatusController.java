package com.peoplecore.employee.controller;


import com.peoplecore.employee.service.HrStatusService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hr-status")
public class HrStatusController {

    private final HrStatusService hrStatusService;

    public HrStatusController(HrStatusService hrStatusService) {
        this.hrStatusService = hrStatusService;
    }

//    인력현황 (조회 join 위주)
}
