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
 * 앱 기동 시 활성 근무그룹 전부에 대해 자동마감 cron 멱등 등록.
 *
 * Quartz JDBC 클러스터링이라 트리거가 QRTZ_TRIGGERS 에 영속되므로 "재기동 시 재등록 필요" 가 본 의도는 아님.
 * 본 의도는 다음 두 시나리오 커버:
 *  - 첫 도입 / prod 첫 배포 / dev DB 갈아엎음 → QRTZ_TRIGGERS 비어있음 → INSERT 필요
 *  - WorkGroup.groupStartTime 변경됐는데 어떤 이유로 register 호출 누락된 케이스 → cron 동기화 보정
 *
 * register 자체가 멱등 (동일 cron 이면 skip) 이라 매 부팅 호출 부담 거의 없음.
 *
 * ApplicationReadyEvent 시점 사용 — DB 커넥션/Quartz Scheduler 빈 초기화 완료 후 안전.
 * 멀티 인스턴스 동시 부팅 race 는 register 안에서 ObjectAlreadyExistsException 흡수.
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