package com.peoplecore.attendance.batch;

import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.MyAttendanceQueryRepository;
import com.peoplecore.attendance.repository.WorkGroupRepository;
import com.peoplecore.attendance.service.AutoCloseBatchService;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.batch.BatchFailureListener;
import com.peoplecore.vacation.repository.VacationRequestQueryRepository;
import com.peoplecore.vacation.service.BusinessDayCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/* 자동마감 + 결근 처리 Batch Job - WorkGroup 단위 2-Step (자동마감 → 결근) */
/* JobInstance 식별자: (companyId, workGroupId, targetDate) - 같은 WorkGroup 같은 날 재실행 자동 차단 */
/* Step 분리 근거: 자동마감 = CommuteRecord, 결근 = Employee 도메인 다름 → 단일 Reader 불가 */
/* 가드 위치: 소정근무요일/공휴일/휴가자 제외는 absentReader 가 사전 처리 (Decider 미사용) */
@Configuration
@Slf4j
public class AutoCloseJobConfig {

    /* 청크 단위 - closeOne/markAbsentOne 내부 REQUIRES_NEW. 외부 tx 비어있음 */
    private static final int CHUNK_SIZE = 50;

    /* 허용 skip 상한 - WorkGroup 단위라 대상자 적음. 초과 시 Step FAILED → Discord 빨간색 알림 */
    private static final int SKIP_LIMIT = 20;

    public static final String JOB_NAME = "autoCloseJob";

    /*
     * TODO 고도화 예정 — Multi-thread Step (taskExecutor + throttleLimit) 도입 검토
     *  - autoCloseStep / absentStep 두 청크 모두 병렬 처리 후보
     *  - 전제 1: ListItemReader 는 안전. JdbcCursor 마이그 시 SynchronizedItemStreamReader 검토
     *  - 전제 2: ItemWriter → Service REQUIRES_NEW 격리 (이미 적용)
     *  - 대안: Partitioned Step (WorkGroup 별 파티션 병렬)
     */
    @Bean(JOB_NAME)
    public Job autoCloseJob(JobRepository jobRepository,
                            Step autoCloseStep, Step absentStep,
                            BatchFailureListener batchFailureListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(batchFailureListener)
                .start(autoCloseStep)
                .next(absentStep)
                .build();
    }

    /* === Step 1: 자동마감 === */

    @Bean
    public Step autoCloseStep(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              ListItemReader<CommuteRecord> autoCloseReader,
                              ItemWriter<CommuteRecord> autoCloseWriter) {
        return new StepBuilder("autoCloseStep", jobRepository)
                .<CommuteRecord, CommuteRecord>chunk(CHUNK_SIZE, transactionManager)
                .reader(autoCloseReader)
                .writer(autoCloseWriter)
                .faultTolerant()
                .retry(TransientDataAccessException.class)   // DB 락/데드락/타임아웃 등 일시 장애 자동 복구
                .retryLimit(3)
                .skip(Exception.class)
                .skipLimit(SKIP_LIMIT)
                .build();
    }

    /* WorkGroup 미존재/삭제 시 빈 List → Step 자연 종료 */
    @Bean
    @StepScope
    public ListItemReader<CommuteRecord> autoCloseReader(
            WorkGroupRepository workGroupRepository,
            MyAttendanceQueryRepository myAttendanceQueryRepository,
            @Value("#{jobParameters['companyId']}") String companyIdStr,
            @Value("#{jobParameters['workGroupId']}") Long workGroupId,
            @Value("#{jobParameters['targetDate']}") String targetDateStr) {

        UUID companyId = UUID.fromString(companyIdStr);
        LocalDate targetDate = LocalDate.parse(targetDateStr);

        WorkGroup wg = workGroupRepository.findById(workGroupId).orElse(null);
        if (wg == null || wg.getGroupDeleteAt() != null) {
            log.info("[AutoClose] 근무그룹 미존재/삭제 - workGroupId={}", workGroupId);
            return new ListItemReader<>(List.of());
        }

        List<CommuteRecord> targets = myAttendanceQueryRepository
                .findAutoCloseTargets(companyId, workGroupId, targetDate);
        return new ListItemReader<>(targets);
    }

    /* WorkGroup 의 groupEndTime 으로 closedAt 계산 1회 후 사원당 closeOne 위임 */
    @Bean
    @StepScope
    public ItemWriter<CommuteRecord> autoCloseWriter(
            AutoCloseBatchService autoCloseBatchService,
            WorkGroupRepository workGroupRepository,
            @Value("#{jobParameters['companyId']}") String companyIdStr,
            @Value("#{jobParameters['workGroupId']}") Long workGroupId,
            @Value("#{jobParameters['targetDate']}") String targetDateStr) {

        UUID companyId = UUID.fromString(companyIdStr);
        LocalDate targetDate = LocalDate.parse(targetDateStr);

        WorkGroup wg = workGroupRepository.findById(workGroupId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_GROUP_NOT_FOUND));
        // 강제 마감 시각 = 근무그룹 종료시각. 야간조도 workDate 유지
        LocalDateTime closedAt = targetDate.atTime(wg.getGroupEndTime());

