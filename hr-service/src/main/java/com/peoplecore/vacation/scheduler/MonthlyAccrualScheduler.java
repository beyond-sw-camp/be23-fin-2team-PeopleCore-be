package com.peoplecore.vacation.scheduler;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.vacation.batch.MonthlyAccrualJobConfig;
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

/* 월차 자동 적립 본체 — 회사 순회 + 회사별 Spring Batch JobLauncher 위임 */
/* 정기 fire = MonthlyAccrualJob (Quartz) → run() 호출 */
/* 수동 트리거 = 관리 API → run() 호출 (장애 복구 시 운영자 직접 실행) */
/* JobInstance(companyId, targetDate) UNIQUE — 같은 회사/같은 날 재실행 자동 차단 */
/* 사원 단위 적립 로직은 MonthlyAccrualJobConfig (Step + Reader/Writer) 가 담당 */
@Component
@Slf4j
public class MonthlyAccrualScheduler {

    /* KST 고정 - 서버 로컬 타임존과 무관하게 정책 시각 준수 */
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final CompanyRepository companyRepository;
    private final VacationTypeRepository vacationTypeRepository;
    private final JobLauncher jobLauncher;
    private final Job monthlyAccrualJob;

    @Autowired
    public MonthlyAccrualScheduler(CompanyRepository companyRepository,
                                   VacationTypeRepository vacationTypeRepository,
                                   JobLauncher jobLauncher,
                                   @Qualifier(MonthlyAccrualJobConfig.JOB_NAME) Job monthlyAccrualJob) {
        this.companyRepository = companyRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.jobLauncher = jobLauncher;
        this.monthlyAccrualJob = monthlyAccrualJob;
    }

    /* 정기/수동 공용 진입점 */
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        log.info("[MonthlyAccrual] 시작 - date={}", today);

        List<Company> activeCompanies = companyRepository.findByCompanyStatus(CompanyStatus.ACTIVE);
        int launched = 0;
        for (Company company : activeCompanies) {
            if (launchForCompany(company.getCompanyId(), today)) {
                launched++;
            }
        }
        log.info("[MonthlyAccrual] 완료 - date={}, launched={}/{}",
                today, launched, activeCompanies.size());
    }

    /* 회사 단위 JobLauncher 호출 - MONTHLY 유형 누락 시 JobInstance 생성도 skip */
    private boolean launchForCompany(UUID companyId, LocalDate today) {
        VacationType monthlyType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_MONTHLY)
                .orElse(null);
        if (monthlyType == null) {
            log.warn("[MonthlyAccrual] MONTHLY 유형 없음 - companyId={} (initDefault 누락?)", companyId);
            return false;
        }

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("companyId", companyId.toString())
                    .addString("targetDate", today.toString())
                    .toJobParameters();
            jobLauncher.run(monthlyAccrualJob, params);
            return true;
        } catch (JobInstanceAlreadyCompleteException e) {
            // 같은 회사/같은 날 이미 완료 — 운영자 수동 재실행 시 정상 흐름
            log.info("[MonthlyAccrual] 이미 완료 - companyId={}, date={}", companyId, today);
            return false;
        } catch (Exception e) {
            log.error("[MonthlyAccrual] Batch 실행 실패 - companyId={}, err={}",
                    companyId, e.getMessage(), e);
            return false;
        }
    }
}
