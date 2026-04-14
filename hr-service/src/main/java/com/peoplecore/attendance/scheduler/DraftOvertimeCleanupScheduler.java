package com.peoplecore.attendance.scheduler;

import com.peoplecore.attendance.entity.OtStatus;
import com.peoplecore.attendance.repository.OvertimeRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * DRAFT OvertimeRequest 자동 정리 스케줄러.
 *  - 사원이 hr 모달 "확인" 만 누르고 결재요청 안 한 orphan 건 제거
 *  - 기준: 생성된 지 2시간 지난 DRAFT
 *  - 매시간 정각 실행
 *
 * @EnableScheduling 이 HrServiceApplication 에 붙어있어야 동작.
 */
@Component
@Slf4j
public class DraftOvertimeCleanupScheduler {

    /** DRAFT 유지 허용 시간 (시간 단위) */
    private static final long DRAFT_RETENTION_HOURS = 2L;

    private final OvertimeRequestRepository overtimeRequestRepository;

    @Autowired
    public DraftOvertimeCleanupScheduler(OvertimeRequestRepository overtimeRequestRepository) {
        this.overtimeRequestRepository = overtimeRequestRepository;
    }

    /** 매시간 정각 DRAFT 정리 */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    @Transactional
    public void cleanupStaleDrafts() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(DRAFT_RETENTION_HOURS);
        long deleted = overtimeRequestRepository
                .deleteByOtStatusAndCreatedAtBefore(OtStatus.DRAFT, threshold);
        if (deleted > 0) {
            log.info("[DraftCleanup] DRAFT OvertimeRequest 삭제 - count={}, threshold={}",
                    deleted, threshold);
        }
    }
}
