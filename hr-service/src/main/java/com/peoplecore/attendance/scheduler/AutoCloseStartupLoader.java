package com.peoplecore.attendance.scheduler;

import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.WorkGroupRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/*
 * 앱 기동 시 기존 전 근무그룹에 대해 자동마감 cron 등록.
 * ApplicationReadyEvent 시점 사용 — DB 커넥션/빈 초기화 완료 후 안전하게 조회.
 * 재기동 시 DB 에 남아있는 활성 그룹(soft delete 아닌 것) 전부 등록.
 */
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

    /*
     * 앱 기동 완료 시점에 호출.
     * 회사 unbound 로 findAll 후 groupDeleteAt IS NULL 인 것만 register.
     * findAll 은 전사 그룹이라 멀티 회사 환경에서도 문제 없음.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerAllOnStartup() {
        List<WorkGroup> all = workGroupRepository.findAll();
        int registered = 0;
        for (WorkGroup wg : all) {
            if (wg.getGroupDeleteAt() != null) continue;
            schedulerManager.register(wg);
            registered++;
        }
        log.info("[AutoCloseStartup] 기동 시 자동마감 cron 등록 완료 — total={}, registered={}",
                all.size(), registered);
    }
}