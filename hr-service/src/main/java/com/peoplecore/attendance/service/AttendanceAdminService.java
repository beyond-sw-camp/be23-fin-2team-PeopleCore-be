package com.peoplecore.attendance.service;

import com.peoplecore.attendance.dto.AttendanceAdminRow;
import com.peoplecore.attendance.dto.AttendanceDailyCardRowResDto;
import com.peoplecore.attendance.dto.AttendanceDailyListRowResDto;
import com.peoplecore.attendance.dto.AttendanceDailySummaryResDto;
import com.peoplecore.attendance.dto.AttendanceDeptSummaryResDto;
import com.peoplecore.attendance.dto.AttendanceEmployeeHistoryHeaderDto;
import com.peoplecore.attendance.dto.AttendanceEmployeeHistoryResDto;
import com.peoplecore.attendance.dto.AttendanceEmployeeHistoryRowResDto;
import com.peoplecore.attendance.dto.AttendanceOvertimeRowResDto;
import com.peoplecore.attendance.dto.AttendancePeriodListRowResDto;
import com.peoplecore.attendance.dto.AttendanceWeeklyDailyStatsResDto;
import com.peoplecore.attendance.dto.PagedResDto;
import com.peoplecore.attendance.entity.AttendanceCardType;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.EmploymentFilter;
import com.peoplecore.attendance.entity.OvertimePolicy;
import com.peoplecore.attendance.entity.WeeklyWorkStatus;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.entity.WorkStatus;
import com.peoplecore.attendance.repository.AttendanceAdminQueryRepository;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import com.peoplecore.attendance.repository.OverTimePolicyRepository;
import com.peoplecore.attendance.repository.OvertimeRequestRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.vacation.service.BusinessDayCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/*
 * 근태 현황 관리자 API 서비스 (Phase 1 일자별 탭).
 *
 * 공통 로직:
 *  - OvertimePolicy.otPolicyWeeklyMaxMinute / otPolicyWarningMinute 를 그대로 Judge 에 전달 (이미 분 단위)
 *  - 정책이 없는 회사는 기본값 3120 분(52h) / 2700 분(45h) 적용 (엔티티 @Builder.Default 와 동일)
 *  - fetchAll 결과 각 Row 에 judge 를 적용해 카드 리스트를 계산
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class AttendanceAdminService {

    /* OvertimePolicy 미존재 회사용 기본 주간 최대 근무 분 (52h = 3120) */
    private static final int DEFAULT_WEEKLY_MAX_MINUTE = 3120;

    /* OvertimePolicy 미존재 회사용 기본 경고 기준 분 (45h = 2700) */
    private static final int DEFAULT_WEEKLY_WARNING_MINUTE = 2700;

    private final AttendanceAdminQueryRepository queryRepository;
    private final OverTimePolicyRepository overtimePolicyRepository;
    private final AttendanceStatusJudge judge;
    private final HistoricalDayJudge historicalDayJudge;
    private final EmployeeRepository employeeRepository;
    private final CommuteRecordRepository commuteRecordRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final CardDetailFormatter cardDetailFormatter;
    private final BusinessDayCalculator businessDayCalculator;

    @Autowired
    public AttendanceAdminService(AttendanceAdminQueryRepository queryRepository,
                                  OverTimePolicyRepository overtimePolicyRepository,
                                  AttendanceStatusJudge judge,
                                  HistoricalDayJudge historicalDayJudge,
                                  EmployeeRepository employeeRepository,
                                  CommuteRecordRepository commuteRecordRepository,
                                  OvertimeRequestRepository overtimeRequestRepository,
                                  CardDetailFormatter cardDetailFormatter,
                                  BusinessDayCalculator businessDayCalculator) {
        this.queryRepository = queryRepository;
        this.overtimePolicyRepository = overtimePolicyRepository;
        this.judge = judge;
        this.historicalDayJudge = historicalDayJudge;
        this.employeeRepository = employeeRepository;
        this.commuteRecordRepository = commuteRecordRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.cardDetailFormatter = cardDetailFormatter;
        this.businessDayCalculator = businessDayCalculator;
    }

    /**
     * 일자별 상단 카드 카운트 요약.
     */
    public AttendanceDailySummaryResDto getSummary(UUID companyId, LocalDate date, EmploymentFilter filter) {
        EmploymentFilter effectiveFilter = (filter != null) ? filter : EmploymentFilter.ALL;
        int weeklyMaxMinutes = resolveWeeklyMaxMinute(companyId);
        boolean isHoliday = isCompanyHoliday(companyId, date); // Judge 결근 가드용

        List<AttendanceAdminRow> rows = queryRepository.fetchAll(companyId, date, effectiveFilter);

        // 모든 카드 타입을 0 으로 초기화 후 카운트 누적
        Map<AttendanceCardType, Integer> counts = new EnumMap<>(AttendanceCardType.class);
        for (AttendanceCardType t : AttendanceCardType.values()) {
            counts.put(t, 0);
        }
        for (AttendanceAdminRow r : rows) {
            List<AttendanceCardType> cards = judge.judge(r, date, weeklyMaxMinutes, isHoliday);
            for (AttendanceCardType c : cards) {
                counts.merge(c, 1, Integer::sum);
            }
        }

        log.debug("[getSummary] companyId={}, date={}, filter={}, rows={}, counts={}",
                companyId, date, effectiveFilter, rows.size(), counts);

        return AttendanceDailySummaryResDto.builder()
                .date(date)
                .counts(counts)
                .build();
    }

    /**
     * 일자별 사원 테이블 (페이지네이션).
     */
    public PagedResDto<AttendanceDailyListRowResDto> getList(UUID companyId, LocalDate date,
                                                             EmploymentFilter filter,
                                                             Long deptId, Long workGroupId,
                                                             List<AttendanceCardType> statuses,
                                                             String keyword,
                                                             int page, int size) {
        // 1. null 필터는 ALL(재직+휴직) 로 보정
        EmploymentFilter effectiveFilter = (filter != null) ? filter : EmploymentFilter.ALL;
        // 2. 회사 정책 조회 후 주간 최대 분 계산
        int weeklyMaxMinutes = resolveWeeklyMaxMinute(companyId);
        boolean isHoliday = isCompanyHoliday(companyId, date); // Judge 결근 가드용

        // 3. Repository 에 SQL 필터 위임 → Row 리스트 획득 (이미 휴가/OT/주간분 병합 상태)
        List<AttendanceAdminRow> rows = queryRepository.fetchAll(
                companyId, date, effectiveFilter, deptId, workGroupId, keyword);

        // 4. Row 를 DTO 로 변환하면서 판정 수행 (한 번만 순회)
        List<AttendanceDailyListRowResDto> mapped = new ArrayList<>(rows.size());
        for (AttendanceAdminRow r : rows) {
            // 4-a. 이 사원의 카드 리스트 계산 (중복 허용 List<CardType>)
            List<AttendanceCardType> cards = judge.judge(r, date, weeklyMaxMinutes, isHoliday);
            // 4-b. 응답 DTO 조립해서 추가
            mapped.add(toListRow(r, cards));
        }

        // 5. statuses 가 지정됐으면 EnumSet 으로 변환 (contains 빠르게)
        Set<AttendanceCardType> required =
                (statuses != null && !statuses.isEmpty()) ? EnumSet.copyOf(statuses) : null;
        // 6. 메모리 필터: 요청 statuses 중 하나라도 포함하면 통과
        List<AttendanceDailyListRowResDto> filtered = (required == null)
                ? mapped
                : mapped.stream()
                .filter(row -> row.getAttendanceStatuses().stream().anyMatch(required::contains))
                .toList();

        // 7. 공통 페이지네이션 유틸에 위임 (empId ASC 기본 정렬)
        PagedResDto<AttendanceDailyListRowResDto> result = paginate(filtered, page, size,
                Comparator.comparing(AttendanceDailyListRowResDto::getEmpId));

        log.debug("[getList] companyId={}, date={}, rowsBeforeFilter={}, afterStatusFilter={}, page={}, size={}",
                companyId, date, rows.size(), filtered.size(), page, result.getSize());
        return result;
    }

    /*
     * 카드 드릴다운.
     * cardType 은 필수. employmentFilter 는 생략 시 ALL.
     * List 와 달리 dept/workGroup/keyword 필터는 받지 않음 (대시보드 UX 상 불필요).
     */
    public PagedResDto<AttendanceDailyCardRowResDto> getCard(UUID companyId, LocalDate date,
                                                             AttendanceCardType cardType,
                                                             EmploymentFilter filter,
                                                             int page, int size) {
        // 1. null 필터는 ALL 보정
        EmploymentFilter effectiveFilter = (filter != null) ? filter : EmploymentFilter.ALL;
        // 2. 주간 최대 분 준비 — formatDetail 내부에서 /60 으로 시간 표기
        int weeklyMaxMinutes = resolveWeeklyMaxMinute(companyId);
        boolean isHoliday = isCompanyHoliday(companyId, date); // Judge 결근 가드용

        // 3. 회사 + 재직필터 기준 전체 Row 조회 (부서/검색어 필터 없음)
        List<AttendanceAdminRow> rows = queryRepository.fetchAll(companyId, date, effectiveFilter);

        // 4. 판정 후 해당 cardType 포함 사원만 카드 응답 DTO 로 변환
        List<AttendanceDailyCardRowResDto> hit = new ArrayList<>();
        for (AttendanceAdminRow r : rows) {
            // 4-a. 카드 리스트 계산
            List<AttendanceCardType> cards = judge.judge(r, date, weeklyMaxMinutes, isHoliday);
            // 4-b. 요청 카드 타입을 가진 사원만 포함 (사원별 한 번만 등장)
            if (cards.contains(cardType)) {
                hit.add(toCardRow(r, cardType, weeklyMaxMinutes));
            }
        }

        // 5. 페이지네이션 (empId ASC)
        PagedResDto<AttendanceDailyCardRowResDto> result = paginate(hit, page, size,
                Comparator.comparing(AttendanceDailyCardRowResDto::getEmpId));

        log.debug("[getCard] companyId={}, date={}, cardType={}, hit={}, page={}, size={}",
                companyId, date, cardType, hit.size(), page, result.getSize());
        return result;
    }

    /*
     * Row + 판정 카드 → List 응답 행.
     * totalWorkMinutes 는 퇴근 완료 전엔 null.
     */
    private AttendanceDailyListRowResDto toListRow(AttendanceAdminRow r, List<AttendanceCardType> cards) {
        // 1. 체크인/체크아웃 모두 존재할 때만 총 근무분 계산
        Long workedMin = (r.getCheckInAt() != null && r.getCheckOutAt() != null)
                ? Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes()
                : null;
        // 2. Builder 로 응답 DTO 조립
        return AttendanceDailyListRowResDto.builder()
                .empId(r.getEmpId())
                .empNum(r.getEmpNum())
                .empName(r.getEmpName())
                .deptName(r.getDeptName())
                .workGroupName(r.getWorkGroupName())
                .checkInAt(r.getCheckInAt())
                .checkOutAt(r.getCheckOutAt())
                .totalWorkMinutes(workedMin)
                .vacationTypeName(r.getVacationTypeName())
                .attendanceStatuses(cards)
                .build();
    }

    /*
     * Row + 드릴다운 대상 카드 타입 → Card 응답 행.
     * weeklyWorkedText 와 detail 은 이 메서드에서 포맷 문자열로 구성.
     */
    private AttendanceDailyCardRowResDto toCardRow(AttendanceAdminRow r, AttendanceCardType cardType,
                                                   int weeklyMaxMinutes) {
        // 1. null 방어 — 주간 분 없으면 0 으로 간주
        long weekMin = (r.getWeekWorkedMinutes() != null) ? r.getWeekWorkedMinutes() : 0L;
        // 2. DTO 조립
        return AttendanceDailyCardRowResDto.builder()
                .empId(r.getEmpId())
                .empNum(r.getEmpNum())
                .empName(r.getEmpName())
                .deptName(r.getDeptName())
                .gradeName(r.getGradeName())
                .weeklyWorkedMinutes(weekMin)
                .weeklyWorkedText(formatHm(weekMin))                     // "Xh Ym"
                .detail(formatDetail(cardType, r, weeklyMaxMinutes))     // 카드별 상세 문구
                .build();
    }

    /* 카드 타입별 detail 텍스트 — CardDetailFormatter 위임 (EnumMap 상태 패턴) */
    private String formatDetail(AttendanceCardType cardType, AttendanceAdminRow r, int weeklyMaxMinutes) {
        return cardDetailFormatter.format(cardType, r, weeklyMaxMinutes);
    }

    /* "Xh Ym" 포맷 — CardDetailFormatter 위임 */
    private String formatHm(long minutes) {
        return CardDetailFormatter.formatHm(minutes);
    }

    /**
     * 메모리 페이지네이션 공통 로직.
     */
    private <T> PagedResDto<T> paginate(List<T> items, int page, int size, Comparator<T> comparator) {
        // 1. 원본 변형 방지를 위해 복사 후 정렬
        List<T> sorted = new ArrayList<>(items);
        sorted.sort(comparator);

        // 2. size 방어적 처리 (0 이하 방지)
        int effectiveSize = Math.max(1, size);
        long total = sorted.size();
        // 3. 전체 페이지 수 계산 (ceil)
        int totalPages = (int) Math.ceil(total / (double) effectiveSize);
        // 4. subList 경계 계산 (page 가 범위를 넘어가도 빈 리스트 반환)
        int from = Math.min(page * effectiveSize, (int) total);
        int to = Math.min(from + effectiveSize, (int) total);
        List<T> content = (from >= to) ? List.of() : new ArrayList<>(sorted.subList(from, to));

        // 5. 응답 DTO 조립
        return PagedResDto.<T>builder()
                .content(content)
                .page(page)
                .size(effectiveSize)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }

    /*
     * 회사별 주간 최대 근무 분 조회. 정책 미존재 시 DEFAULT_WEEKLY_MAX_MINUTE.
     */
    private int resolveWeeklyMaxMinute(UUID companyId) {
        return overtimePolicyRepository.findByCompany_CompanyId(companyId)
                .map(OvertimePolicy::getOtPolicyWeeklyMaxMinutes)
                .orElse(DEFAULT_WEEKLY_MAX_MINUTE);
    }

    /*
     * 회사별 주간 경고 기준 분 조회. 정책 미존재 시 DEFAULT_WEEKLY_WARNING_MINUTE.
     */
    private int resolveWeeklyWarningMinute(UUID companyId) {
        return overtimePolicyRepository.findByCompany_CompanyId(companyId)
                .map(OvertimePolicy::getOtPolicyWarningMinutes)
                .orElse(DEFAULT_WEEKLY_WARNING_MINUTE);
    }

    /*
     * 기간별 사원 테이블 (페이지네이션).
     */
    public PagedResDto<AttendancePeriodListRowResDto> getPeriodList(UUID companyId,
                                                                    LocalDate start, LocalDate end,
                                                                    EmploymentFilter filter,
                                                                    Long deptId, Long workGroupId,
                                                                    List<AttendanceCardType> statuses,
                                                                    String keyword,
                                                                    int page, int size) {
        // 1. 범위 검증 — 역순이면 예외
        if (start == null || end == null) {
            throw new IllegalArgumentException("start / end 는 필수입니다.");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end 는 start 이후여야 합니다. start=" + start + ", end=" + end);
        }

        // 2. 기본값 보정 및 정책 분
        EmploymentFilter effectiveFilter = (filter != null) ? filter : EmploymentFilter.ALL;
        int weeklyMaxMinutes = resolveWeeklyMaxMinute(companyId);

        // 3. statuses EnumSet (빈 필터는 null)
        Set<AttendanceCardType> required =
                (statuses != null && !statuses.isEmpty()) ? EnumSet.copyOf(statuses) : null;

        // 4. 일자별로 fetchAll → 판정 → DTO 변환
        List<AttendancePeriodListRowResDto> all = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            boolean isHoliday = isCompanyHoliday(companyId, d); // 매 날짜 휴일 가드
            // 4-a. 단일 날짜 fetch — cr.workDate = :d 로 파티션 프루닝
            List<AttendanceAdminRow> rows = queryRepository.fetchAll(
                    companyId, d, effectiveFilter, deptId, workGroupId, keyword);
            final LocalDate day = d; // effectively-final 제약
            for (AttendanceAdminRow r : rows) {
                // 4-b. 판정
                List<AttendanceCardType> cards = judge.judge(r, day, weeklyMaxMinutes, isHoliday);
                // 4-c. statuses 필터 (교집합 없으면 skip)
                if (required != null && cards.stream().noneMatch(required::contains)) continue;
                // 4-d. 응답 행 조립
                all.add(toPeriodRow(r, day, cards));
            }
        }

        // 5. 정렬 — 날짜 DESC, 사번 ASC
        all.sort(Comparator.comparing(AttendancePeriodListRowResDto::getWorkDate).reversed()
                .thenComparing(AttendancePeriodListRowResDto::getEmpNum,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        // 6. 수동 페이지 슬라이싱
        int effectiveSize = Math.max(1, size);
        int total = all.size();
        int totalPages = (int) Math.ceil(total / (double) effectiveSize);
        int from = Math.min(page * effectiveSize, total);
        int to = Math.min(from + effectiveSize, total);
        List<AttendancePeriodListRowResDto> content = (from >= to) ? List.of()
                : new ArrayList<>(all.subList(from, to));

        log.debug("[getPeriodList] companyId={}, range=[{},{}], total={}, page={}, size={}",
                companyId, start, end, total, page, effectiveSize);

        return PagedResDto.<AttendancePeriodListRowResDto>builder()
                .content(content)
                .page(page)
                .size(effectiveSize)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }

    /*
     * Row + 판정 카드 → 기간별 응답 행.
     * totalWorkMinutes 는 출퇴근 둘 다 있을 때만 계산.
     */
    private AttendancePeriodListRowResDto toPeriodRow(AttendanceAdminRow r, LocalDate date,
                                                      List<AttendanceCardType> cards) {
        // 1. 당일 실근무 분 계산
        Long workedMin = (r.getCheckInAt() != null && r.getCheckOutAt() != null)
                ? Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes()
                : null;
        // 2. Builder 로 DTO 조립
        return AttendancePeriodListRowResDto.builder()
                .workDate(date)
                .empId(r.getEmpId())
                .empNum(r.getEmpNum())
                .empName(r.getEmpName())
                .deptName(r.getDeptName())
                .workGroupName(r.getWorkGroupName())
                .checkInAt(r.getCheckInAt())
                .checkOutAt(r.getCheckOutAt())
                .totalWorkMinutes(workedMin)
                .vacationTypeName(r.getVacationTypeName())
                .attendanceStatuses(cards)
                .build();
    }

    /*
     * 주간현황 — 해당 주 월~일 각 일자별 전사 집계.
     */
    public List<AttendanceWeeklyDailyStatsResDto> getWeeklyStats(UUID companyId, LocalDate weekStart,
                                                                 EmploymentFilter filter) {
        // 1. 입력 검증
        if (weekStart == null) throw new IllegalArgumentException("weekStart 는 필수입니다.");
        // 2. 기본 보정
        EmploymentFilter effectiveFilter = (filter != null) ? filter : EmploymentFilter.ALL;
        int weeklyMaxMinutes = resolveWeeklyMaxMinute(companyId);
        // 3. 해당 주 월요일로 정규화
        LocalDate monday = weekStart.with(DayOfWeek.MONDAY);

        List<AttendanceWeeklyDailyStatsResDto> result = new ArrayList<>(7);
        // 4. 월~일 7일 반복
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            boolean isHoliday = isCompanyHoliday(companyId, day); // 매 날짜 휴일 가드
            // 4-a. 해당일 fetchAll (단일 파티션)
            List<AttendanceAdminRow> rows = queryRepository.fetchAll(companyId, day, effectiveFilter);

            // 4-b. 카운터 누적
            int total = rows.size();
            int normal = 0, late = 0, earlyLeave = 0, absent = 0, onLeave = 0, overtime = 0;
            for (AttendanceAdminRow r : rows) {
                List<AttendanceCardType> cards = judge.judge(r, day, weeklyMaxMinutes, isHoliday);
                boolean hasVac = Boolean.TRUE.equals(r.getHasApprovedVacationToday());

                if (hasVac) onLeave++;
                if (cards.contains(AttendanceCardType.NORMAL)) normal++;
                if (cards.contains(AttendanceCardType.LATE)) late++;
                if (cards.contains(AttendanceCardType.EARLY_LEAVE)) earlyLeave++;

                // 결근: 소정근무일 && 출근기록 없음 && 휴가 아님 && 공휴일 아님 && 근무종료 후
                boolean scheduled = isScheduledWorkDay(r, day);
                boolean noCheckIn = !hasCheckIn(r);
                if (scheduled && noCheckIn && !hasVac && !isHoliday
                        && isWorkdayOver(day, r.getGroupEndTime())) absent++;

                // 초과근무: 승인 OT 존재 OR 미승인 OT 카드
                long approvedOt = (r.getApprovedOtMinutesToday() != null) ? r.getApprovedOtMinutesToday() : 0L;
                boolean hasOt = approvedOt > 0 || cards.contains(AttendanceCardType.UNAPPROVED_OT);
                if (hasOt) overtime++;
            }

            // 4-c. 출근율 — 정상 + 지각
            double attendRate = (total == 0) ? 0.0 : round1((normal + late) * 100.0 / total);

            result.add(AttendanceWeeklyDailyStatsResDto.builder()
                    .date(day).dayOfWeek(day.getDayOfWeek())
                    .totalEmp(total).normal(normal).late(late).earlyLeave(earlyLeave)
                    .absent(absent).onLeave(onLeave).overtime(overtime)
                    .attendRate(attendRate)
                    .build());
        }

        log.debug("[getWeeklyStats] companyId={}, week=[{}~{}]", companyId, monday, monday.plusDays(6));
        return result;
    }

    /*
     * Dept Summary API — 부서별현황 (주간 단위) */

    public List<AttendanceDeptSummaryResDto> getDeptSummary(UUID companyId, LocalDate weekStart,
                                                            EmploymentFilter filter) {
        // 1. 입력 검증
        if (weekStart == null) throw new IllegalArgumentException("weekStart 는 필수입니다.");
        // 2. 기본 보정
        EmploymentFilter effectiveFilter = (filter != null) ? filter : EmploymentFilter.ALL;
        int weeklyMaxMinutes = resolveWeeklyMaxMinute(companyId);
        // 3. 주 범위 계산 (월~일)
        LocalDate monday = weekStart.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        // 4. 부서별 누적 상태 (삽입 순서 보존)
        Map<Long, DeptAggregator> agg = new LinkedHashMap<>();

        // 5. 월~일 7일 순회 → 부서 단위 누적
        for (LocalDate day = monday; !day.isAfter(sunday); day = day.plusDays(1)) {
            boolean isHoliday = isCompanyHoliday(companyId, day); // 매 날짜 휴일 가드
            List<AttendanceAdminRow> rows = queryRepository.fetchAll(companyId, day, effectiveFilter);
            final LocalDate d = day;
            for (AttendanceAdminRow r : rows) {
                DeptAggregator a = agg.computeIfAbsent(r.getDeptId(),
                        k -> new DeptAggregator(r.getDeptId(), r.getDeptName()));
                a.empIds.add(r.getEmpId());

                List<AttendanceCardType> cards = judge.judge(r, d, weeklyMaxMinutes, isHoliday);
                boolean hasVac = Boolean.TRUE.equals(r.getHasApprovedVacationToday());
                boolean scheduled = isScheduledWorkDay(r, d);
                boolean checkedIn = hasCheckIn(r);

                // 휴일/휴가는 부서 분모/결근에서 제외
                if (scheduled && !hasVac && !isHoliday) {
                    a.scheduledEmpDays++;
                    if (checkedIn) a.attendedDays++;
                    if (cards.contains(AttendanceCardType.LATE)) a.lateCount++;
                    // 결근 확정은 근무종료 후에만
                    if (!checkedIn && isWorkdayOver(d, r.getGroupEndTime())) a.absentCount++;
                }

                long dayWorked = computeDayWorkedMinutes(r, d);
                a.workedMinByEmp.merge(r.getEmpId(), dayWorked, Long::sum);
            }
        }

        // 6. 누적 → DTO 변환
        List<AttendanceDeptSummaryResDto> out = new ArrayList<>(agg.size());
        for (DeptAggregator a : agg.values()) {
            int totalEmp = a.empIds.size();

            long totalMin = a.workedMinByEmp.values().stream().mapToLong(Long::longValue).sum();
            double weeklyAvgH = (totalEmp == 0) ? 0.0 : round1(totalMin / 60.0 / totalEmp);

            double overtimeHSum = 0.0;
            int overtimeEmpCount = 0;
            for (long min : a.workedMinByEmp.values()) {
                long over = min - weeklyMaxMinutes;
                if (over > 0) {
                    overtimeHSum += over / 60.0;
                    overtimeEmpCount++;
                }
            }
            double avgOtH = (totalEmp == 0) ? 0.0 : round1(overtimeHSum / totalEmp);

            double attendRate = (a.scheduledEmpDays == 0) ? 0.0
                    : round1(a.attendedDays * 100.0 / a.scheduledEmpDays);
            double lateRate = (a.scheduledEmpDays == 0) ? 0.0
                    : round1(a.lateCount * 100.0 / a.scheduledEmpDays);

            out.add(AttendanceDeptSummaryResDto.builder()
                    .deptId(a.deptId).deptName(a.deptName).totalEmp(totalEmp)
                    .attendRate(attendRate).lateRate(lateRate)
                    .absentCount(a.absentCount)
                    .avgOvertimeHours(avgOtH)
                    .overtimeCount(overtimeEmpCount)
                    .weeklyAvg(weeklyAvgH)
                    .build());
        }

        log.debug("[getDeptSummary] companyId={}, week=[{}~{}], depts={}",
                companyId, monday, sunday, out.size());
        return out;
    }

    /*
     * 부서별현황 집계용 내부 상태 객체 (Service-private).
     */
    private static class DeptAggregator {
        /** 부서 PK */
        final Long deptId;
        /** 부서명 */
        final String deptName;
        /** 주 내 등장한 사원 PK Set (totalEmp 계산용) */
        final Set<Long> empIds = new HashSet<>();
        /** 사원별 주간 근무분 누적 (weeklyAvg / overtime 계산용) */
        final Map<Long, Long> workedMinByEmp = new HashMap<>();
        /** 부서원 소정근무일수 총합 (분모) */
        int scheduledEmpDays = 0;
        /** 출근한 소정근무일수 합 (attendRate 분자) */
        int attendedDays = 0;
        /** 지각 건수 (중복 포함) */
        int lateCount = 0;
        /** 결근 건수 (중복 포함) */
        int absentCount = 0;

        DeptAggregator(Long deptId, String deptName) {
            this.deptId = deptId;
            this.deptName = deptName;
        }
    }

    /*
     * 초과근무 리스트 — 페이지네이션.
     * 상태 판정: WeeklyWorkStatus.of(worked, max, warning) 분 단위 비교.
     */
    public PagedResDto<AttendanceOvertimeRowResDto> getOvertimeList(UUID companyId, LocalDate weekStart,
                                                                    EmploymentFilter filter,
                                                                    String keyword,
                                                                    int page, int size) {
        // 1. 입력 검증
        if (weekStart == null) throw new IllegalArgumentException("weekStart 는 필수입니다.");
        // 2. 정책값 / 기본 보정
        EmploymentFilter effectiveFilter = (filter != null) ? filter : EmploymentFilter.ALL;
        int weeklyMaxMinute = resolveWeeklyMaxMinute(companyId);
        int warningMinute = resolveWeeklyWarningMinute(companyId);
        // 3. 주 범위
        LocalDate monday = weekStart.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        // 4. 사원별 주간 근무분 누적 + 기본정보 스냅샷 (주 내 마지막으로 본 row 사용)
        Map<Long, Long> workedMinByEmp = new HashMap<>();
        Map<Long, AttendanceAdminRow> empSnapshot = new LinkedHashMap<>();

        // 5. 월~일 순회
        for (LocalDate day = monday; !day.isAfter(sunday); day = day.plusDays(1)) {
            List<AttendanceAdminRow> rows = queryRepository.fetchAll(
                    companyId, day, effectiveFilter, null, null, keyword);
            for (AttendanceAdminRow r : rows) {
                empSnapshot.put(r.getEmpId(), r);
                long dayWorked = computeDayWorkedMinutes(r, day);
                workedMinByEmp.merge(r.getEmpId(), dayWorked, Long::sum);
            }
        }

        // 6. DTO 변환
        List<AttendanceOvertimeRowResDto> all = new ArrayList<>(empSnapshot.size());
        for (Map.Entry<Long, AttendanceAdminRow> e : empSnapshot.entrySet()) {
            AttendanceAdminRow r = e.getValue();
            long workedMin = workedMinByEmp.getOrDefault(e.getKey(), 0L);
            long overMin = Math.max(0L, workedMin - weeklyMaxMinute);

            // 상태 판정 — enum 정적 팩토리로 일원화
            WeeklyWorkStatus status = WeeklyWorkStatus.of(workedMin, weeklyMaxMinute, warningMinute);

            all.add(AttendanceOvertimeRowResDto.builder()
                    .empId(r.getEmpId()).empNum(r.getEmpNum()).empName(r.getEmpName())
                    .deptName(r.getDeptName()).gradeName(r.getGradeName())
                    .weeklyWorkMinutes(workedMin)
                    .weeklyMaxMinutes(weeklyMaxMinute)
                    .weeklyWarningMinutes(warningMinute)
                    .overtimeMinutes(overMin)
                    .status(status)
                    .build());
        }

        // 7. 주간근무 DESC 정렬 (많이 일한 사람 먼저)
        all.sort(Comparator.comparing(AttendanceOvertimeRowResDto::getWeeklyWorkMinutes).reversed());

        // 8. 페이지 슬라이싱
        int effectiveSize = Math.max(1, size);
        int total = all.size();
        int totalPages = (int) Math.ceil(total / (double) effectiveSize);
        int from = Math.min(page * effectiveSize, total);
        int to = Math.min(from + effectiveSize, total);
        List<AttendanceOvertimeRowResDto> content = (from >= to) ? List.of()
                : new ArrayList<>(all.subList(from, to));

        log.debug("[getOvertimeList] companyId={}, week=[{}~{}], totalEmp={}, page={}, size={}",
                companyId, monday, sunday, total, page, effectiveSize);

        return PagedResDto.<AttendanceOvertimeRowResDto>builder()
                .content(content)
                .page(page)
                .size(effectiveSize)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }

    /**
     * 당일 실근무 분 계산식.
     * - 휴가일 OR 비근무요일: 승인 OT 분만
     * - 결근(소정근무일이지만 출근기록 없음): 0
     * - 그 외: Max(0, 소정 - 지각 - 조퇴) + 승인 OT
     */
    private long computeDayWorkedMinutes(AttendanceAdminRow r, LocalDate day) {
        boolean hasVac = Boolean.TRUE.equals(r.getHasApprovedVacationToday());
        boolean scheduled = isScheduledWorkDay(r, day);
        boolean checkedIn = hasCheckIn(r);
        long approvedOt = (r.getApprovedOtMinutesToday() != null) ? r.getApprovedOtMinutesToday() : 0L;

        // 1. 휴가/비근무요일: OT 만 반영
        if (hasVac || !scheduled) return approvedOt;
        // 2. 결근: 0 분
        if (!checkedIn) return 0L;
        // 3. 정상: 소정 - 지각 - 조퇴 + OT
        long sched = scheduledMinutes(r);
        long late = extractLateMinutes(r);
        long early = extractEarlyLeaveMinutes(r);
        long base = sched - late - early;
        if (base < 0) base = 0;
        return base + approvedOt;
    }

    /*
     * 근무그룹 소정근무분 = groupEndTime - groupStartTime.
     */
    private long scheduledMinutes(AttendanceAdminRow r) {
        if (r.getGroupStartTime() == null || r.getGroupEndTime() == null) return 0L;
        long total = Duration.between(r.getGroupStartTime(), r.getGroupEndTime()).toMinutes();
        return Math.max(0, total);
    }

    /*
     * 소정근무요일 여부. WorkGroup.groupWorkDay 비트마스크 (월=1, 화=2, 수=4, …, 일=64).
     */
    private boolean isScheduledWorkDay(AttendanceAdminRow r, LocalDate day) {
        if (r.getGroupWorkDay() == null || r.getGroupStartTime() == null) return false;
        int bit = 1 << (day.getDayOfWeek().getValue() - 1);
        return (r.getGroupWorkDay() & bit) != 0;
    }

    /* 체크인 여부 — checkInAt 기반. ABSENT 레코드는 comRecId 있어도 checkInAt null */
    private boolean hasCheckIn(AttendanceAdminRow r) {
        return r.getCheckInAt() != null;
    }

    /* 회사 공휴일 캐시에서 해당 날짜의 휴일 여부 1회 조회 — Judge 결근 가드 + Service 집계 가드 공용 */
    private boolean isCompanyHoliday(UUID companyId, LocalDate date) {
        return businessDayCalculator.getHolidaysInMonth(companyId, YearMonth.from(date))
                .contains(date);
    }

    /* 결근 확정 시점 가드 (Service 집계용 자체 사본).
     * 과거면 true / 오늘이면 groupEndTime 경과 후 / 미래·groupEndTime null 이면 false */
    private boolean isWorkdayOver(LocalDate date, LocalTime groupEndTime) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) return true;
        if (date.isAfter(today)) return false;
        if (groupEndTime == null) return false;
        return LocalTime.now().isAfter(groupEndTime);
    }

    /* 지각 분 = max(0, checkInAt.time - groupStartTime). LATE / LATE_AND_EARLY 일 때만 유효 */
    private long extractLateMinutes(AttendanceAdminRow r) {
        if (r.getCheckInAt() == null || r.getGroupStartTime() == null) return 0L;
        WorkStatus ws = r.getWorkStatus();
        if (ws != WorkStatus.LATE && ws != WorkStatus.LATE_AND_EARLY) return 0L;
        long diff = Duration.between(r.getGroupStartTime(), r.getCheckInAt().toLocalTime()).toMinutes();
        return Math.max(0, diff);
    }

    /* 조퇴 분 = max(0, groupEndTime - checkOutAt.time). EARLY_LEAVE / LATE_AND_EARLY 일 때만 유효 */
    private long extractEarlyLeaveMinutes(AttendanceAdminRow r) {
        if (r.getCheckOutAt() == null || r.getGroupEndTime() == null) return 0L;
        WorkStatus ws = r.getWorkStatus();
        if (ws != WorkStatus.EARLY_LEAVE && ws != WorkStatus.LATE_AND_EARLY) return 0L;
        long diff = Duration.between(r.getCheckOutAt().toLocalTime(), r.getGroupEndTime()).toMinutes();
        return Math.max(0, diff);
    }

    /*
     * double 값을 소수 1자리로 반올림 (DeptSummary 시간 계산용 — 다른 데선 거의 안 씀).
     */
    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /*
     * 사원 일별 근무 현황 조회.
     */
    public AttendanceEmployeeHistoryResDto getEmployeeHistory(UUID companyId, Long empId,
                                                              LocalDate date,
                                                              AttendanceCardType cardType,
                                                              int page, int size) {
        // 1. 사원 조회 — 회사 소속 검증 포함
        Employee employee = employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "해당 회사 소속 사원을 찾을 수 없습니다. empId=" + empId));

        // 2. 조회 기준일 검증 — 미래 금지 + 입사일 이전 금지
        LocalDate hireDate = employee.getEmpHireDate();
        if (date == null) throw new IllegalArgumentException("date 는 필수입니다.");
        if (hireDate != null && date.isBefore(hireDate)) {
            throw new IllegalArgumentException(
                    "조회일은 입사일(" + hireDate + ") 이후여야 합니다. date=" + date);
        }

        // 3. 정책값 로드 — 52시간 현황 계산용 (분 단위)
        int weeklyMaxMinute = resolveWeeklyMaxMinute(companyId);
        int warningMinute = resolveWeeklyWarningMinute(companyId);

        // 4. 주간 범위 (date 가 속한 월~일)
        LocalDate weekStart = date.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        // 5. 주간 실근무 분 합계 — native 쿼리로 single round-trip
        Long weeklyMinRaw = commuteRecordRepository.sumWorkedMinutesBetween(
                companyId, empId, weekStart, weekEnd);
        long weeklyMin = (weeklyMinRaw != null) ? weeklyMinRaw : 0L;

        // 6. 52시간 현황 라벨 — enum 일원화
        WeeklyWorkStatus weeklyStatus = WeeklyWorkStatus.of(weeklyMin, weeklyMaxMinute, warningMinute);

        // 7. 헤더 DTO 조립
        AttendanceEmployeeHistoryHeaderDto header = AttendanceEmployeeHistoryHeaderDto.builder()
                .empId(employee.getEmpId())
                .empNum(employee.getEmpNum())
                .empName(employee.getEmpName())
                .deptName(employee.getDept() != null ? employee.getDept().getDeptName() : null)
                .gradeName(employee.getGrade() != null ? employee.getGrade().getGradeName() : null)
                .weeklyWorkMinutes(weeklyMin)
                .weeklyWorkText(formatHm(weeklyMin))
                .cardType(cardType)
                .weeklyMaxMinutes(weeklyMaxMinute)
                .weeklyWarningMinutes(warningMinute)
                .weeklyStatus(weeklyStatus)
                .build();

        // 8. 일별 근무 페이지 조회 — [hireDate, date] 범위, workDate DESC
        LocalDate from = (hireDate != null) ? hireDate : date.minusYears(10); // 입사일 null 안전장치
        int effectiveSize = Math.max(1, size);
        Pageable pageable = PageRequest.of(Math.max(0, page), effectiveSize);
        Page<CommuteRecord> crPage = commuteRecordRepository
                .findByCompanyIdAndEmployee_EmpIdAndWorkDateBetweenOrderByWorkDateDesc(
                        companyId, empId, from, date, pageable);

        // 9. 페이지 내 workDate 범위만 승인 OT 집계 (최소 스캔)
        Map<LocalDate, Long> approvedOtByDate = loadApprovedOtByDate(empId, crPage.getContent());

        // 10. 사원 근무그룹 스냅샷 — 판정에 사용 (현재 시점의 그룹 기준)
        WorkGroup wg = employee.getWorkGroup();

        // 11. 페이지 행 → DTO 변환
        List<AttendanceEmployeeHistoryRowResDto> rows = new ArrayList<>(crPage.getNumberOfElements());
        for (CommuteRecord c : crPage.getContent()) {
            long approvedOt = approvedOtByDate.getOrDefault(c.getWorkDate(), 0L);
            rows.add(toHistoryRow(c, wg, approvedOt));
        }

        // 12. PagedResDto 조립
        PagedResDto<AttendanceEmployeeHistoryRowResDto> historyPaged =
                PagedResDto.<AttendanceEmployeeHistoryRowResDto>builder()
                        .content(rows)
                        .page(crPage.getNumber())
                        .size(crPage.getSize())
                        .totalElements(crPage.getTotalElements())
                        .totalPages(crPage.getTotalPages())
                        .build();

        log.debug("[getEmployeeHistory] companyId={}, empId={}, date={}, weeklyMin={}, weeklyStatus={}, " +
                "pageTotal={}", companyId, empId, date, weeklyMin, weeklyStatus, crPage.getTotalElements());

        return AttendanceEmployeeHistoryResDto.builder()
                .header(header)
                .history(historyPaged)
                .build();
    }

    /*
     * 페이지 내 레코드 중 workDate 최소~최대 범위에 대해 APPROVED OT 분을 일자별 맵으로 반환.
     */
    private Map<LocalDate, Long> loadApprovedOtByDate(Long empId, List<CommuteRecord> records) {
        if (records.isEmpty()) return Map.of();
        LocalDate minDate = records.get(0).getWorkDate();
        LocalDate maxDate = records.get(0).getWorkDate();
        for (CommuteRecord c : records) {
            if (c.getWorkDate().isBefore(minDate)) minDate = c.getWorkDate();
            if (c.getWorkDate().isAfter(maxDate)) maxDate = c.getWorkDate();
        }
        LocalDateTime fromDt = minDate.atStartOfDay();
        LocalDateTime toDt = maxDate.atTime(LocalTime.MAX);

        List<Object[]> raw = overtimeRequestRepository.sumApprovedOtMinutesByDate(empId, fromDt, toDt);
        Map<LocalDate, Long> out = new HashMap<>(raw.size() * 2);
        for (Object[] row : raw) {
            // row[0] = java.sql.Date / row[1] = Number (BigInteger or Long)
            LocalDate d = ((Date) row[0]).toLocalDate();
            long minutes = ((Number) row[1]).longValue();
            out.put(d, minutes);
        }
        return out;
    }

    /*
     * CommuteRecord + 근무그룹 + 승인 OT → 일별 행 DTO.
     */
    private AttendanceEmployeeHistoryRowResDto toHistoryRow(CommuteRecord c, WorkGroup wg, long approvedOt) {
        LocalDateTime checkInAt = c.getComRecCheckIn();
        LocalDateTime checkOutAt = c.getComRecCheckOut();
        Long workMin = (checkInAt != null && checkOutAt != null)
                ? Duration.between(checkInAt, checkOutAt).toMinutes()
                : null;
        String workText = (workMin != null) ? formatHm(workMin) : null;

        Long otMin = (approvedOt > 0) ? approvedOt : null;
        String otText = (approvedOt > 0) ? formatHm(approvedOt) : null;

        List<AttendanceCardType> cards = historicalDayJudge.judge(c, wg, approvedOt);

        return AttendanceEmployeeHistoryRowResDto.builder()
                .workDate(c.getWorkDate())
                .dayOfWeek(c.getWorkDate().getDayOfWeek())
                .checkInAt(checkInAt)
                .checkOutAt(checkOutAt)
                .workMinutes(workMin)
                .workText(workText)
                .overtimeMinutes(otMin)
                .overtimeText(otText)
                .attendanceStatuses(cards)
                .build();
    }

}
