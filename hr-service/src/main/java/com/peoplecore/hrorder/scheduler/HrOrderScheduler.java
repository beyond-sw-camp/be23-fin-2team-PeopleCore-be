package com.peoplecore.hrorder.scheduler;

import com.peoplecore.hrorder.service.HrOrderService;
import com.peoplecore.resign.service.ResignService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HrOrderScheduler {

    private final HrOrderService hrOrderService;
    private final ResignService resignService;

    public HrOrderScheduler(HrOrderService hrOrderService, ResignService resignService) {
        this.hrOrderService = hrOrderService;
        this.resignService = resignService;
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void applyConfirmedOrders(){
        hrOrderService.applyAllScheduledOrders();
        resignService.processScheduledResigns();
    }
}
