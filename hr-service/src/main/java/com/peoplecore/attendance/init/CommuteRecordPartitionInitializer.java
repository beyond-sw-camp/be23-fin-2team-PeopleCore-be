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

/*
 * commute_record 테이블의 파티션 + 복합 PK + UNIQUE 제약 초기화기.
 *
 * 배경 (리팩토링 인수인계):
 *  - 두 테이블은 월별 RANGE COLUMNS 파티션이 필요.
 *  - Hibernate 6 @IdClass + @GeneratedValue(IDENTITY) 구조가 MySQL "auto_increment 는
 *    key 의 첫 컬럼이어야 한다" 제약과 충돌 → CREATE TABLE 자체가 실패.
 *  - 해결: JPA 엔티티는 단일 PK(Long) 로만 매핑, DB 레벨 복합 PK + 파티션은 이 클래스가 ALTER 로 재정의.
 *  - 비즈니스 유일성(company_id, emp_id, date) 은 엔티티 @UniqueConstraint 로 보장 (본 클래스는 2차 방어).
 *
 * run() 순서 (순서 중요):
 *  1) 각 테이블에 UNIQUE 제약 보장
 *     → PK 를 교체해도 (company_id, emp_id, date) 유일성이 끊기지 않도록 먼저 수행.
 *  2) 각 테이블에 대해 PK 복합 재정의 → PARTITION BY RANGE COLUMNS.
 *     PK 재정의 이유: MySQL 은 파티션 키가 PK/UK 에 반드시 포함되어야 함.
 *     엔티티의 단일 PK(id) 만으로는 파티션 키를 포함하지 못함.
 */
@Slf4j
@Component
@Order(1)
public class CommuteRecordPartitionInitializer implements ApplicationRunner {

    /** 생성할 월 개수 (START_MONTH 기준으로 앞쪽 N개월) */
    private static final int MONTHS_TO_CREATE = 24;

    /** 파티션 시작 기준 월 (과거 데이터 커버 범위 제한) */
    private static final YearMonth START_MONTH = YearMonth.of(2026, 1);

    /*
     * 파티션 적용 대상.
     *  - tableName    : 테이블명
     *  - partitionKey : 파티션 키 컬럼 (날짜)
     *  - idColumn     : AUTO_INCREMENT PK 컬럼 (복합 PK 첫 컬럼으로 유지해야 함)
     *  - uniqueKeyName: UNIQUE 제약 이름
     *  - uniqueKeyCols: UNIQUE 컬럼 목록 (쉼표 구분, 파티션 키 포함 필수)
     */
    private static final List<TablePartition> TARGETS = List.of(
            new TablePartition(
                    "commute_record", "work_date", "com_rec_id",
                    "uk_commute_company_emp_date",
                    "company_id, emp_id, work_date")
    );

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CommuteRecordPartitionInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 1) UNIQUE 제약 먼저 보장 — PK 교체 전에 유일성을 DB 가 지키도록
        for (TablePartition target : TARGETS) {
            ensureUniqueKey(target);
        }
        // 2) 테이블별 PK 복합 재정의 + 파티션 적용
        for (TablePartition target : TARGETS) {
            applyPartitioningIfAbsent(target);
        }
    }

    /*
     * 파티션이 없으면
     *  (a) PK 를 (idColumn, partitionKey) 복합으로 재정의 후
     *  (b) PARTITION BY RANGE COLUMNS DDL 실행.
     * 파티션이 이미 있으면 두 스텝 모두 스킵 (idempotent)
     */
    private void applyPartitioningIfAbsent(TablePartition t) {
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

        // (a) PK 복합 재정의: 단일 PK(id) → 복합 PK(id, 파티션키)
        //     MySQL 제약: 파티션 키는 PK/UK 에 반드시 포함되어야 함.
        String alterPk = String.format(
                "ALTER TABLE %s DROP PRIMARY KEY, ADD PRIMARY KEY (%s, %s)",
                t.tableName, t.idColumn, t.partitionKey);
        log.info("{} PK 복합 재정의 DDL 실행: PK({}, {})",
                t.tableName, t.idColumn, t.partitionKey);
        jdbcTemplate.execute(alterPk);

        // (b) PARTITION BY RANGE COLUMNS — 24개월 + pmax
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

    /*
     * (company_id, emp_id, date) UNIQUE 제약이 없으면 추가.
     * 역할:
     *  - 동시 요청/중복 체크인 race condition 차단 (주 방어는 엔티티 @UniqueConstraint,
     *    본 메서드는 Hibernate 가 누락한 경우 대비 2차 방어).
     * 파티션 테이블 제약:
     *  - MySQL 은 UNIQUE 인덱스가 파티션 키를 포함해야 함 → 모든 대상 UK 는 날짜 컬럼 포함.
     */
    private void ensureUniqueKey(TablePartition t) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                        "WHERE TABLE_SCHEMA = DATABASE() " +
                        "  AND TABLE_NAME = ? " +
                        "  AND INDEX_NAME = ? " +
                        "  AND NON_UNIQUE = 0",
                Integer.class, t.tableName, t.uniqueKeyName);

        if (cnt != null && cnt > 0) {
            log.info("{} UNIQUE 제약 {} 이미 존재 - 스킵", t.tableName, t.uniqueKeyName);
            return;
        }

        String ddl = "ALTER TABLE " + t.tableName + " " +
                "ADD CONSTRAINT " + t.uniqueKeyName + " " +
                "UNIQUE (" + t.uniqueKeyCols + ")";

        log.info("{} UNIQUE 제약 생성 DDL 실행: {}", t.tableName, t.uniqueKeyName);
        jdbcTemplate.execute(ddl);
        log.info("{} UNIQUE 제약 생성 완료", t.tableName);
    }

    /** 파티션 대상 테이블 메타 */
    private record TablePartition(
            String tableName,
            String partitionKey,
            String idColumn,
            String uniqueKeyName,
            String uniqueKeyCols
    ) {}
}
