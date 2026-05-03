package com.peoplecore.vacation.scheduler;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.vacation.batch.BalanceExpiryJobConfig;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/* 만료 Batch 런처 본체 — 회사별 Job 인스턴스 분리 */
/* 정기 fire = BalanceExpiryJob (Quartz) → run() 호출 */
/* 수동 트리거 = 관리 API → run() 호출 */
/* JobInstance 식별자: (companyId, targetDate) 조합 - 같은 회사 같은 날 재실행 dedup, 다른 회사는 독립 */
/* 매일 00:20 KST 전 회사 런칭 - Balance 의 expiresAt 은 연차/월차(정책) + EVENT_BASED + 관리자 수동 지급 모두 포함 */
@Component
@Slf4j
public class BalanceExpiryScheduler {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    /* 회계연도 종료일 - VacationPolicy.FIXED_FISCAL_START="01-01" 고정 → 종료는 12-31 */
    private static final int FISCAL_END_MONTH = 12;
    private static final int FISCAL_END_DAY = 31;

    private final JobLauncher jobLauncher;
    private final Job balanceExpiryJob;
    private final CompanyRepository companyRepository;
    private final VacationPolicyRepository vacationPolicyRepository;

    @Autowired
    public BalanceExpiryScheduler(JobLauncher jobLauncher,
                                  @Qualifier(BalanceExpiryJobConfig.JOB_NAME) Job balanceExpiryJob,
                                  CompanyRepository companyRepository,
                                  VacationPolicyRepository vacationPolicyRepository) {
        this.jobLauncher = jobLauncher;
        this.balanceExpiryJob = balanceExpiryJob;
        this.companyRepository = companyRepository;
        this.vacationPolicyRepository = vacationPolicyRepository;
    }

    /* 정기/수동 공용 진입점 */
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        log.info("[BalanceExpiryBatch] 시작 - date={}", today);
        List<Company> activeCompanies = companyRepository.findByCompanyStatus(CompanyStatus.ACTIVE);
        int launched = 0;
        int skipped = 0;
        for (Company company : activeCompanies) {
            UUID companyId = company.getCompanyId();
            try {
                if (launchForCompany(companyId, today)) {
                    launched++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                // 한 회사 실패가 다른 회사 런칭에 전파되지 않도록 격리
                log.error("[BalanceExpiryBatch] 회사 처리 실패 - companyId={}, err={}",
                        companyId, e.getMessage(), e);
            }
        }
        log.info("[BalanceExpiryBatch] 전체 완료 - date={}, total={}, launched={}, skipped={}",
                today, activeCompanies.size(), launched, skipped);
    }

    /* 회사 단위 잡 런칭 판단 + 실행 - 정책별 가드 적용. true = 런칭 성공, false = 정책/상태 가드로 스킵 */
    private boolean launchForCompany(UUID companyId, LocalDate today) {
        VacationPolicy policy = vacationPolicyRepository.findByCompanyId(companyId).orElse(null);
        if (policy == null) {
            log.warn("[BalanceExpiryBatch] 정책 없음 - companyId={} (createDefault 누락?)", companyId);
            return false;
        }

        /* FISCAL 정책: 회계연도 종료일(12-31) 하루만 런칭 - 다른 날은 풀스캔 낭비 */
        /* HIRE 정책: 사원별 expiresAt 분산 → 매일 런칭 */
        if (policy.getPolicyBaseType() == VacationPolicy.PolicyBaseType.FISCAL
                && !(today.getMonthValue() == FISCAL_END_MONTH && today.getDayOfMonth() == FISCAL_END_DAY)) {
            log.debug("[BalanceExpiryBatch] FISCAL 정책 - 회계연도 종료일 아님 skip. companyId={}, date={}",
                    companyId, today);
            return false;
        }

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("companyId", companyId.toString())
                    .addString("targetDate", today.toString())
                    .toJobParameters();
            JobExecution exec = jobLauncher.run(balanceExpiryJob, params);

            StepExecution step = exec.getStepExecutions().stream().findFirst().orElse(null);
            long read = step != null ? step.getReadCount() : 0;
            long write = step != null ? step.getWriteCount() : 0;
            log.info("[BalanceExpiryBatch] 완료 - companyId={}, date={}, status={}, read={}, write={}",
                    companyId, today, exec.getStatus(), read, write);
            return true;
        } catch (JobInstanceAlreadyCompleteException e) {
            // 같은 (companyId, targetDate) 조합은 하루 1회만 실행 - Spring Batch JobInstance UNIQUE 제약
            log.info("[BalanceExpiryBatch] 오늘 이미 완료됨 - skip. companyId={}, date={}", companyId, today);
            return false;
        } catch (Exception e) {
            log.error("[BalanceExpiryBatch] 실행 실패 - companyId={}, date={}, err={}",
                    companyId, today, e.getMessage(), e);
            return false;
        }
    }
}
