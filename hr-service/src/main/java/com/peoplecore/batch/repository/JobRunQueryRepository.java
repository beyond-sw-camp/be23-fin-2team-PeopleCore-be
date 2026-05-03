package com.peoplecore.batch.repository;

import com.peoplecore.batch.dto.JobRunResDto;
import com.peoplecore.batch.dto.StepRunResDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Spring Batch 메타 테이블 (BATCH_JOB_INSTANCE / EXECUTION / EXECUTION_PARAMS / STEP_EXECUTION) 직접 조회.
 *
 * native query 채택 근거:
 *  - JobExplorer 인터페이스는 페이징 / date / companyId 필터가 빈약 → 운영 대시보드 요구 충족 어려움
 *  - BATCH_JOB_* 스키마는 Spring Batch 가 보장 (메이저 버전 변경 시 호환성 검증 필요)
 *
 * N+1 방지: 검색 결과의 jobParameters 는 IN 절 일괄 조회 (fetchParamsBatch).
 */
@Repository
@Slf4j
public class JobRunQueryRepository {

    /* JobExecution 헤더 RowMapper - 검색/단건 공용 */
    private final RowMapper<JobRunResDto> runHeaderMapper = (rs, rowNum) -> {
        Timestamp create = rs.getTimestamp("CREATE_TIME");
        Timestamp end = rs.getTimestamp("END_TIME");
        return JobRunResDto.builder()
                .jobInstanceId(rs.getLong("JOB_INSTANCE_ID"))
                .jobExecutionId(rs.getLong("JOB_EXECUTION_ID"))
                .jobName(rs.getString("JOB_NAME"))
                .status(rs.getString("STATUS"))
                .exitCode(rs.getString("EXIT_CODE"))
                .startTime(create != null ? create.toLocalDateTime() : null)
                .endTime(end != null ? end.toLocalDateTime() : null)
                .build();
    };

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public JobRunQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /*
     * 잡 실행 검색 - 페이징 + 다중 필터 (jobName, companyId, fromDate, toDate, status).
     * companyId 매칭은 BATCH_JOB_EXECUTION_PARAMS 의 'companyId' 키 EXISTS 서브쿼리.
     * Steps 는 응답에서 null (성능 — 단건 조회 시만 fetch).
     * 정렬 고정: create_time DESC.
     */
    public Page<JobRunResDto> search(String jobName, String companyId,
                                     LocalDate fromDate, LocalDate toDate,
                                     String status, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();

        if (jobName != null && !jobName.isBlank()) {
            where.append(" AND i.JOB_NAME = ? ");
            args.add(jobName);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND e.STATUS = ? ");
            args.add(status);
        }
        if (fromDate != null) {
            where.append(" AND e.CREATE_TIME >= ? ");
            args.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            // toDate inclusive → 다음날 00:00 미만으로 변환
            where.append(" AND e.CREATE_TIME < ? ");
            args.add(Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
        }
        if (companyId != null && !companyId.isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM BATCH_JOB_EXECUTION_PARAMS p ")
                    .append(" WHERE p.JOB_EXECUTION_ID = e.JOB_EXECUTION_ID ")
                    .append(" AND p.PARAMETER_NAME = 'companyId' AND p.PARAMETER_VALUE = ?) ");
            args.add(companyId);
        }

        // count 쿼리
        String countSql = "SELECT COUNT(*) FROM BATCH_JOB_INSTANCE i " +
                " JOIN BATCH_JOB_EXECUTION e ON i.JOB_INSTANCE_ID = e.JOB_INSTANCE_ID " + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());

        if (total == null || total == 0L) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // 페이지 데이터 쿼리
        String dataSql = "SELECT i.JOB_INSTANCE_ID, e.JOB_EXECUTION_ID, i.JOB_NAME, " +
                " e.STATUS, e.EXIT_CODE, e.CREATE_TIME, e.END_TIME " +
                " FROM BATCH_JOB_INSTANCE i " +
                " JOIN BATCH_JOB_EXECUTION e ON i.JOB_INSTANCE_ID = e.JOB_INSTANCE_ID " +
                where +
                " ORDER BY e.CREATE_TIME DESC " +
                " LIMIT ? OFFSET ? ";
        List<Object> dataArgs = new ArrayList<>(args);
        dataArgs.add(pageable.getPageSize());
        dataArgs.add(pageable.getOffset());

        List<JobRunResDto> rows = jdbcTemplate.query(dataSql, runHeaderMapper, dataArgs.toArray());

        // jobParameters 일괄 조회 (N+1 방지) - jobExecutionId IN (...)
        Map<Long, Map<String, String>> paramMap = fetchParamsBatch(
                rows.stream().map(JobRunResDto::getJobExecutionId).toList());
        rows.forEach(row -> row.setJobParameters(
                paramMap.getOrDefault(row.getJobExecutionId(), Map.of())));

        return new PageImpl<>(rows, pageable, total);
    }

