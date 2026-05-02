package com.peoplecore.attendance.scheduler;

import com.peoplecore.attendance.service.PartitionEnsureService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/*
 * Quartz 가 fire 시각에 호출하는 파티션 사전 생성 잡 진입점.
 *
 * 동작:
 *  - PartitionEnsureService.ensureNextMonthPartition 위임
 *  - 멱등: 서비스 메서드가 COUNT 체크 후 DDL 발사 — 두 번 fire 돼도 두 번째는 skip
 *  - 잡 실행 중 예외는 여기서 흡수 + 로깅. JobExecutionException 안 던짐
 *
 * 의존성 주입:
 *  - Quartz 가 매 fire 마다 새 인스턴스 instantiate (빈 생성자 사용)
 *  - Spring Boot AutowireCapableBeanJobFactory 가 필드 @Autowired 자동 주입
 *  - 생성자 주입 불가 — Quartz 가 reflection 으로 빈 생성자 호출
 *
 * 멀티 노드 환경:
 *  - QRTZ_LOCKS row lock 이 한 노드만 fire 보장
 *  - 동일 시각 다중 fire 발생해도 ensureNextMonthPartition 멱등 → 데이터 안전
 */
@Slf4j
public class PartitionEnsureJob implements Job {

    @Autowired
    private PartitionEnsureService partitionEnsureService;

    @Override
    public void execute(JobExecutionContext context) {
        try {
            partitionEnsureService.ensureNextMonthPartition();
        } catch (Exception e) {
            // 본 메서드 내부에서도 try-catch 로 흡수하지만 안전망으로 한 번 더.
            // misfire=FIRE_NOW 라도 멱등 보장돼 refire 안전.
            log.error("[PartitionEnsureJob] 실행 실패", e);
        }
    }
}
