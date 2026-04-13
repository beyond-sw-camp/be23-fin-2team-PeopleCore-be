package com.peoplecore.attendance.init;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/* commute_record 월별 파티션 초기화기

    두 테이블에 range columns 파티션 적용

 * 기준 월부터 N개월치 파티션 + pmax 생성
 * ddl-auto가 파티션 ddl을 생성하지 못하는 한계를 우회하는 용도  */
@Slf4j
@Component
@SuppressWarnings({"SqlSourceToSinkFlow", "SqlNoDataSourceInspection", "SpellCheckingInspection"})
@Order(1)
public class CommuteRecordPartitionInitializer implements ApplicationRunner {

    /* 생성할 월 개수 (현재 월 기준 앞쪽 N개월) */
    private static final int MONTHS_TO_CREATE = 24;


    /*파티션 시작 기준 월(너무 과거 데이터까지는 커버할 필요 X) */
    private static final YearMonth START_MONTH = YearMonth.of(2026, 1);

    /* commute_record 에 걸어야 하는 UNIQUE 제약 이름 */
    private static final String COMMUTE_UNIQUE_KEY = "uk_commute_company_emp_date";

    /*
     * 파티션을 적용할 (테이블명, 파티션 키 컬럼명) 목록
     */
    private static final List<TablePartition> TARGETS = List.of(
            new TablePartition("commute_record", "work_date"),
            new TablePartition("attendance", "atten_work_date")
    );

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CommuteRecordPartitionInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 1) 파티션 적용 (기존 로직)
        for (TablePartition target : TARGETS) {
            applyIfAbsent(target);
        }
        // 2) commute_record UNIQUE 제약 보장 (B-1)
        ensureCommuteUniqueKey();
    }

    /* 테이블별로 파티션 적용(없을 때만 )*/
    private void applyIfAbsent(TablePartition t) {
        Integer partitionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.PARTITIONS " +
                        "WHERE TABLE_SCHEMA = DATABASE() " +
                        "  AND TABLE_NAME = ? " +
                        "  AND PARTITION_NAME IS NOT NULL",
                Integer.class, t.tableName);

        if (partitionCount != null && partitionCount > 0) {
            log.info("{} 파티션 이미 존재 {}개 - 스킵", t.tableName, partitionCount);
            return;
        }

        String partitions = IntStream.range(0, MONTHS_TO_CREATE).mapToObj(i -> {
            YearMonth ym = START_MONTH.plusMonths(i);
            LocalDate nextMonthFirst = ym.plusMonths(1).atDay(1);
            return String.format("PARTITION p%s VALUES LESS THAN ('%s')",
                    ym.format(DateTimeFormatter.ofPattern("yyyyMM")),
                    nextMonthFirst);
        }).collect(Collectors.joining(",\n  "));

        String ddl = "ALTER TABLE " + t.tableName + "\n" +
                "PARTITION BY RANGE COLUMNS(" + t.partitionKey + ") (\n  " +
                partitions + ",\n  " +
                "PARTITION pmax VALUES LESS THAN (MAXVALUE)\n)";


        log.info("{} 파티션 생성 DDL 실행", t.tableName);
        jdbcTemplate.execute(ddl);
        log.info("{} 파티션 생성 완료 ({}개월 + pmax)", t.tableName, MONTHS_TO_CREATE);
    }

    /* comute_record
    * 목적 : 체크인 버튼 연타/동시 요청시 같은날 중복 레코드 생성 방지
    * 동작 : 인덱스 존재 여부 조회, 이미 있으면 스킵, 없을 때만 테이블 생성 */
    private void ensureCommuteUniqueKey() {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                        "WHERE TABLE_SCHEMA = DATABASE() " +
                        "  AND TABLE_NAME = 'commute_record' " +
                        "  AND INDEX_NAME = ? " +
                        "  AND NON_UNIQUE = 0",
                Integer.class, COMMUTE_UNIQUE_KEY);

        if (cnt != null && cnt > 0) {
            log.info("commute_record UNIQUE 제약 {} 이미 존재 - 스킵", COMMUTE_UNIQUE_KEY);
            return;
        }

        String ddl = "ALTER TABLE commute_record " +
                "ADD CONSTRAINT " + COMMUTE_UNIQUE_KEY + " " +
                "UNIQUE (company_id, emp_id, work_date)";

        log.info("commute_record UNIQUE 제약 생성 DDL 실행: {}", COMMUTE_UNIQUE_KEY);
        jdbcTemplate.execute(ddl);
        log.info("commute_record UNIQUE 제약 생성 완료");
    }


    /*파티션 대상 테이블 메타 */
    private record TablePartition(String tableName, String partitionKey) {
    }


}
