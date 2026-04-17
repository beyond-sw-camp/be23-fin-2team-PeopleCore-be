package com.peoplecore.vacation.scheduler;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationBalanceQueryRepository;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import com.peoplecore.vacation.service.PromotionNoticeService;
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

/* 연차 촉진 통지 스케줄러 - 매일 자정 */
/* 정책 isPromotionActive=true 회사만 처리. 1차/2차 각각 설정 있는 경우 실행 */
/* 1차: expires_at = today + firstNoticeMonthsBefore / 2차: 동일 공식 + 잔여 > 0 */
@Component
@Slf4j
public class PromotionNoticeScheduler {

    private static final Duration LOCK_TTL = Duration.ofMinutes(10);
    private static final String LOCK_KEY_PREFIX = "promotion-notice";
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final StringRedisTemplate redisTemplate;
    private final CompanyRepository companyRepository;
    private final VacationPolicyRepository vacationPolicyRepository;
    private final VacationTypeRepository vacationTypeRepository;
    private final VacationBalanceQueryRepository vacationBalanceQueryRepository;
    private final PromotionNoticeService promotionNoticeService;

    @Autowired
    public PromotionNoticeScheduler(StringRedisTemplate redisTemplate,
                                    CompanyRepository companyRepository,
                                    VacationPolicyRepository vacationPolicyRepository,
                                    VacationTypeRepository vacationTypeRepository,
                                    VacationBalanceQueryRepository vacationBalanceQueryRepository,
                                    PromotionNoticeService promotionNoticeService) {
        this.redisTemplate = redisTemplate;
        this.companyRepository = companyRepository;
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.vacationBalanceQueryRepository = vacationBalanceQueryRepository;
        this.promotionNoticeService = promotionNoticeService;
    }

    /* 매일 00:00 KST - 분산 락 후 활성 회사 순회 */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        String lockKey = LOCK_KEY_PREFIX + ":" + today;

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[PromotionNotice] 다른 인스턴스 진행 중 - skip. date={}", today);
            return;
        }
        log.info("[PromotionNotice] 시작 - date={}", today);
        try {
            List<Company> activeCompanies = companyRepository.findByCompanyStatus(CompanyStatus.ACTIVE);
            for (Company company : activeCompanies) {
                try {
                    processCompany(company, today);
                } catch (Exception e) {
                    log.error("[PromotionNotice] 회사 처리 실패 - companyId={}, err={}",
                            company.getCompanyId(), e.getMessage(), e);
                }
            }
            log.info("[PromotionNotice] 완료 - date={}", today);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /* 회사 단위 - 정책/유형 조회 후 1차/2차 분기 */
    private void processCompany(Company company, LocalDate today) {
        UUID companyId = company.getCompanyId();

        VacationPolicy policy = vacationPolicyRepository.findByCompanyId(companyId).orElse(null);
        if (policy == null || !Boolean.TRUE.equals(policy.getIsPromotionActive())) return;

        VacationType annualType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_ANNUAL).orElse(null);
        if (annualType == null) {
            log.warn("[PromotionNotice] ANNUAL 유형 없음 - companyId={}", companyId);
            return;
        }

        /* 1차 통지 - firstNoticeMonthsBefore 설정된 경우 */
        if (policy.getFirstNoticeMonthsBefore() != null) {
            LocalDate targetExpiresAt = today.plusMonths(policy.getFirstNoticeMonthsBefore());
            List<VacationBalance> targets = vacationBalanceQueryRepository
                    .findByCompanyAndTypeAndExpiresAt(companyId, annualType.getTypeId(), targetExpiresAt);
            dispatchFirst(targets);
        }

        /* 2차 통지 - secondNoticeMonthsBefore 설정된 경우 + 잔여 > 0 사원만 */
        if (policy.getSecondNoticeMonthsBefore() != null) {
            LocalDate targetExpiresAt = today.plusMonths(policy.getSecondNoticeMonthsBefore());
            List<VacationBalance> targets = vacationBalanceQueryRepository
                    .findRemainingByCompanyAndTypeAndExpiresAt(companyId, annualType.getTypeId(), targetExpiresAt);
            dispatchSecond(targets);
        }
    }

    /* 1차 통지 발송 - balance 단위 try-catch 격리 */
    private void dispatchFirst(List<VacationBalance> targets) {
        if (targets.isEmpty()) return;
        log.info("[PromotionNotice-FIRST] 대상={}건", targets.size());
        for (VacationBalance balance : targets) {
            try {
                promotionNoticeService.sendFirstNotice(balance);
            } catch (Exception e) {
                log.error("[PromotionNotice-FIRST] 발송 실패 - balanceId={}, err={}",
                        balance.getBalanceId(), e.getMessage(), e);
            }
        }
    }

    /* 2차 통지 발송 */
    private void dispatchSecond(List<VacationBalance> targets) {
        if (targets.isEmpty()) return;
        log.info("[PromotionNotice-SECOND] 대상={}건", targets.size());
        for (VacationBalance balance : targets) {
            try {
                promotionNoticeService.sendSecondNotice(balance);
            } catch (Exception e) {
                log.error("[PromotionNotice-SECOND] 발송 실패 - balanceId={}, err={}",
                        balance.getBalanceId(), e.getMessage(), e);
            }
        }
    }
}