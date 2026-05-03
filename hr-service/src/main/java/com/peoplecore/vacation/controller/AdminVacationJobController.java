package com.peoplecore.vacation.controller;

import com.peoplecore.auth.RoleRequired;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* 휴가 도메인 운영 잡 수동 트리거 컨트롤러 */
/* 정기 fire 외 즉시 실행 시나리오: 장애 복구 / 정책 변경 즉시 반영 */
/* Quartz triggerJob 호출 → 멀티노드 한 대만 실행 보장 + 백그라운드 워커 스레드 처리 */
/* 권한: HR_SUPER_ADMIN 단독 (운영 잡 수동 트리거는 최고 권한자만) */
@Slf4j
@RestController
@RequestMapping("/admin/vacations")
public class AdminVacationJobController {

    /* JOB_GROUP — Vacation*SchedulerConfig 의 JOB_GROUP 상수와 일치해야 함 */
    private static final String JOB_GROUP = "vacation";

    private final Scheduler quartzScheduler;

    @Autowired
    public AdminVacationJobController(Scheduler quartzScheduler) {
        this.quartzScheduler = quartzScheduler;
    }

    @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping("/monthly-accrual/run")
    public ResponseEntity<Void> runMonthlyAccrual(@RequestHeader("X-User-Id") String userId) {
        return triggerOrFail(JobKey.jobKey("monthly-accrual", JOB_GROUP), userId);
    }

    @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping("/annual-transition/run")
    public ResponseEntity<Void> runAnnualTransition(@RequestHeader("X-User-Id") String userId) {
        return triggerOrFail(JobKey.jobKey("annual-transition", JOB_GROUP), userId);
    }

    @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping("/annual-grant/run")
    public ResponseEntity<Void> runAnnualGrant(@RequestHeader("X-User-Id") String userId) {
        return triggerOrFail(JobKey.jobKey("annual-grant", JOB_GROUP), userId);
    }

    @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping("/promotion-notice/run")
    public ResponseEntity<Void> runPromotionNotice(@RequestHeader("X-User-Id") String userId) {
        return triggerOrFail(JobKey.jobKey("promotion-notice", JOB_GROUP), userId);
    }

    @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping("/balance-expiry/run")
    public ResponseEntity<Void> runBalanceExpiry(@RequestHeader("X-User-Id") String userId) {
        return triggerOrFail(JobKey.jobKey("balance-expiry", JOB_GROUP), userId);
    }

    @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping("/menstrual-monthly-grant/run")
    public ResponseEntity<Void> runMenstrualMonthlyGrant(@RequestHeader("X-User-Id") String userId) {
        return triggerOrFail(JobKey.jobKey("menstrual-monthly-grant", JOB_GROUP), userId);
    }

    /* Quartz Job 즉시 트리거 — 응답은 즉시(202), 실행은 워커 스레드에서 */
    private ResponseEntity<Void> triggerOrFail(JobKey jobKey, String userId) {
        try {
            quartzScheduler.triggerJob(jobKey);
            log.info("[AdminJob] {} 수동 트리거 - userId={}", jobKey, userId);
            return ResponseEntity.accepted().build();
        } catch (SchedulerException e) {
            log.error("[AdminJob] {} 트리거 실패 - userId={}", jobKey, userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
