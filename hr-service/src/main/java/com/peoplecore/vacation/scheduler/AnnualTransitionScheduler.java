package com.peoplecore.vacation.scheduler;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.vacation.batch.AnnualTransitionJobConfig;
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

/* 월차→연차 전환 본체 — 회사 순회 + 회사별 Spring Batch JobLauncher 위임 */
/* 정기 fire = AnnualTransitionJob (Quartz) → run() 호출 */
/* 수동 트리거 = 관리 API → run() 호출 */
/* JobInstance(companyId, targetDate) UNIQUE — 같은 회사/같은 날 재실행 자동 차단 */
/* 사원 단위 전환 로직은 AnnualTransitionJobConfig (Step + Reader/Writer) 가 담당 */
@Component
@Slf4j
public class AnnualTransitionScheduler {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final CompanyRepository companyRepository;
    private final VacationPolicyRepository vacationPolicyRepository;
    private final VacationTypeRepository vacationTypeRepository;
    private final JobLauncher jobLauncher;
    private final Job annualTransitionJob;

    @Autowired
    public AnnualTransitionScheduler(CompanyRepository companyRepository,
                                     VacationPolicyRepository vacationPolicyRepository,
                                     VacationTypeRepository vacationTypeRepository,
                                     JobLauncher jobLauncher,
                                     @Qualifier(AnnualTransitionJobConfig.JOB_NAME) Job annualTransitionJob) {
        this.companyRepository = companyRepository;
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.jobLauncher = jobLauncher;
        this.annualTransitionJob = annualTransitionJob;
    }

    /* 정기/수동 공용 진입점 */
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        log.info("[AnnualTransition] 시작 - date={}", today);

        List<Company> activeCompanies = companyRepository.findByCompanyStatus(CompanyStatus.ACTIVE);
        int launched = 0;
        for (Company company : activeCompanies) {
            if (launchForCompany(company.getCompanyId(), today)) {
                launched++;
            }
        }
        log.info("[AnnualTransition] 완료 - date={}, launched={}/{}",
                today, launched, activeCompanies.size());
    }

    /* 회사 단위 JobLauncher 호출 - 정책/유형 누락 시 JobInstance 생성도 skip */
    private boolean launchForCompany(UUID companyId, LocalDate today) {
        boolean hasPolicy = vacationPolicyRepository.findByCompanyIdFetchRules(companyId).isPresent();
        if (!hasPolicy) {
            log.warn("[AnnualTransition] 정책 없음 - companyId={}", companyId);
            return false;
        }
        boolean hasMonthly = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_MONTHLY).isPresent();
        boolean hasAnnual = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_ANNUAL).isPresent();
        if (!hasMonthly || !hasAnnual) {
            log.warn("[AnnualTransition] 시스템 유형 누락 - companyId={}, monthly={}, annual={}",
                    companyId, hasMonthly, hasAnnual);
            return false;
        }

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("companyId", companyId.toString())
                    .addString("targetDate", today.toString())
                    .toJobParameters();
            jobLauncher.run(annualTransitionJob, params);
            return true;
        } catch (JobInstanceAlreadyCompleteException e) {
            log.info("[AnnualTransition] 이미 완료 - companyId={}, date={}", companyId, today);
            return false;
        } catch (Exception e) {
            log.error("[AnnualTransition] Batch 실행 실패 - companyId={}, err={}",
                    companyId, e.getMessage(), e);
            return false;
        }
    }
}
