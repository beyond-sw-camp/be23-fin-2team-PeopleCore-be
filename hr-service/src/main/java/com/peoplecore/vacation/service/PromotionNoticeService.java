package com.peoplecore.vacation.service;

import com.peoplecore.alarm.publisher.HrAlarmPublisher;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationPromotionNotice;
import com.peoplecore.vacation.repository.VacationPromotionNoticeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/* 연차 촉진 통지 서비스 - balance 단위 REQUIRES_NEW 격리 */
/* 1차: 사용 계획 제출 요구 / 2차: 회사 지정 사용일자 통보 */
/* 멱등: (company, emp, year, stage) UNIQUE 제약 + existsBy 선체크 */
@Service
@Slf4j
public class PromotionNoticeService {

    private final VacationPromotionNoticeRepository vacationPromotionNoticeRepository;
    private final HrAlarmPublisher hrAlarmPublisher;

    @Autowired
    public PromotionNoticeService(VacationPromotionNoticeRepository vacationPromotionNoticeRepository,
                                  HrAlarmPublisher hrAlarmPublisher) {
        this.vacationPromotionNoticeRepository = vacationPromotionNoticeRepository;
        this.hrAlarmPublisher = hrAlarmPublisher;
    }

    /* 1차 통지 - 잔여 무관 (인수인계 기준). 사용 계획 제출 요구 */
    @Retryable(/*알림 발행 일시 장애 대비재시도 */ maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2), recover = "recoverNotice")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendFirstNotice(VacationBalance balance) {
        sendIfAbsent(balance, VacationPromotionNotice.STAGE_FIRST,
                "연차 사용 촉진 안내 (1차)",
                "%s 만료 예정 연차 %s일에 대한 사용 계획을 제출해 주세요.",
                "PROMOTION_NOTICE_FIRST");
    }

    /* 2차 통지 - 잔여 > 0 전제 (Scheduler 가 이미 필터링). 회사 지정 사용일자 통보 */
    @Retryable(
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2),
            recover = "recoverNotice"
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendSecondNotice(VacationBalance balance) {
        sendIfAbsent(balance, VacationPromotionNotice.STAGE_SECOND,
                "연차 사용 촉진 안내 (2차)",
                "%s 만료 예정 연차 %s일에 대해 사용일자 지정 안내 드립니다.",
                "PROMOTION_NOTICE_SECOND");
    }


    /* 재시도 소진 시 로그 남기고 스케줄러 루프는 계속 진행 */
    @Recover
    public void recoverNotice(Exception e, VacationBalance balance) {
        log.error("[PromotionNotice] 재시도 소진 - empId={}, balanceId={}, cause={}",
                balance.getEmployee().getEmpId(), balance.getBalanceId(), e.getMessage(), e);
    }

    /* 공통 - 멱등 체크 → 알림 발송 → 이력 INSERT */
    private void sendIfAbsent(VacationBalance balance, String stage,
                              String title, String contentTemplate, String alarmRefType) {
        UUID companyId = balance.getCompanyId();
        Long empId = balance.getEmployee().getEmpId();
        Integer year = balance.getBalanceYear();

        if (vacationPromotionNoticeRepository
                .existsByCompanyIdAndEmpIdAndNoticeYearAndNoticeStage(companyId, empId, year, stage)) {
            log.debug("[PromotionNotice] 이미 발송됨 - empId={}, year={}, stage={}", empId, year, stage);
            return;
        }

        BigDecimal targetDays = balance.getAvailableDays();
        if (targetDays.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("[PromotionNotice] 잔여 0 - 통지 스킵. empId={}, stage={}", empId, stage);
            return;
        }

        /* 알림 이벤트 발행 - 실패해도 예외 전파 안 함 (HrAlarmPublisher 내부에서 로그) */
        AlarmEvent alarm = AlarmEvent.builder()
                .companyId(companyId)
                .alarmType("Hr")
                .alarmTitle(title)
                .alarmContent(String.format(contentTemplate, balance.getExpiresAt(), targetDays))
                .alarmLink("/vacation/me")
                .alarmRefType(alarmRefType)
                .alarmRefId(empId)
                .empIds(List.of(empId))
                .build();
        hrAlarmPublisher.publisher(alarm);

        /* 이력 INSERT - UNIQUE 제약으로 마지막 방어선 (동시 통지 경합) */
        VacationPromotionNotice notice = VacationPromotionNotice.STAGE_FIRST.equals(stage)
                ? VacationPromotionNotice.createFirst(balance, targetDays)
                : VacationPromotionNotice.createSecond(balance, targetDays);
        vacationPromotionNoticeRepository.save(notice);

        log.info("[PromotionNotice] 발송 - empId={}, stage={}, year={}, targetDays={}",
                empId, stage, year, targetDays);
    }
}