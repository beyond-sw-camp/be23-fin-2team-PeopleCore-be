package com.peoplecore.vacation.scheduler;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.vacation.batch.MenstrualMonthlyGrantJobConfig;
import com.peoplecore.vacation.entity.VacationType;
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

/* 생리휴가 월별 부여 본체 — 회사 순회 + 회사별 Spring Batch JobLauncher 위임 */
/* 정기 fire = MenstrualMonthlyGrantJob (Quartz) → run() 호출 */
/* 수동 트리거 = 관리 API → run() 호출 */
/* JobInstance(companyId, targetDate) UNIQUE — 같은 회사/같은 날 재실행 자동 차단 */
/* 사원 단위 적립 로직은 MenstrualMonthlyGrantJobConfig (Step + Reader/Writer) 가 담당 */
@Component
@Slf4j
public class MenstrualMonthlyGrantScheduler {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final CompanyRepository companyRepository;
    private final VacationTypeRepository vacationTypeRepository;
    private final JobLauncher jobLauncher;
    private final Job menstrualMonthlyGrantJob;

    @Autowired
    public MenstrualMonthlyGrantScheduler(CompanyRepository companyRepository,
                                          VacationTypeRepository vacationTypeRepository,
                                          JobLauncher jobLauncher,
                                          @Qualifier(MenstrualMonthlyGrantJobConfig.JOB_NAME) Job menstrualMonthlyGrantJob) {
        this.companyRepository = companyRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.jobLauncher = jobLauncher;
        this.menstrualMonthlyGrantJob = menstrualMonthlyGrantJob;
    }

    /* 정기/수동 공용 진입점 */
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        log.info("[MenstrualGrant] 시작 - date={}", today);

        List<Company> activeCompanies = companyRepository.findByCompanyStatus(CompanyStatus.ACTIVE);
        int launched = 0;
        for (Company company : activeCompanies) {
            if (launchForCompany(company.getCompanyId(), today)) {
                launched++;
            }
        }
        log.info("[MenstrualGrant] 완료 - date={}, launched={}/{}",
                today, launched, activeCompanies.size());
    }

    /* 회사 단위 JobLauncher 호출 - MENSTRUAL 유형 누락/비활성 시 JobInstance 생성도 skip */
    private boolean launchForCompany(UUID companyId, LocalDate today) {
        VacationType menstrualType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, "MENSTRUAL")
                .orElse(null);
        if (menstrualType == null) {
            log.warn("[MenstrualGrant] MENSTRUAL 유형 없음 - companyId={} (initDefault 재실행 필요)", companyId);
            return false;
        }
        if (!Boolean.TRUE.equals(menstrualType.getIsActive())) {
            log.info("[MenstrualGrant] MENSTRUAL 유형 비활성 - companyId={}, skip", companyId);
            return false;
        }

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("companyId", companyId.toString())
                    .addString("targetDate", today.toString())
                    .toJobParameters();
            jobLauncher.run(menstrualMonthlyGrantJob, params);
            return true;
        } catch (JobInstanceAlreadyCompleteException e) {
            log.info("[MenstrualGrant] 이미 완료 - companyId={}, date={}", companyId, today);
            return false;
        } catch (Exception e) {
            log.error("[MenstrualGrant] Batch 실행 실패 - companyId={}, err={}",
                    companyId, e.getMessage(), e);
            return false;
        }
    }
}