        return chunk -> chunk.getItems().forEach(rec ->
                autoCloseBatchService.closeOne(rec, companyId, closedAt));
    }

    /* === Step 2: 결근 === */

    @Bean
    public Step absentStep(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager,
                           ListItemReader<Employee> absentReader,
                           ItemWriter<Employee> absentWriter) {
        return new StepBuilder("absentStep", jobRepository)
                .<Employee, Employee>chunk(CHUNK_SIZE, transactionManager)
                .reader(absentReader)
                .writer(absentWriter)
                .faultTolerant()
                .retry(TransientDataAccessException.class)   // DB 락/데드락/타임아웃 등 일시 장애 자동 복구
                .retryLimit(3)
                .skip(Exception.class)
                .skipLimit(SKIP_LIMIT)
                .build();
    }

    /* 가드 (소정근무요일/공휴일/휴가자) 미충족 시 빈 List → Step 자연 종료 (Decider 대신) */
    /* 가드 통과 시 absentTargets 조회 + 휴가자 제외 후 ListItemReader 반환 */
    @Bean
    @StepScope
    public ListItemReader<Employee> absentReader(
            WorkGroupRepository workGroupRepository,
            BusinessDayCalculator businessDayCalculator,
            MyAttendanceQueryRepository myAttendanceQueryRepository,
            VacationRequestQueryRepository vacationRequestQueryRepository,
            @Value("#{jobParameters['companyId']}") String companyIdStr,
            @Value("#{jobParameters['workGroupId']}") Long workGroupId,
            @Value("#{jobParameters['targetDate']}") String targetDateStr) {

        UUID companyId = UUID.fromString(companyIdStr);
        LocalDate targetDate = LocalDate.parse(targetDateStr);

        WorkGroup wg = workGroupRepository.findById(workGroupId).orElse(null);
        if (wg == null || wg.getGroupDeleteAt() != null) {
            log.info("[AutoClose] 결근 Step - 근무그룹 미존재/삭제. workGroupId={}", workGroupId);
            return new ListItemReader<>(List.of());
        }

        // 소정근무요일 비트 가드
        int dayBit = 1 << (targetDate.getDayOfWeek().getValue() - 1);
        if ((wg.getGroupWorkDay() & dayBit) == 0) {
            log.debug("[AutoClose] 결근 Step - 비소정근무요일 skip. workGroupId={}, date={}",
                    workGroupId, targetDate);
            return new ListItemReader<>(List.of());
        }

        // 공휴일 가드 - 회사 단위 공휴일 캐시 1회 조회
        Set<LocalDate> monthHolidays = businessDayCalculator.getHolidaysInMonth(
                companyId, YearMonth.from(targetDate));
        if (monthHolidays.contains(targetDate)) {
            log.debug("[AutoClose] 결근 Step - 공휴일 skip. workGroupId={}, date={}",
                    workGroupId, targetDate);
            return new ListItemReader<>(List.of());
        }

        // 결근 후보 조회 + 승인 휴가자 제외 (사전 차집합)
        List<Employee> absentTargets = myAttendanceQueryRepository
                .findAbsentTargets(companyId, workGroupId, targetDate);
        if (absentTargets.isEmpty()) {
            return new ListItemReader<>(List.of());
        }
        Set<Long> onLeaveEmpIds = vacationRequestQueryRepository
                .findOnLeaveEmpIds(companyId, workGroupId, targetDate);

        List<Employee> filtered = absentTargets.stream()
                .filter(emp -> !onLeaveEmpIds.contains(emp.getEmpId()))
                .toList();
        return new ListItemReader<>(filtered);
    }

    /* 사원당 markAbsentOne 위임 */
    @Bean
    @StepScope
    public ItemWriter<Employee> absentWriter(
            AutoCloseBatchService autoCloseBatchService,
            @Value("#{jobParameters['companyId']}") String companyIdStr,
            @Value("#{jobParameters['targetDate']}") String targetDateStr) {

        UUID companyId = UUID.fromString(companyIdStr);
        LocalDate targetDate = LocalDate.parse(targetDateStr);

        return chunk -> chunk.getItems().forEach(emp ->
                autoCloseBatchService.markAbsentOne(emp, companyId, targetDate));
    }
}
