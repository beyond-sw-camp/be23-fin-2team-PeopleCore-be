package com.peoplecore.attendance.scheduler;

import com.peoplecore.attendance.batch.AutoCloseJobConfig;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.LocalDate;
import java.time.ZoneId;

/*
 * Quartz 가 fire 시각에 호출하는 자동마감 잡 진입점.
 *
 * 동작:
 *  - JobDataMap 의 (workGroupId, companyId) 추출 → JobLauncher.run(autoCloseJob) 위임
 *  - 실제 처리는 AutoCloseJobConfig 의 2-Step (자동마감 → 결근) 가 담당
 *
 * JobInstance 식별자:
 *  - (companyId, workGroupId, targetDate) UNIQUE — 같은 WorkGroup 같은 날 재실행 자동 차단
 *  - companyId 는 BatchFailureListener 의 Discord 회사 라벨 생성용
 *
 * 의존성 주입:
 *  - Quartz 가 매 fire 마다 새 인스턴스 instantiate (빈 생성자 사용)
 *  - 생성자 주입 불가 — 필드 @Autowired 표준
 *
 * 예외 처리:
 *  - JobInstanceAlreadyCompleteException → INFO 로그 흡수 (운영자 수동 재실행 정상 흐름)
 *  - 그 외 예외 → ERROR 로그 + JobExecutionException 변환 throw → JobFailureNotifier 가 Discord 알림
 *  - refireImmediately=false (misfire DO_NOTHING 일관)
 */
@Slf4j
public class AutoCloseJob implements Job {

    /* JobDataMap key — AutoCloseSchedulerManager.register 와 일치해야 함 */
    private static final String KEY_WORK_GROUP_ID = "workGroupId";
    private static final String KEY_COMPANY_ID = "companyId";

    /* KST 고정 - 서버 로컬 타임존과 무관하게 어제(today-1) 일관 */
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier(AutoCloseJobConfig.JOB_NAME)
    private org.springframework.batch.core.Job autoCloseJob;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long workGroupId = context.getMergedJobDataMap().getLong(KEY_WORK_GROUP_ID);
        String companyId = context.getMergedJobDataMap().getString(KEY_COMPANY_ID);
        LocalDate targetDate = LocalDate.now(ZONE_SEOUL).minusDays(1);

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("companyId", companyId)
                    .addLong("workGroupId", workGroupId)
                    .addString("targetDate", targetDate.toString())
                    .toJobParameters();
            jobLauncher.run(autoCloseJob, params);
        } catch (JobInstanceAlreadyCompleteException e) {
            log.info("[AutoCloseJob] 이미 완료 - workGroupId={}, date={}", workGroupId, targetDate);
        } catch (Exception e) {
            log.error("[AutoCloseJob] Batch 실행 실패 - workGroupId={}, date={}, err={}",
                    workGroupId, targetDate, e.getMessage(), e);
            throw new JobExecutionException(e, false);  // refireImmediately X (misfire DO_NOTHING 일관)
        }
    }
}
