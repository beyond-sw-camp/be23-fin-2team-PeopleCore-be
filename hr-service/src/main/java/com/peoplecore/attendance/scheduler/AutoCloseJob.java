package com.peoplecore.attendance.scheduler;

import com.peoplecore.attendance.service.AutoCloseBatchService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/*
 * Quartz 가 fire 시각에 호출하는 자동마감 잡 진입점.
 *
 * 동작:
 *  - JobDataMap 의 workGroupId 를 Long 으로 추출 → AutoCloseBatchService.autoCloseForWorkGroup 위임
 *  - 잡 본체 트랜잭션은 AutoCloseBatchService 의 @Transactional 가 관리 (여기는 무트랜잭션)
 *  - 예외 발생 시 ERROR 로그 + JobExecutionException 변환 throw → JobFailureNotifier 가 Discord 알림
 *
 * 의존성 주입:
 *  - Quartz 가 매 fire 마다 새 인스턴스 instantiate (빈 생성자 사용)
 *  - Spring Boot 의 AutowireCapableBeanJobFactory 가 필드 @Autowired 자동 주입
 *  - 생성자 주입 불가 — Quartz 가 reflection 으로 빈 생성자 호출하기 때문
 *
 * 예외 처리:
 *  - autoCloseForWorkGroup 내부 예외 → @Transactional 롤백 → 여기서 catch
 *  - throw new JobExecutionException(e, false) — refireImmediately=false 라 즉시 refire 안 함 (misfire DO_NOTHING 일관)
 *  - JobListener (JobFailureNotifier) 가 jobException 감지 → Discord webhook 알림
 */
@Slf4j
public class AutoCloseJob implements Job {

    /* JobDataMap key — AutoCloseSchedulerManager.register 의 키 명과 일치해야 함 */
    private static final String KEY_WORK_GROUP_ID = "workGroupId";

    /* AutoCloseBatchService 위임 — 자동마감 + 결근 처리 본체 */
    @Autowired
    private AutoCloseBatchService batchService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long workGroupId = context.getMergedJobDataMap().getLong(KEY_WORK_GROUP_ID);
        try {
            batchService.autoCloseForWorkGroup(workGroupId);
        } catch (Exception e) {
            log.error("[AutoCloseJob] 실행 실패 — workGroupId={}", workGroupId, e);
            throw new JobExecutionException(e, false);  // false = refireImmediately X (misfire DO_NOTHING 일관)
        }
    }
}
