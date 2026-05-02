package com.peoplecore.attendance.scheduler;

import com.peoplecore.attendance.service.AutoCloseBatchService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/*
 * Quartz 가 fire 시각에 호출하는 자동마감 잡 진입점.
 *
 * 동작:
 *  - JobDataMap 의 workGroupId 를 Long 으로 추출 → AutoCloseBatchService.autoCloseForWorkGroup 위임
 *  - 잡 본체 트랜잭션은 AutoCloseBatchService 의 @Transactional 가 관리 (여기는 무트랜잭션)
 *  - 잡 실행 중 예외는 여기서 흡수 + 로깅. JobExecutionException 던지지 않음
 *    (misfire DO_NOTHING 정책과 일관 — 즉시 refire 시 자동마감 중복 처리 위험)
 *
 * 의존성 주입:
 *  - Quartz 가 매 fire 마다 새 인스턴스 instantiate (빈 생성자 사용)
 *  - Spring Boot 의 AutowireCapableBeanJobFactory 가 필드 @Autowired 자동 주입
 *  - 생성자 주입 불가 — Quartz 가 reflection 으로 빈 생성자 호출하기 때문
 *
 * 예외 발생 시:
 *  - autoCloseForWorkGroup 내부 예외 → @Transactional 롤백 → 여기서 catch 하여 ERROR 로그
 *  - JobExecutionException 안 던짐 — Quartz 가 즉시 refire 시도 못 하게 함 (DO_NOTHING 일관)
 *  - 운영자는 알림(JobListener, 추후 도입)/로그 모니터링으로 인지 후 수동 트리거로 복구
 */
@Slf4j
public class AutoCloseJob implements Job {

    /* JobDataMap key — AutoCloseSchedulerManager.register 의 키 명과 일치해야 함 */
    private static final String KEY_WORK_GROUP_ID = "workGroupId";

    /* AutoCloseBatchService 위임 — 자동마감 + 결근 처리 본체 */
    @Autowired
    private AutoCloseBatchService batchService;

    @Override
    public void execute(JobExecutionContext context) {
        Long workGroupId = context.getMergedJobDataMap().getLong(KEY_WORK_GROUP_ID);
        try {
            batchService.autoCloseForWorkGroup(workGroupId);
        } catch (Exception e) {
            // misfire DO_NOTHING 정책 일관 — 예외 흡수, refire 트리거 X. 다음 정상 fire 시각 대기.
            // 운영자는 ERROR 로그 또는 알림으로 인지 후 수동 트리거로 복구.
            log.error("[AutoCloseJob] 실행 실패 — workGroupId={}", workGroupId, e);
        }
    }
}
