package com.peoplecore.vacation.scheduler;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.vacation.batch.AnnualGrantFiscalJobConfig;
import com.peoplecore.vacation.batch.AnnualGrantHireJobConfig;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
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

/* 연차 발생 본체 — 회사별 정책(HIRE/FISCAL) 따라 분기, 양쪽 모두 Spring Batch JobLauncher 위임 */
/* 정기 fire = AnnualGrantJob (Quartz) → run() 호출 */
/* 수동 트리거 = 관리 API → run() 호출 */
/* HIRE: 매일 fire, 입사기념일 매치 사원 잡으로 처리 */
/* FISCAL: 매일 fire, 오늘 == 회계연도 시작일일 때만 잡 런칭 */
/* JobInstance(companyId, targetDate) UNIQUE — 같은 회사/같은 날 재실행 자동 차단 (HIRE/FISCAL job_name 이 다르므로 충돌 X) */
@Component
@Slf4j
public class AnnualGrantScheduler {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final CompanyRepository companyRepository;
    private final VacationPolicyRepository vacationPolicyRepository;
    private final VacationTypeRepository vacationTypeRepository;
    private final JobLauncher jobLauncher;
    private final Job annualGrantHireJob;
    private final Job annualGrantFiscalJob;

    @Autowired
    public AnnualGrantScheduler(CompanyRepository companyRepository,
                                VacationPolicyRepository vacationPolicyRepository,
                                VacationTypeRepository vacationTypeRepository,
                                JobLauncher jobLauncher,
                                @Qualifier(AnnualGrantHireJobConfig.JOB_NAME) Job annualGrantHireJob,
                                @Qualifier(AnnualGrantFiscalJobConfig.JOB_NAME) Job annualGrantFiscalJob) {
        this.companyRepository = companyRepository;
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.jobLauncher = jobLauncher;
        this.annualGrantHireJob = annualGrantHireJob;
        this.annualGrantFiscalJob = annualGrantFiscalJob;
    }

    /* 정기/수동 공용 진입점 */
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        log.info("[AnnualGrant] 시작 - date={}", today);

        List<Company> activeCompanies = companyRepository.findByCompanyStatus(CompanyStatus.ACTIVE);
        int processed = 0;
        for (Company company : activeCompanies) {
            try {
                processCompany(company, today);
                processed++;
            } catch (Exception e) {
                log.error("[AnnualGrant] 회사 처리 실패 - companyId={}, err={}",
                        company.getCompanyId(), e.getMessage(), e);
            }
        }
        log.info("[AnnualGrant] 완료 - date={}, companies={}/{}", today, processed, activeCompanies.size());
    }

    /* 회사 단위 - 정책/유형 선검증 후 HIRE/FISCAL 분기 */
    private void processCompany(Company company, LocalDate today) {
        UUID companyId = company.getCompanyId();

        VacationPolicy policy = vacationPolicyRepository.findByCompanyIdFetchRules(companyId).orElse(null);
        if (policy == null) {
            log.warn("[AnnualGrant] 정책 없음 - companyId={} (initDefault 누락?)", companyId);
            return;
        }
        boolean hasAnnualType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_ANNUAL).isPresent();
        if (!hasAnnualType) {
            log.warn("[AnnualGrant] ANNUAL 유형 없음 - companyId={} (initDefault 누락?)", companyId);
            return;
        }

        switch (policy.getPolicyBaseType()) {
            case HIRE -> launchHire(companyId, today);
            case FISCAL -> launchFiscal(companyId, policy, today);
        }
    }

    /* HIRE - JobLauncher 위임. 입사기념일 매치/2년 이상 가드는 잡 내부에서 처리 */
    private void launchHire(UUID companyId, LocalDate today) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("companyId", companyId.toString())
                    .addString("targetDate", today.toString())
                    .toJobParameters();
            jobLauncher.run(annualGrantHireJob, params);
        } catch (JobInstanceAlreadyCompleteException e) {
            log.info("[AnnualGrant-HIRE] 이미 완료 - companyId={}, date={}", companyId, today);
        } catch (Exception e) {
            log.error("[AnnualGrant-HIRE] Batch 실행 실패 - companyId={}, err={}",
                    companyId, e.getMessage(), e);
        }
    }

    /* FISCAL - 오늘 == 회계연도 시작일일 때만 잡 런칭. 회사별 전사원 대상 */
    private void launchFiscal(UUID companyId, VacationPolicy policy, LocalDate today) {
        String fiscalStart = policy.getPolicyFiscalYearStart();
        if (fiscalStart == null || fiscalStart.isBlank()) {
            log.warn("[AnnualGrant-FISCAL] fiscal_year_start null - companyId={}", companyId);
            return;
        }

        String todayMmDd = String.format("%02d-%02d", today.getMonthValue(), today.getDayOfMonth());
        if (!fiscalStart.equals(todayMmDd)) return;

        log.info("[AnnualGrant-FISCAL] Batch 런칭 - companyId={}, fiscalStart={}, today={}",
                companyId, fiscalStart, todayMmDd);
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("companyId", companyId.toString())
                    .addString("targetDate", today.toString())
                    .toJobParameters();
            jobLauncher.run(annualGrantFiscalJob, params);
        } catch (JobInstanceAlreadyCompleteException e) {
            log.info("[AnnualGrant-FISCAL] 이미 완료 - companyId={}, date={}", companyId, today);
        } catch (Exception e) {
            log.error("[AnnualGrant-FISCAL] Batch 실행 실패 - companyId={}, err={}",
                    companyId, e.getMessage(), e);
        }
    }
}
