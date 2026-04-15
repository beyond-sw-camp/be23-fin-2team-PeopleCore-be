package com.peoplecore.attendance.scheduler;

import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.MyAttendanceQueryRepository;
import com.peoplecore.attendance.service.AutoCloseBatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/*
 * 자동마감 배치의 근무그룹별 동적 스케줄러 관리자.

 * 동작:
 *  - WorkGroup 하나당 cron 1개 등록. cron 시각 = groupStartTime - 2h (해당 일 기준).
 *  - WorkGroup CRUD 시 WorkGroupService 에서 register / unregister 훅 호출.
 *  - 앱 기동 시 ApplicationReadyEvent 리스너가 전 그룹에 대해 register (Step C 에서).
 *
 * 동시성:
 *  - register/unregister 는 synchronized — 같은 workGroupId 의 중복/경합 방지.
 *  - handles 는 ConcurrentHashMap 으로 조회 성능 유지.
 *
 * 시간대:
 *  - Asia/Seoul 고정. 서버 로컬 타임존과 무관하게 정책 시각 준수.
 *
 *
 *
 */
@Component
@Slf4j
public class AutoCloseSchedulerManager {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");
    private final TaskScheduler taskScheduler;
    private final AutoCloseBatchService batchService;

    /**
     * workGroupId → ScheduledFuture 핸들. cancel 용으로 보관.
     */
    private final Map<Long, ScheduledFuture<?>> handles = new ConcurrentHashMap<>();

    @Autowired
    public AutoCloseSchedulerManager(TaskScheduler taskScheduler,
                                     AutoCloseBatchService batchService) {
        this.taskScheduler = taskScheduler;
        this.batchService = batchService;
    }

    /*
     * 근무그룹에 대해 cron 등록.
     * 기존 등록이 있으면 먼저 해제 후 재등록 (그룹 시간 변경 시 호출).
     * 삭제(soft delete) 된 그룹은 unregister 만 수행.
     */
    public synchronized void register(WorkGroup wg) {
        if (wg == null) return;

        Long workGroupId = wg.getWorkGroupId();

        // 1. 삭제된 그룹은 해제만
        if (wg.getGroupDeleteAt() != null) {
            unregister(workGroupId);
            return;
        }

        // 2. 기존 등록 해제 (중복 방지)
        unregister(workGroupId);

        // 3. cron 계산 + 등록
        if (wg.getGroupStartTime() == null) {
            log.warn("[AutoCloseScheduler] groupStartTime null — 등록 스킵. workGroupId={}", workGroupId);
            return;
        }

        String cron = toCronExpression(wg.getGroupStartTime());
        CronTrigger trigger = new CronTrigger(cron, ZONE_SEOUL);

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> batchService.autoCloseForWorkGroup(workGroupId),
                trigger);

        if (future != null) {
            handles.put(workGroupId, future);
            log.info("[AutoCloseScheduler] 등록 — workGroupId={}, groupName={}, startTime={}, cron={}",
                    workGroupId, wg.getGroupName(), wg.getGroupStartTime(), cron);
        } else {
            log.warn("[AutoCloseScheduler] schedule() null 반환 — workGroupId={}, cron={}",
                    workGroupId, cron);
        }
    }

    /*
     * 근무그룹 cron 해제.
     * 삭제/비활성화/시각 변경 재등록 시 호출.
     */
    public synchronized void unregister(Long workGroupId) {
        if (workGroupId == null) return;
        ScheduledFuture<?> prev = handles.remove(workGroupId);
        if (prev != null) {
            prev.cancel(false);  // 실행 중인 태스크는 완료까지 보장 (배치 도중 인터럽트 방지)
            log.info("[AutoCloseScheduler] 해제 — workGroupId={}", workGroupId);
        }
    }

    /*
     * 근무그룹 시작시각 -2h 를 cron 표현식으로 변환.
     */
    static String toCronExpression(LocalTime startTime) {
        LocalTime fireAt = startTime.minusHours(2);
        return String.format("0 %d %d * * *", fireAt.getMinute(), fireAt.getHour());
    }

    /**
     * 현재 등록된 workGroup 수 — 디버그/테스트용
     */
    public int activeCount() {
        return handles.size();
    }
}