    /*
     * 단건 조회 - JobExecution 헤더 + JobParameters + Steps 모두 fetch.
     * 미존재 시 null 반환 (Service 가 Optional 로 래핑).
     */
    public JobRunResDto findOne(Long jobExecutionId) {
        String sql = "SELECT i.JOB_INSTANCE_ID, e.JOB_EXECUTION_ID, i.JOB_NAME, " +
                " e.STATUS, e.EXIT_CODE, e.CREATE_TIME, e.END_TIME " +
                " FROM BATCH_JOB_INSTANCE i " +
                " JOIN BATCH_JOB_EXECUTION e ON i.JOB_INSTANCE_ID = e.JOB_INSTANCE_ID " +
                " WHERE e.JOB_EXECUTION_ID = ? ";
        List<JobRunResDto> rows = jdbcTemplate.query(sql, runHeaderMapper, jobExecutionId);
        if (rows.isEmpty()) return null;
        JobRunResDto row = rows.get(0);

        row.setJobParameters(fetchParams(jobExecutionId));
        row.setSteps(fetchSteps(jobExecutionId));
        return row;
    }

    /* 단건 jobParameters 조회 */
    private Map<String, String> fetchParams(Long jobExecutionId) {
        String sql = "SELECT PARAMETER_NAME, PARAMETER_VALUE FROM BATCH_JOB_EXECUTION_PARAMS " +
                " WHERE JOB_EXECUTION_ID = ? ";
        Map<String, String> result = new HashMap<>();
        jdbcTemplate.query(sql, (ResultSet rs) -> {
            result.put(rs.getString("PARAMETER_NAME"), rs.getString("PARAMETER_VALUE"));
        }, jobExecutionId);
        return result;
    }

    /* 다건 jobParameters 일괄 조회 - 검색 결과 N+1 방지 */
    private Map<Long, Map<String, String>> fetchParamsBatch(List<Long> jobExecutionIds) {
        if (jobExecutionIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", jobExecutionIds.stream().map(id -> "?").toList());
        String sql = "SELECT JOB_EXECUTION_ID, PARAMETER_NAME, PARAMETER_VALUE " +
                " FROM BATCH_JOB_EXECUTION_PARAMS " +
                " WHERE JOB_EXECUTION_ID IN (" + placeholders + ") ";
        Map<Long, Map<String, String>> result = new HashMap<>();
        jdbcTemplate.query(sql, (ResultSet rs) -> {
            Long execId = rs.getLong("JOB_EXECUTION_ID");
            result.computeIfAbsent(execId, k -> new HashMap<>())
                    .put(rs.getString("PARAMETER_NAME"), rs.getString("PARAMETER_VALUE"));
        }, jobExecutionIds.toArray());
        return result;
    }

    /* Step 일람 조회 - STEP_EXECUTION_ID 오름차순 (실행 순서 보존) */
    /* Spring Batch 5 부터 SKIP_COUNT 단일 컬럼이 READ/WRITE/PROCESS 3개로 분리됨 → 합산해서 노출 */
    private List<StepRunResDto> fetchSteps(Long jobExecutionId) {
        String sql = "SELECT STEP_EXECUTION_ID, STEP_NAME, STATUS, " +
                " READ_COUNT, WRITE_COUNT, " +
                " (READ_SKIP_COUNT + WRITE_SKIP_COUNT + PROCESS_SKIP_COUNT) AS SKIP_COUNT, " +
                " COMMIT_COUNT, ROLLBACK_COUNT " +
                " FROM BATCH_STEP_EXECUTION " +
                " WHERE JOB_EXECUTION_ID = ? " +
                " ORDER BY STEP_EXECUTION_ID ";
        return jdbcTemplate.query(sql, (ResultSet rs, int n) -> StepRunResDto.builder()
                .stepExecutionId(rs.getLong("STEP_EXECUTION_ID"))
                .stepName(rs.getString("STEP_NAME"))
                .status(rs.getString("STATUS"))
                .readCount(rs.getLong("READ_COUNT"))
                .writeCount(rs.getLong("WRITE_COUNT"))
                .skipCount(rs.getLong("SKIP_COUNT"))
                .commitCount(rs.getLong("COMMIT_COUNT"))
                .rollbackCount(rs.getLong("ROLLBACK_COUNT"))
                .build(), jobExecutionId);
    }
}
