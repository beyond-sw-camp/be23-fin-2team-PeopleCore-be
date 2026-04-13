package com.peoplecore.attendence.init;

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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/* commute_record 월별 파티션 초기화기

 * 기준 월부터 N개월치 파티션 + pmax 생성
 * ddl-auto가 파티션 ddl을 생성하지 못하는 한계를 우회하는 용도  */
@Slf4j
@Component
@Order(1)
public class CommuteRecordPartitionInitializer implements ApplicationRunner {

    /* 생성할 월 개수 (현재 월 기준 앞쪽 N개월) */
    private static final int MONTHS_TO_CREATE = 24;


    /*파티션 시작 기준 월(너무 과거 데이터까지는 커버할 필요 X) */
    private static final YearMonth START_MONTH = YearMonth.of(2026, 1);

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CommuteRecordPartitionInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer partitionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.PARTITIONS " +
                        "WHERE TABLE_SCHEMA = DATABASE() " +
                        "  AND TABLE_NAME = 'commute_record' " +
                        "  AND PARTITION_NAME IS NOT NULL",
                Integer.class);

        if (partitionCount != null && partitionCount > 0) {
            log.info("commute_record 파티션 이미 존재 {} 개 - 스킵", partitionCount);
            return;
        }

        String partitions = IntStream.range(0, MONTHS_TO_CREATE).mapToObj(i -> {
            YearMonth ym = START_MONTH.plusMonths(i);
            LocalDate nextMonthFirst = ym.plusMonths(1).atDay(1);
            return String.format("PARTITION p%s VALUES LESS THAN ('%s')",
                    ym.format(DateTimeFormatter.ofPattern("yyyyMM")),
                    nextMonthFirst);
        }).collect(Collectors.joining(",\n "));

        String ddl = "ALTER TABLE commute_record\n" +
                "PARTITION BY RANGE COLUMNS(com_rec_date) (\n  " +
                partitions + ",\n  " +
                "PARTITION pmax VALUES LESS THAN (MAXVALUE)\n)";

        log.info("commute_record 파티션 생성 DDL 실행 ");
        jdbcTemplate.execute(ddl);
        log.info("commute_record 파티션 생성 완료 ({}개월 + pmax)", MONTHS_TO_CREATE);


    }
}
