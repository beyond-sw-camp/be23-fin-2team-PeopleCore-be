package com.peoplecore.attendance.scheduler;

import com.peoplecore.attendance.service.PartitionEnsureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/*
 * 파티션 사전 생성 — 운영자 수동 트리거 빈.
 *
 * 정기 fire 는 PartitionSchedulerConfig 가 Quartz 에 등록한 PartitionEnsureJob 이 담당.
 * 이 클래스는 관리 API 등에서 즉시 실행이 필요할 때 호출하는 진입점만 노출.
 *
 * 호출 예 (추후 관리 컨트롤러):
 *  - POST /admin/partitions/ensure → partitionScheduler.triggerNow()
 *
 * Quartz 잡과 같은 PartitionEnsureService.ensureNextMonthPartition() 를 호출 →
 * 두 경로 동작 일관성 보장. 멱등이라 정기 잡과 시간상 겹쳐도 안전.
 */
@Slf4j
@Component
public class PartitionScheduler {

    private final PartitionEnsureService partitionEnsureService;

    @Autowired
    public PartitionScheduler(PartitionEnsureService partitionEnsureService) {
        this.partitionEnsureService = partitionEnsureService;
    }

    /* 운영자 강제 실행용. 스케줄 대기 없이 즉시 실행. */
    public void triggerNow() {
        log.info("[PartitionScheduler] 수동 트리거 호출");
        partitionEnsureService.ensureNextMonthPartition();
    }
}
