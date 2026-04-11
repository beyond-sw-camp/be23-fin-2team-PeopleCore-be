package com.peoplecore.hrorder.scheduler;

import com.peoplecore.hrorder.service.HrOrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HrOrderScheduler {

    private final HrOrderService hrOrderService;

    public HrOrderScheduler(HrOrderService hrOrderService) {
        this.hrOrderService = hrOrderService;
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void applyConfirmedOrders(){
        hrOrderService.applyAllScheduledOrders();
    }
}
