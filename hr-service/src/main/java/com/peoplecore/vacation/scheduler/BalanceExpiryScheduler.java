package com.peoplecore.vacation.scheduler;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.repository.VacationBalanceQueryRepository;
import com.peoplecore.vacation.service.BalanceExpiryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/* 만료 잡 스케줄러 - 매일 자정. expires_at 도래 balance 처리 */
/* 회사 순회 → findExpiringByCompany 로 대상 조회 → balance 단위 expireRemaining */
@Component
@Slf4j
public class BalanceExpiryScheduler {

    private static final Duration LOCK_TTL = Duration.ofMinutes(10);
    private static final String LOCK_KEY_PREFIX = "balance-expiry";
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final StringRedisTemplate redisTemplate;
    private final CompanyRepository companyRepository;
    private final VacationBalanceQueryRepository vacationBalanceQueryRepository;
    private final BalanceExpiryService balanceExpiryService;

    @Autowired
    public BalanceExpiryScheduler(StringRedisTemplate redisTemplate,
                                  CompanyRepository companyRepository,
                                  VacationBalanceQueryRepository vacationBalanceQueryRepository,
                                  BalanceExpiryService balanceExpiryService) {
        this.redisTemplate = redisTemplate;
        this.companyRepository = companyRepository;
        this.vacationBalanceQueryRepository = vacationBalanceQueryRepository;
        this.balanceExpiryService = balanceExpiryService;
    }

    /* 매일 00:00 KST - 분산 락 후 활성 회사 순회 */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        String lockKey = LOCK_KEY_PREFIX + ":" + today;

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[BalanceExpiry] 다른 인스턴스 진행 중 - skip. date={}", today);
            return;
        }
        log.info("[BalanceExpiry] 시작 - date={}", today);

        List<Company> activeCompanies = companyRepository.findByCompanyStatus(CompanyStatus.ACTIVE);
        int totalExpired = 0;
        for (Company company : activeCompanies) {
            try {
                totalExpired += processCompany(company, today);
            } catch (Exception e) {
                log.error("[BalanceExpiry] 회사 처리 실패 - companyId={}, err={}",
                        company.getCompanyId(), e.getMessage(), e);
            }
        }
        log.info("[BalanceExpiry] 완료 - date={}, totalExpired={}건", today, totalExpired);
    }

    /* 회사 단위 - 만료 대상 balance 조회 후 단건씩 위임. 처리 건수 반환 */
    private int processCompany(Company company, LocalDate today) {
        UUID companyId = company.getCompanyId();
        List<VacationBalance> targets = vacationBalanceQueryRepository.findExpiringByCompany(companyId, today);
        if (targets.isEmpty()) return 0;

        int processed = 0;
        for (VacationBalance balance : targets) {
            try {
                balanceExpiryService.expireBalance(balance);
                processed++;
            } catch (Exception e) {
                log.error("[BalanceExpiry] balance 처리 실패 - balanceId={}, err={}",
                        balance.getBalanceId(), e.getMessage(), e);
            }
        }
        return processed;
    }
}