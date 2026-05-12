package com.peoplecore.attendance.scheduler;

import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.WorkGroupRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/* 첫 도입/DB 갈아엎음/register 누락 보정 용도. register 자체가 멱등이라 매 부팅 호출 안전 */
@Component
@Slf4j
public class AutoCloseStartupLoader {

    private final WorkGroupRepository workGroupRepository;
    private final AutoCloseSchedulerManager schedulerManager;

    @Autowired
    public AutoCloseStartupLoader(WorkGroupRepository workGroupRepository,
                                   AutoCloseSchedulerManager schedulerManager) {
        this.workGroupRepository = workGroupRepository;
        this.schedulerManager = schedulerManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerAllOnStartup() {
        List<WorkGroup> all = workGroupRepository.findAll();
        int registered = 0;
        for (WorkGroup wg : all) {
            if (wg.getGroupDeleteAt() != null) continue;
            schedulerManager.register(wg);
            registered++;
        }
        log.info("[AutoCloseStartup] Trigger 등록 완료 — total={}, registered={}", all.size(), registered);
    }
}
