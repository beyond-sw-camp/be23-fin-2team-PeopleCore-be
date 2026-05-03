package com.peoplecore.attendance.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/*
 * 월별 파티션 사전 생성 도메인 서비스.
 *
 * 책임:
 *  - 대상 테이블의 내달 파티션 존재 여부 확인 → 없으면 REORGANIZE PARTITION pmax 로 생성.
 *  - 멱등: COUNT 체크 후 존재 시 skip. 동일 메서드 2회 호출 안전.
 *
 * 호출 경로:
 *  - PartitionEnsureJob (Quartz fire) — 매월 25일 03:00 KST 정기 실행
 *  - AdminAttendanceJobController → quartzScheduler.triggerJob(partition-ensure) — 운영자 수동 트리거
 *  두 경로 모두 같은 PartitionEnsureJob.execute() 거쳐 ensureNextMonthPartition() 호출 — 동작 일관성 보장.
 *
 * 주의:
 *  - 단순 ADD PARTITION 은 pmax(MAXVALUE) 때문에 실패. REORGANIZE 로 pmax 를 쪼개야 함.
 *  - 한 테이블 실패가 다른 테이블을 막지 않도록 개별 try-catch.
 */
@Slf4j
@Service
public class PartitionEnsureService {

    /* 파티션 적용 대상. CommuteRecordPartitionInitializer 의 TARGETS 와 일치해야 함. */
    private static final List<TablePartition> TARGETS = List.of(
            new TablePartition("commute_record", "work_date")
    );

    /* 파티션 이름 포맷: p + yyyyMM (예: p202605) */
    private static final DateTimeFormatter PARTITION_NAME_FMT =
            DateTimeFormatter.ofPattern("yyyyMM");

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public PartitionEnsureService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /*
     * 내달 파티션 점검·생성. 정기 잡과 수동 트리거 공용 진입점.
     * 한 테이블 실패는 ERROR 로그 후 다음 테이블 계속 진행.
     */
    public void ensureNextMonthPartition() {
        YearMonth nextMonth = YearMonth.now().plusMonths(1);
        log.info("[PartitionEnsure] 내달 파티션 점검 시작 target={}", nextMonth);

        for (TablePartition t : TARGETS) {
            try {
                ensurePartitionFor(t, nextMonth);
            } catch (Exception e) {
                log.error("[PartitionEnsure] {} 내달 파티션 생성 실패 target={}",
                        t.tableName, nextMonth, e);
            }
        }
        log.info("[PartitionEnsure] 내달 파티션 점검 종료");
    }

    /*
     * 특정 테이블·월 파티션 존재 확인 후 없으면 생성.
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
            log.info("[PartitionEnsure] {}.{} 이미 존재 - 스킵", t.tableName, partitionName);
            return;
        }

        // pmax 를 쪼개 내달 파티션 삽입. DATE/DATETIME 둘 다 'YYYY-MM-DD' 리터럴 허용.
        LocalDate nextMonthFirst = ym.plusMonths(1).atDay(1);
        String ddl = "ALTER TABLE " + t.tableName + " " +
                "REORGANIZE PARTITION pmax INTO (" +
                "PARTITION " + partitionName + " VALUES LESS THAN ('" + nextMonthFirst + "'), " +
                "PARTITION pmax VALUES LESS THAN (MAXVALUE))";

        log.info("[PartitionEnsure] {}.{} 생성 DDL 실행", t.tableName, partitionName);
        jdbcTemplate.execute(ddl);
        log.info("[PartitionEnsure] {}.{} 생성 완료", t.tableName, partitionName);
    }

    /** 파티션 대상 테이블 메타 (테이블명 + 파티션 키 컬럼명) */
    private record TablePartition(String tableName, String partitionKey) {
    }
}
