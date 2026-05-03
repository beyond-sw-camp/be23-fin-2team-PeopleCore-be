package com.peoplecore.vacation.scheduler;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.vacation.batch.PromotionNoticeJobConfig;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/* 연차 촉진 통지 Batch 런처 본체 — 회사별 1차/2차 Job 을 (companyId, targetDate, stage) 키로 런칭 */
/* 정기 fire = PromotionNoticeJob (Quartz) → run() 호출 */
/* 수동 트리거 = 관리 API → run() 호출 */
/* BATCH_JOB_INSTANCE 유니크로 중복 실행 자동 차단 → misfire FIRE_NOW 안전 */
/* 매일 00:15 KST - 발생 잡(00:10) 후 잔여 측정이 정확 */
@Component
@Slf4j
public class PromotionNoticeScheduler {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final CompanyRepository companyRepository;
    private final VacationPolicyRepository vacationPolicyRepository;
    private final JobLauncher jobLauncher;
    private final Job promotionNoticeJob;

    @Autowired
    public PromotionNoticeScheduler(CompanyRepository companyRepository,
                                    VacationPolicyRepository vacationPolicyRepository,
                                    JobLauncher jobLauncher,
                                    @Qualifier(PromotionNoticeJobConfig.JOB_NAME) Job promotionNoticeJob) {
        this.companyRepository = companyRepository;
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.jobLauncher = jobLauncher;
        this.promotionNoticeJob = promotionNoticeJob;
    }

    /* 정기/수동 공용 진입점 */
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        log.info("[PromotionNoticeBatch] 런처 시작 - date={}", today);

        List<Company> activeCompanies = companyRepository.findByCompanyStatus(CompanyStatus.ACTIVE);
        for (Company company : activeCompanies) {
            try {
                launchForCompany(company.getCompanyId(), today);
            } catch (Exception e) {
                log.error("[PromotionNoticeBatch] 회사 런칭 실패 - companyId={}, err={}",
                        company.getCompanyId(), e.getMessage(), e);
            }
        }
        log.info("[PromotionNoticeBatch] 런처 완료 - date={}", today);
    }

    /* 회사별 1차/2차 Job 런칭 - 정책 미설정 stage 는 건너뜀 */
    private void launchForCompany(UUID companyId, LocalDate today) {
        VacationPolicy policy = vacationPolicyRepository.findByCompanyId(companyId).orElse(null);
        if (policy == null || !Boolean.TRUE.equals(policy.getIsPromotionActive())) return;

        if (policy.getFirstNoticeMonthsBefore() != null) {
            launchStage(companyId, today, policy.getFirstNoticeMonthsBefore(),
                    PromotionNoticeJobConfig.STAGE_FIRST);
        }
        if (policy.getSecondNoticeMonthsBefore() != null) {
            launchStage(companyId, today, policy.getSecondNoticeMonthsBefore(),
                    PromotionNoticeJobConfig.STAGE_SECOND);
        }
    }

    /* 단일 stage Job 런칭 - 완료된 JobInstance 면 스킵 로그만 */
    private void launchStage(UUID companyId, LocalDate today, Integer monthsBefore, String stage) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("companyId", companyId.toString())
                    .addString("targetDate", today.toString())
                    .addString("stage", stage)
                    .addLong("monthsBefore", monthsBefore.longValue())
                    .toJobParameters();
            jobLauncher.run(promotionNoticeJob, params);
        } catch (JobInstanceAlreadyCompleteException e) {
            log.info("[PromotionNoticeBatch-{}] 이미 완료 - companyId={}, date={}", stage, companyId, today);
        } catch (Exception e) {
            log.error("[PromotionNoticeBatch-{}] Batch 실행 실패 - companyId={}, err={}",
                    stage, companyId, e.getMessage(), e);
        }
    }
}
