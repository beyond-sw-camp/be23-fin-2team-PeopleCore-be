package com.peoplecore.common.quartz;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Component;

/* JobFailureNotifier 를 Scheduler 에 전역 등록 */
/* SchedulerFactoryBeanCustomizer 는 Spring Boot Quartz 자동 구성이 빌드 단계에 호출 */
/* setGlobalJobListeners → 모든 잡에 자동 적용 (그룹·이름 매처 불필요) */
@Slf4j
@Component
public class QuartzListenerRegistrar implements SchedulerFactoryBeanCustomizer {

    private final JobFailureNotifier notifier;

    @Autowired
    public QuartzListenerRegistrar(JobFailureNotifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public void customize(SchedulerFactoryBean schedulerFactoryBean) {
        schedulerFactoryBean.setGlobalJobListeners(notifier);
        log.info("[QuartzListenerRegistrar] 전역 JobListener 등록 - {}", notifier.getName());
    }
}
