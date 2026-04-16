package com.peoplecore.attendance.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/*
 * 월별 파티션 사전 생성 스케줄러.
 *
 * 배경:
 *  - commute_record / attendance 는 work_date / atten_work_date 기준 월별 RANGE COLUMNS 파티션.
 *  - 다음 달 파티션이 없으면 월 경계 첫 INSERT 시점에 pmax 로 떨어지거나 에러 발생 가능.
 *  - 최초 1회 생성은 CommuteRecordPartitionInitializer 가 담당(START_MONTH 기준 24개월 + pmax).
 *  - 이 스케줄러는 주기적으로 "내달 파티션" 이 있는지 확인하고 없으면 미리 만들어 둔다.
 *
 * 동작:
 *  1) 매월 25일 03:00 (KST) 실행 — 월말 직전 여유 있게
 *  2) 대상 테이블 각각에 대해 내달 파티션(pYYYYMM) 존재 여부 조회
 *  3) 없으면 REORGANIZE PARTITION pmax INTO (p내달, pmax) 로 pmax 앞에 삽입
 *     - 단순 ADD PARTITION 은 pmax(MAXVALUE) 때문에 실패하므로 REORGANIZE 사용
 *  4) 실패 시 log.error 로 남기고 다음 테이블 계속 진행 (한 테이블 실패가 다른 테이블을 막지 않도록)
 *
 * 주의:
 *  - 수동 트리거가 필요하면 triggerNow() 메서드를 직접 호출 (테스트용 엔드포인트에서 사용 가능).
 */
@Slf4j
@Component
public class PartitionScheduler {

    /*
     * 파티션 적용 대상 (테이블명, 파티션 키 컬럼명).
     * CommuteRecordPartitionInitializer 의 TARGETS 와 일치해야 함.
     */
    private static final List<TablePartition> TARGETS = List.of(
            new TablePartition("commute_record", "work_date")
    );

    /* 파티션 이름 포맷: p + yyyyMM (예: p202605) */
    private static final DateTimeFormatter PARTITION_NAME_FMT =
            DateTimeFormatter.ofPattern("yyyyMM");

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public PartitionScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /*
     * 매월 25일 03:00 실행. 내달 파티션이 없으면 생성.
     *
     * cron: 초 분 시 일 월 요일
     *   "0 0 3 25 * *" → 매월 25일 03:00:00
     *
     * 실행 시각을 월말 전(25일)으로 잡은 이유:
     *  - 말일 경계(28/30/31) 이슈 회피
     *  - 실패 시 DBA 가 월말 전 수동 개입할 시간 확보
     */
    @Scheduled(cron = "0 0 3 25 * *", zone = "Asia/Seoul")
    public void ensureNextMonthPartition() {
        YearMonth nextMonth = YearMonth.now().plusMonths(1);
        log.info("[PartitionScheduler] 내달 파티션 점검 시작 target={}", nextMonth);

        for (TablePartition t : TARGETS) {
            try {
                ensurePartitionFor(t, nextMonth);
            } catch (Exception e) {
                // 한 테이블 실패가 다른 테이블 처리를 막지 않도록 개별 try-catch.
                // 알림 연동(슬랙/메일)은 배포 후 확장.
                log.error("[PartitionScheduler] {} 내달 파티션 생성 실패 target={}",
                        t.tableName, nextMonth, e);
            }
        }
        log.info("[PartitionScheduler] 내달 파티션 점검 종료");
    }

    /*
     * 수동 트리거(운영자 강제 실행용). 스케줄 대기 없이 즉시 실행.
     * 추후 관리 API 에서 호출 가능하도록 public 으로 노출.
     */
    public void triggerNow() {
        ensureNextMonthPartition();
    }

    /*
     * 대상 테이블에 특정 월 파티션이 존재하는지 확인하고, 없으면 생성한다.
     *
     * @param t  테이블 + 파티션 키 컬럼
     * @param ym 생성 대상 월 (일반적으로 내달)
     * @throws org.springframework.dao.DataAccessException DDL 실행 실패 시
     */
    private void ensurePartitionFor(TablePartition t, YearMonth ym) {
        String partitionName = "p" + ym.format(PARTITION_NAME_FMT);

        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.PARTITIONS " +
                        "WHERE TABLE_SCHEMA = DATABASE() " +
                        "  AND TABLE_NAME = ? " +
                        "  AND PARTITION_NAME = ?",
                Integer.class, t.tableName, partitionName);

        if (exists != null && exists > 0) {
            log.info("[PartitionScheduler] {}.{} 이미 존재 - 스킵", t.tableName, partitionName);
            return;
        }

       /*  REORGANIZE PARTITION pmax INTO (p내달 VALUES LESS THAN ('내달+1 1일'), pmax VALUES LESS THAN (MAXVALUE))
         - pmax 는 MAXVALUE 라 단순 ADD PARTITION 이 불가하므로 pmax 를 쪼개는 방식을 사용.
         - 파티션 키 타입: DATE / DATETIME 둘 다 'YYYY-MM-DD' 리터럴 허용.*/
        LocalDate nextMonthFirst = ym.plusMonths(1).atDay(1);
        String ddl = "ALTER TABLE " + t.tableName + " " +
                "REORGANIZE PARTITION pmax INTO (" +
                "PARTITION " + partitionName + " VALUES LESS THAN ('" + nextMonthFirst + "'), " +
                "PARTITION pmax VALUES LESS THAN (MAXVALUE))";

        log.info("[PartitionScheduler] {}.{} 생성 DDL 실행", t.tableName, partitionName);
        jdbcTemplate.execute(ddl);
        log.info("[PartitionScheduler] {}.{} 생성 완료", t.tableName, partitionName);
    }

    /** 파티션 대상 테이블 메타 (테이블명 + 파티션 키 컬럼명) */
    private record TablePartition(String tableName, String partitionKey) {
    }
}