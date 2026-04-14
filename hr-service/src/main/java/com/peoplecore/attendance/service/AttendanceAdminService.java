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
import com.peoplecore.attendance.entity.CheckInStatus;
import com.peoplecore.attendance.entity.CheckOutStatus;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.EmploymentFilter;
import com.peoplecore.attendance.entity.OvertimePolicy;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.AttendanceAdminQueryRepository;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import com.peoplecore.attendance.repository.OverTimePolicyRepository;
import com.peoplecore.attendance.repository.OvertimeRequestRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
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
import java.time.format.DateTimeFormatter;
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

/**
 * 근태 현황 관리자 API 서비스 (Phase 1 일자별 탭).
 *
 * 공통 로직:
 *  - OvertimePolicy.otPolicyWeeklyMaxHour 를 분 단위로 변환해 Judge 에 전달
 *  - 정책이 없는 회사는 기본값 52h 적용 (엔티티 @Builder.Default 와 동일)
 *  - fetchAll 결과 각 Row 에 judge 를 적용해 카드 리스트를 계산
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class AttendanceAdminService {

    /** OvertimePolicy 미존재 회사용 기본 주간 최대 근무시간 (시간 단위) */
    private static final int DEFAULT_WEEKLY_MAX_HOUR = 52;

    /** OvertimePolicy 미존재 회사용 기본 경고 기준 (시간 단위) — 엔티티 @Builder.Default(45) 와 동일 */
    private static final int DEFAULT_WEEKLY_WARNING_HOUR = 45;

    /** 시각 표시용 HH:mm 포맷터 (LATE/EARLY_LEAVE detail 등에 사용) */
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceAdminQueryRepository queryRepository;
    private final OverTimePolicyRepository overtimePolicyRepository;
    private final AttendanceStatusJudge judge;
    private final EmployeeRepository employeeRepository;
    private final CommuteRecordRepository commuteRecordRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;

    @Autowired
    public AttendanceAdminService(AttendanceAdminQueryRepository queryRepository,
                                  OverTimePolicyRepository overtimePolicyRepository,
                                  AttendanceStatusJudge judge,
                                  EmployeeRepository employeeRepository,
                                  CommuteRecordRepository commuteRecordRepository,
                                  OvertimeRequestRepository overtimeRequestRepository) {
        this.queryRepository = queryRepository;
        this.overtimePolicyRepository = overtimePolicyRepository;
        this.judge = judge;
        this.employeeRepository = employeeRepository;
        this.commuteRecordRepository = commuteRecordRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
    }

    /**
     * 일자별 상단 카드 카운트 요약.
     *
     * @param companyId 회사 PK (X-User-Company 헤더 값)
     * @param date      조회 기준일
     * @param filter    재직상태 필터 (null 이면 ALL 로 처리)
     * @return 카드 타입별 카운트 (10개 타입 전부 포함, 없으면 0)
     */
    public AttendanceDailySummaryResDto getSummary(UUID companyId, LocalDate date, EmploymentFilter filter) {
        EmploymentFilter effectiveFilter = (filter != null) ? filter : EmploymentFilter.ALL;
        int weeklyMaxMinutes = resolveWeeklyMaxMinutes(companyId);

        List<AttendanceAdminRow> rows = queryRepository.fetchAll(companyId, date, effectiveFilter);

        // 모든 카드 타입을 0 으로 초기화 후 카운트 누적
        Map<AttendanceCardType, Integer> counts = new EnumMap<>(AttendanceCardType.class);
        for (AttendanceCardType t : AttendanceCardType.values()) {
            counts.put(t, 0);
        }
        for (AttendanceAdminRow r : rows) {
            List<AttendanceCardType> cards = judge.judge(r, date, weeklyMaxMinutes);
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

    /* =========================================================================
     * List API (A-3) — 사원 테이블. SQL 단계 필터 후 판정 + 메모리 statuses 필터 + 페이지네이션
     * ========================================================================= */

    /**
     * 일자별 사원 테이블 (페이지네이션).
     *
     * 필터 분리:
     *  - deptId / workGroupId / keyword / employmentFilter → SQL 단계에서 적용 (Repository)
     *  - statuses (CardType 배열) → 판정 후 메모리에서 교집합 필터
     */
    public PagedResDto<AttendanceDailyListRowResDto> getList(UUID companyId, LocalDate date,
                                                             EmploymentFilter filter,
                                                             Long deptId, Long workGroupId,
                                                             List<AttendanceCardType> statuses,
                                                             String keyword,
                                                             int page, int size) {
        // 1. null 필터는 ALL(재직+휴직) 로 보정
        EmploymentFilter effectiveFilter = (filter != null) ? filter : EmploymentFilter.ALL;
        // 2. 회사 정책 조회 후 주간 최대 분(minutes) 계산
        int weeklyMaxMinutes = resolveWeeklyMaxMinutes(companyId);

        // 3. Repository 에 SQL 필터 위임 → Row 리스트 획득 (이미 휴가/OT/주간분 병합 상태)
        List<AttendanceAdminRow> rows = queryRepository.fetchAll(
                companyId, date, effectiveFilter, deptId, workGroupId, keyword);

        // 4. Row 를 DTO 로 변환하면서 판정 수행 (한 번만 순회)
        List<AttendanceDailyListRowResDto> mapped = new ArrayList<>(rows.size());
        for (AttendanceAdminRow r : rows) {
            // 4-a. 이 사원의 카드 리스트 계산 (중복 허용 List<CardType>)
            List<AttendanceCardType> cards = judge.judge(r, date, weeklyMaxMinutes);
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

    /* =========================================================================
     * Card Drilldown API (A-4) — 특정 CardType 에 해당하는 사원 목록
     * ========================================================================= */

    /**
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
        // 2. 주간 최대 분과 시간(hour) 두 값 준비 — MAX_HOUR_EXCEED detail 에 시간 단위로 표시
        int weeklyMaxMinutes = resolveWeeklyMaxMinutes(companyId);
        int weeklyMaxHour = weeklyMaxMinutes / 60;

        // 3. 회사 + 재직필터 기준 전체 Row 조회 (부서/검색어 필터 없음)
        List<AttendanceAdminRow> rows = queryRepository.fetchAll(companyId, date, effectiveFilter);

        // 4. 판정 후 해당 cardType 포함 사원만 카드 응답 DTO 로 변환
        List<AttendanceDailyCardRowResDto> hit = new ArrayList<>();
        for (AttendanceAdminRow r : rows) {
            // 4-a. 카드 리스트 계산
            List<AttendanceCardType> cards = judge.judge(r, date, weeklyMaxMinutes);
            // 4-b. 요청 카드 타입을 가진 사원만 포함 (사원별 한 번만 등장)
            if (cards.contains(cardType)) {
                hit.add(toCardRow(r, cardType, weeklyMaxHour));
            }
        }

        // 5. 페이지네이션 (empId ASC)
        PagedResDto<AttendanceDailyCardRowResDto> result = paginate(hit, page, size,
                Comparator.comparing(AttendanceDailyCardRowResDto::getEmpId));

        log.debug("[getCard] companyId={}, date={}, cardType={}, hit={}, page={}, size={}",
                companyId, date, cardType, hit.size(), page, result.getSize());
        return result;
    }

    /* =========================================================================
     * Row → 응답 DTO 변환
     * ========================================================================= */

    /**
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

    /**
     * Row + 드릴다운 대상 카드 타입 → Card 응답 행.
     * weeklyWorkedText 와 detail 은 이 메서드에서 포맷 문자열로 구성.
     */
    private AttendanceDailyCardRowResDto toCardRow(AttendanceAdminRow r, AttendanceCardType cardType,
                                                    int weeklyMaxHour) {
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
                .weeklyWorkedText(formatHm(weekMin))                    // "Xh Ym"
                .detail(formatDetail(cardType, r, weeklyMaxHour))        // 카드별 상세 문구
                .build();
    }

    /* =========================================================================
     * detail 포맷 — 카드 타입별 고정 문구 생성
     * ========================================================================= */

    /**
     * 카드 타입별 detail 텍스트 생성.
     * 프론트 UI 스펙에 맞춘 고정 포맷. 결측 데이터는 안전 기본값으로 대체.
     */
    private String formatDetail(AttendanceCardType cardType, AttendanceAdminRow r, int weeklyMaxHour) {
        // switch expression — 각 분기 return 이 detail 문자열
        return switch (cardType) {

            // 정상: 지각/조퇴 없는 사원. 체크아웃 전에도 "정시 출근 · 정시 퇴근" 으로 표기 (UI 통일성)
            case NORMAL -> "정시 출근 · 정시 퇴근";

            // 종일근무: 체크아웃 여부로 "근무중"/"퇴근" 분기
            case WORKING -> (r.getCheckOutAt() != null) ? "퇴근" : "근무중";

            // 지각: 체크인시각 + (체크인 - groupStartTime) 분
            case LATE -> {
                long lateMin = (r.getCheckInAt() != null && r.getGroupStartTime() != null)
                        ? Duration.between(r.getGroupStartTime(), r.getCheckInAt().toLocalTime()).toMinutes()
                        : 0L;
                // "HH:mm 출근 (N분 지각)" — 음수 방어
                yield String.format("%s 출근 (%d분 지각)",
                        r.getCheckInAt() != null ? r.getCheckInAt().toLocalTime().format(HHMM) : "--:--",
                        Math.max(0, lateMin));
            }

            // 조퇴: 체크아웃시각 + (groupEndTime - 체크아웃) 분
            case EARLY_LEAVE -> {
                long earlyMin = (r.getCheckOutAt() != null && r.getGroupEndTime() != null)
                        ? Duration.between(r.getCheckOutAt().toLocalTime(), r.getGroupEndTime()).toMinutes()
                        : 0L;
                yield String.format("%s 퇴근 (%d분 조퇴)",
                        r.getCheckOutAt() != null ? r.getCheckOutAt().toLocalTime().format(HHMM) : "--:--",
                        Math.max(0, earlyMin));
            }

            // 휴가 중 출근: 휴가유형명 + " 중 출근" (유형명 없으면 "휴가" 기본)
            case VACATION_ATTEND -> {
                String type = (r.getVacationTypeName() != null) ? r.getVacationTypeName() : "휴가";
                yield type + " 중 출근";
            }

            // 출퇴근 누락: 체크인 자체 없음 → "출근 누락", 체크인 있는데 체크아웃 없음 → "퇴근 누락"
            case MISSING_COMMUTE -> (r.getComRecId() == null) ? "출근 누락" : "퇴근 누락";

            // 1일 소정근로 미달: 실근무분 / 소정근로분 ("Xh Ym / Xh Ym 소정")
            case UNDER_MIN_HOUR -> {
                long workedMin = (r.getCheckInAt() != null && r.getCheckOutAt() != null)
                        ? Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes() : 0L;
                long scheduledMin = (r.getGroupStartTime() != null && r.getGroupEndTime() != null)
                        ? Duration.between(r.getGroupStartTime(), r.getGroupEndTime()).toMinutes() : 0L;
                yield String.format("%s / %s 소정", formatHm(workedMin), formatHm(scheduledMin));
            }

            // 근무지 외: IP 가 있으면 IP 노출, 없으면 기본 문구
            case OFFSITE -> (r.getCheckInIp() != null && !r.getCheckInIp().isBlank())
                    ? r.getCheckInIp()
                    : "근무지 외 체크인";

            // 미승인 초과근무: (체크아웃 - groupEndTime) 분을 "Xh Ym 초과 (미승인)" 로 표기
            case UNAPPROVED_OT -> {
                long overMin = (r.getCheckOutAt() != null && r.getGroupEndTime() != null)
                        ? Duration.between(r.getGroupEndTime(), r.getCheckOutAt().toLocalTime()).toMinutes()
                        : 0L;
                yield String.format("%s 초과 (미승인)", formatHm(Math.max(0, overMin)));
            }

            // 주간 최대근무시간 초과: 사원 주간(시간) / 정책(시간) 표기
            case MAX_HOUR_EXCEED -> {
                long weekMin = (r.getWeekWorkedMinutes() != null) ? r.getWeekWorkedMinutes() : 0L;
                yield String.format("%dh / %dh 정책", weekMin / 60, weeklyMaxHour);
            }
        };
    }

    /** 분 단위 값을 "Xh Ym" 문자열로 변환 (음수는 0 으로 간주하지 않고 그대로 표시 — 호출측에서 Math.max 처리). */
    private String formatHm(long minutes) {
        long h = minutes / 60;     // 시간 부분
        long m = minutes % 60;     // 분 부분
        return h + "h " + m + "m"; // 예: "7h 30m"
    }

    /* =========================================================================
     * 공통 유틸
     * ========================================================================= */

    /**
     * 메모리 페이지네이션 공통 로직.
     *
     * @param items      전체 원소
     * @param page       0-based 페이지 번호
     * @param size       페이지 크기 (최소 1 로 보정)
     * @param comparator 정렬 규칙
     * @return PagedResDto
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

    /**
     * 회사별 주간 최대 근무시간(분) 조회. 정책 미존재 시 기본값 52h 사용.
     */
    private int resolveWeeklyMaxMinutes(UUID companyId) {
        int hours = overtimePolicyRepository.findByCompany_CompanyId(companyId)
                .map(OvertimePolicy::getOtPolicyWeeklyMaxHour)
                .orElse(DEFAULT_WEEKLY_MAX_HOUR);
        return hours * 60;
    }

    /**
     * 회사별 주간 경고 기준(시간) 조회. 정책 미존재 시 기본값 45h.
     * OvertimePolicy.otPolicyWarningHour 를 그대로 사용.
     */
    private int resolveWeeklyWarningHour(UUID companyId) {
        return overtimePolicyRepository.findByCompany_CompanyId(companyId)
                .map(OvertimePolicy::getOtPolicyWarningHour)
                .orElse(DEFAULT_WEEKLY_WARNING_HOUR);
    }

    /* =========================================================================
     * Period List API — 기간별 (사원 × 일자) 행 리스트
     *
     * - 일자별 Row 구조 그대로 재사용 (AttendancePeriodListRowResDto 는 workDate 만 추가)
     * - 기존 fetchAll 을 date 마다 재사용 (단일 파티션 프루닝 유지)
     * - statuses 는 판정 후 메모리에서 교집합 필터
     * ========================================================================= */

    /**
     * 기간별 사원 테이블 (페이지네이션).
     *
     * @param companyId      회사 PK
     * @param start          조회 시작일 (포함)
     * @param end            조회 종료일 (포함)
     * @param filter         재직상태 필터 (null → ALL)
     * @param deptId         부서 필터 (nullable)
     * @param workGroupId    근무그룹 필터 (nullable)
     * @param statuses       카드 타입 필터 (nullable/empty 미적용)
     * @param keyword        사번/이름/부서명 부분일치 (nullable/blank 미적용)
     * @param page           0-based 페이지 번호
     * @param size           페이지 크기 (최소 1 보정)
     * @return 기간 내 (사원×일자) 행들의 페이지. 정렬: workDate DESC, empId ASC
     * @throws IllegalArgumentException end < start 인 경우
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

        // 2. 기본값 보정 및 정책 분(min)
        EmploymentFilter effectiveFilter = (filter != null) ? filter : EmploymentFilter.ALL;
        int weeklyMaxMinutes = resolveWeeklyMaxMinutes(companyId);

        // 3. statuses EnumSet (빈 필터는 null)
        Set<AttendanceCardType> required =
                (statuses != null && !statuses.isEmpty()) ? EnumSet.copyOf(statuses) : null;

        // 4. 일자별로 fetchAll → 판정 → DTO 변환
        List<AttendancePeriodListRowResDto> all = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            // 4-a. 단일 날짜 fetch — cr.workDate = :d 로 파티션 프루닝
            List<AttendanceAdminRow> rows = queryRepository.fetchAll(
                    companyId, d, effectiveFilter, deptId, workGroupId, keyword);
            final LocalDate day = d; // effectively-final 제약
            for (AttendanceAdminRow r : rows) {
                // 4-b. 판정
                List<AttendanceCardType> cards = judge.judge(r, day, weeklyMaxMinutes);
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

    /**
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

    /* =========================================================================
     * Weekly Stats API — 주간현황 (월~일 일자별 전사 카운트)
     *
     * 카운트 정의:
     *  - normal     : NORMAL 카드 포함
     *  - late       : LATE 카드 포함
     *  - earlyLeave : EARLY_LEAVE 카드 포함
     *  - onLeave    : hasApprovedVacationToday == true
     *  - absent     : 소정근무일 && comRecId == null && 승인 휴가 없음
     *  - overtime   : approvedOtMinutesToday > 0 || UNAPPROVED_OT
     *  - attendRate : (normal + late) / totalEmp * 100 (1자리 반올림)
     * ========================================================================= */

    /**
     * 주간현황 — 해당 주 월~일 각 일자별 전사 집계.
     *
     * @param companyId  회사 PK
     * @param weekStart  주 시작일 (임의 날짜 가능 → 해당 주 월요일로 자동 정렬)
     * @param filter     재직상태 필터 (null → ALL)
     * @return 월~일 7행 (일자 오름차순)
     */
    public List<AttendanceWeeklyDailyStatsResDto> getWeeklyStats(UUID companyId, LocalDate weekStart,
                                                                  EmploymentFilter filter) {
        // 1. 입력 검증
        if (weekStart == null) throw new IllegalArgumentException("weekStart 는 필수입니다.");
        // 2. 기본 보정
        EmploymentFilter effectiveFilter = (filter != null) ? filter : EmploymentFilter.ALL;
        int weeklyMaxMinutes = resolveWeeklyMaxMinutes(companyId);
        // 3. 해당 주 월요일로 정규화
        LocalDate monday = weekStart.with(DayOfWeek.MONDAY);

        List<AttendanceWeeklyDailyStatsResDto> result = new ArrayList<>(7);
        // 4. 월~일 7일 반복
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            // 4-a. 해당일 fetchAll (단일 파티션)
            List<AttendanceAdminRow> rows = queryRepository.fetchAll(companyId, day, effectiveFilter);

            // 4-b. 카운터 누적
            int total = rows.size();
            int normal = 0, late = 0, earlyLeave = 0, absent = 0, onLeave = 0, overtime = 0;
            for (AttendanceAdminRow r : rows) {
                List<AttendanceCardType> cards = judge.judge(r, day, weeklyMaxMinutes);
                boolean hasVac = Boolean.TRUE.equals(r.getHasApprovedVacationToday());

                if (hasVac) onLeave++;
                if (cards.contains(AttendanceCardType.NORMAL)) normal++;
                if (cards.contains(AttendanceCardType.LATE)) late++;
                if (cards.contains(AttendanceCardType.EARLY_LEAVE)) earlyLeave++;

                // 결근: 소정근무일 && 출근기록 없음 && 휴가 아님
                boolean scheduled = isScheduledWorkDay(r, day);
                boolean noCheckIn = (r.getComRecId() == null);
                if (scheduled && noCheckIn && !hasVac) absent++;

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

    /* =========================================================================
     * Dept Summary API — 부서별현황 (주간 단위)
     *
     * 집계 항목:
     *  - totalEmp        : 주 내 한 번이라도 나타난 사원 수 (중복 제거)
     *  - scheduledEmpDays: 부서원별 소정근무일수 총합 (분모)
     *  - attendRate      : (출근한 소정근무일수) / scheduledEmpDays
     *  - lateRate        : 지각건수 / scheduledEmpDays
     *  - absentCount     : 결근 건수 (중복 포함)
     *  - weeklyAvg       : 부서 사원 주간 근무시간 합 / totalEmp
     *  - overtimeCount   : weekly > weeklyMaxHour 인 사원 수
     *  - avgOvertimeHours: (초과분 합) / totalEmp
     * ========================================================================= */

    /**
     * 부서별현황 — 주간 집계.
     */
    public List<AttendanceDeptSummaryResDto> getDeptSummary(UUID companyId, LocalDate weekStart,
                                                             EmploymentFilter filter) {
        // 1. 입력 검증
        if (weekStart == null) throw new IllegalArgumentException("weekStart 는 필수입니다.");
        // 2. 기본 보정
        EmploymentFilter effectiveFilter = (filter != null) ? filter : EmploymentFilter.ALL;
        int weeklyMaxMinutes = resolveWeeklyMaxMinutes(companyId);
        // 3. 주 범위 계산 (월~일)
        LocalDate monday = weekStart.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        // 4. 부서별 누적 상태 (삽입 순서 보존)
        Map<Long, DeptAggregator> agg = new LinkedHashMap<>();

        // 5. 월~일 7일 순회 → 부서 단위 누적
        for (LocalDate day = monday; !day.isAfter(sunday); day = day.plusDays(1)) {
            List<AttendanceAdminRow> rows = queryRepository.fetchAll(companyId, day, effectiveFilter);
            final LocalDate d = day;
            for (AttendanceAdminRow r : rows) {
                // 5-a. 부서 누적자 조회/생성
                DeptAggregator a = agg.computeIfAbsent(r.getDeptId(),
                        k -> new DeptAggregator(r.getDeptId(), r.getDeptName()));
                a.empIds.add(r.getEmpId());

                // 5-b. 판정 및 상태 플래그
                List<AttendanceCardType> cards = judge.judge(r, d, weeklyMaxMinutes);
                boolean hasVac = Boolean.TRUE.equals(r.getHasApprovedVacationToday());
                boolean scheduled = isScheduledWorkDay(r, d);
                boolean hasCheckIn = (r.getComRecId() != null);

                // 5-c. 소정근무일(휴가 제외) 분모/분자 갱신
                if (scheduled && !hasVac) {
                    a.scheduledEmpDays++;
                    if (hasCheckIn) a.attendedDays++;
                    if (cards.contains(AttendanceCardType.LATE)) a.lateCount++;
                    if (!hasCheckIn) a.absentCount++;
                }

                // 5-d. 사원별 당일 근무분 누적 (주간 합계)
                long dayWorked = computeDayWorkedMinutes(r, d);
                a.workedMinByEmp.merge(r.getEmpId(), dayWorked, Long::sum);
            }
        }

        // 6. 누적 → DTO 변환
        List<AttendanceDeptSummaryResDto> out = new ArrayList<>(agg.size());
        for (DeptAggregator a : agg.values()) {
            int totalEmp = a.empIds.size();

            // 6-a. 주간 평균 근무시간 (h)
            long totalMin = a.workedMinByEmp.values().stream().mapToLong(Long::longValue).sum();
            double weeklyAvgH = (totalEmp == 0) ? 0.0 : round1(totalMin / 60.0 / totalEmp);

            // 6-b. 초과분 집계 (weeklyMaxMinutes 초과)
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

            // 6-c. 출근률/지각률 — 분모 0 방어
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

    /** 부서별현황 집계용 내부 상태 객체 (Service-private). */
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

    /* =========================================================================
     * Overtime List API — 초과근무 탭 (주간 사원별 근무/초과)
     *
     * 주간근무 = Σ(소정근무분 - 지각분 - 조퇴분) + 승인 OT 분
     *  - 휴가일/비근무요일: 승인 OT 만
     *  - 결근일: 0
     * 상태:
     *  - 초과 : weekly > weeklyMaxHour
     *  - 경고 : weekly >= weeklyWarningHour
     *  - 정상 : 그 외
     * ========================================================================= */

    /**
     * 초과근무 리스트 — 페이지네이션.
     */
    public PagedResDto<AttendanceOvertimeRowResDto> getOvertimeList(UUID companyId, LocalDate weekStart,
                                                                     EmploymentFilter filter,
                                                                     String keyword,
                                                                     int page, int size) {
        // 1. 입력 검증
        if (weekStart == null) throw new IllegalArgumentException("weekStart 는 필수입니다.");
        // 2. 정책값 / 기본 보정
        EmploymentFilter effectiveFilter = (filter != null) ? filter : EmploymentFilter.ALL;
        int weeklyMaxMinutes = resolveWeeklyMaxMinutes(companyId);
        int weeklyMaxHour = weeklyMaxMinutes / 60;
        int warningHour = resolveWeeklyWarningHour(companyId);
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
            long min = workedMinByEmp.getOrDefault(e.getKey(), 0L);
            double hours = round1(min / 60.0);
            double over = Math.max(0.0, round1(hours - weeklyMaxHour));

            // 상태 분기 — 정책 초과 > 경고 > 정상
            String status;
            if (hours > weeklyMaxHour)      status = "초과";
            else if (hours >= warningHour)  status = "경고";
            else                            status = "정상";

            all.add(AttendanceOvertimeRowResDto.builder()
                    .empId(r.getEmpId()).empNum(r.getEmpNum()).empName(r.getEmpName())
                    .deptName(r.getDeptName()).gradeName(r.getGradeName())
                    .weeklyWorkHours(hours)
                    .weeklyMaxHour(weeklyMaxHour)
                    .weeklyWarningHour(warningHour)
                    .overtimeHours(over)
                    .status(status)
                    .build());
        }

        // 7. 주간근무 DESC 정렬 (많이 일한 사람 먼저)
        all.sort(Comparator.comparingDouble(AttendanceOvertimeRowResDto::getWeeklyWorkHours).reversed());

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

    /* =========================================================================
     * 공통 계산 헬퍼 (주간 근무/결근/소정 계산)
     * ========================================================================= */

    /**
     * 당일 실근무 분 계산식.
     *  - 휴가일 OR 비근무요일: 승인 OT 분만
     *  - 결근(소정근무일이지만 출근기록 없음): 0
     *  - 그 외: Max(0, 소정 - 지각 - 조퇴) + 승인 OT
     */
    private long computeDayWorkedMinutes(AttendanceAdminRow r, LocalDate day) {
        boolean hasVac = Boolean.TRUE.equals(r.getHasApprovedVacationToday());
        boolean scheduled = isScheduledWorkDay(r, day);
        boolean hasCheckIn = (r.getComRecId() != null);
        long approvedOt = (r.getApprovedOtMinutesToday() != null) ? r.getApprovedOtMinutesToday() : 0L;

        // 1. 휴가/비근무요일: OT 만 반영
        if (hasVac || !scheduled) return approvedOt;
        // 2. 결근: 0 분
        if (!hasCheckIn) return 0L;
        // 3. 정상: 소정 - 지각 - 조퇴 + OT
        long sched = scheduledMinutes(r);
        long late = extractLateMinutes(r);
        long early = extractEarlyLeaveMinutes(r);
        long base = sched - late - early;
        if (base < 0) base = 0;
        return base + approvedOt;
    }

    /**
     * 근무그룹 소정근무분 = groupEndTime - groupStartTime.
     * Row 에 break 정보가 없으므로 소정 계산에서는 break 를 제외하지 않음
     * (LATE/EARLY 차감 로직과 동일한 기준을 맞추기 위함).
     */
    private long scheduledMinutes(AttendanceAdminRow r) {
        if (r.getGroupStartTime() == null || r.getGroupEndTime() == null) return 0L;
        long total = Duration.between(r.getGroupStartTime(), r.getGroupEndTime()).toMinutes();
        return Math.max(0, total);
    }

    /**
     * 소정근무요일 여부. WorkGroup.groupWorkDay 비트마스크 (월=1, 화=2, 수=4, …, 일=64).
     * workGroup 미배정(필수지만 이력 호환)이면 false.
     */
    private boolean isScheduledWorkDay(AttendanceAdminRow r, LocalDate day) {
        if (r.getGroupWorkDay() == null || r.getGroupStartTime() == null) return false;
        int bit = 1 << (day.getDayOfWeek().getValue() - 1);
        return (r.getGroupWorkDay() & bit) != 0;
    }

    /**
     * 지각 분 = max(0, checkInAt.time - groupStartTime). checkInStatus == LATE 일 때만 유효.
     */
    private long extractLateMinutes(AttendanceAdminRow r) {
        if (r.getCheckInAt() == null || r.getGroupStartTime() == null) return 0L;
        if (r.getCheckInStatus() != CheckInStatus.LATE) return 0L;
        long diff = Duration.between(r.getGroupStartTime(), r.getCheckInAt().toLocalTime()).toMinutes();
        return Math.max(0, diff);
    }

    /**
     * 조퇴 분 = max(0, groupEndTime - checkOutAt.time). checkOutStatus == EARLY_LEAVE 일 때만 유효.
     */
    private long extractEarlyLeaveMinutes(AttendanceAdminRow r) {
        if (r.getCheckOutAt() == null || r.getGroupEndTime() == null) return 0L;
        if (r.getCheckOutStatus() != CheckOutStatus.EARLY_LEAVE) return 0L;
        long diff = Duration.between(r.getCheckOutAt().toLocalTime(), r.getGroupEndTime()).toMinutes();
        return Math.max(0, diff);
    }

    /** double 값을 소수 1자리로 반올림. */
    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /* =========================================================================
     * Employee History API — 사원 일별 근무 현황 (상세 모달)
     *
     * 반환 구조:
     *  - header : 주간 근무시간 / 카드타입 에코 / 52시간 현황 (정책 기준)
     *  - history: 입사일 ~ 조회일 사이 commute_record 페이지 (workDate DESC)
     *
     * 판정(attendanceStatuses):
     *  - LATE / EARLY_LEAVE / OFFSITE / MISSING_COMMUTE(퇴근누락) / UNAPPROVED_OT / WORKING / NORMAL
     *  - week context 가 없는 단건 판정이라 MAX_HOUR_EXCEED/UNDER_MIN_HOUR/VACATION_ATTEND 는 제외
     * ========================================================================= */

    /**
     * 사원 일별 근무 현황 조회.
     *
     * @param companyId  회사 PK (Gateway 주입 헤더 값)
     * @param empId      대상 사원 PK
     * @param date       조회 기준일 (주간 근무시간 계산 기준)
     * @param cardType   드릴다운 카드 타입 (에코용, nullable)
     * @param page       0-based 페이지
     * @param size       페이지 크기 (기본 10)
     * @return 헤더 + 일별 근무 현황 페이지
     * @throws IllegalArgumentException 사원이 해당 회사 소속이 아니거나 date < hireDate
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

        // 3. 정책값 로드 — 52시간 현황 계산용
        int weeklyMaxMinutes = resolveWeeklyMaxMinutes(companyId);
        int weeklyMaxHour = weeklyMaxMinutes / 60;
        int warningHour = resolveWeeklyWarningHour(companyId);

        // 4. 주간 범위 (date 가 속한 월~일)
        LocalDate weekStart = date.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        // 5. 주간 실근무 분 합계 — native 쿼리로 single round-trip
        Long weeklyMinRaw = commuteRecordRepository.sumWorkedMinutesBetween(
                companyId, empId, weekStart, weekEnd);
        long weeklyMin = (weeklyMinRaw != null) ? weeklyMinRaw : 0L;
        double weeklyHours = weeklyMin / 60.0;

        // 6. 52시간 현황 라벨
        String weeklyStatus;
        if (weeklyHours > weeklyMaxHour)     weeklyStatus = "초과";
        else if (weeklyHours >= warningHour) weeklyStatus = "경고";
        else                                 weeklyStatus = "정상";

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
                .weeklyMaxHour(weeklyMaxHour)
                .weeklyWarningHour(warningHour)
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

    /**
     * 페이지 내 레코드 중 workDate 최소~최대 범위에 대해 APPROVED OT 분을 일자별 맵으로 반환.
     * native 쿼리 DATE(ot_date) 결과를 java.time.LocalDate 로 변환.
     */
    private Map<LocalDate, Long> loadApprovedOtByDate(Long empId, List<CommuteRecord> records) {
        if (records.isEmpty()) return Map.of();
        // 페이지 내 최소/최대 workDate 계산 — 쿼리 범위 최소화
        LocalDate minDate = records.get(0).getWorkDate();
        LocalDate maxDate = records.get(0).getWorkDate();
        for (CommuteRecord c : records) {
            if (c.getWorkDate().isBefore(minDate)) minDate = c.getWorkDate();
            if (c.getWorkDate().isAfter(maxDate))  maxDate = c.getWorkDate();
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

    /**
     * CommuteRecord + 근무그룹 + 승인 OT → 일별 행 DTO.
     */
    private AttendanceEmployeeHistoryRowResDto toHistoryRow(CommuteRecord c, WorkGroup wg, long approvedOt) {
        // 1. 실근무 분 — 출퇴근 둘 다 있을 때만
        LocalDateTime checkInAt = c.getComRecCheckIn();
        LocalDateTime checkOutAt = c.getComRecCheckOut();
        Long workMin = (checkInAt != null && checkOutAt != null)
                ? Duration.between(checkInAt, checkOutAt).toMinutes()
                : null;
        String workText = (workMin != null) ? formatHm(workMin) : null;

        // 2. 초과근무 표시값 — 승인 OT 분 0 이면 null 반환 (프론트 "-" 표시)
        Long otMin = (approvedOt > 0) ? approvedOt : null;
        String otText = (approvedOt > 0) ? formatHm(approvedOt) : null;

        // 3. 카드 리스트 계산 (주간 컨텍스트 없이 당일 단건 판정)
        List<AttendanceCardType> cards = judgeHistoricalDay(c, wg, approvedOt);

        // 4. 행 DTO
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

    /**
     * 일별 단건 판정 — 주간 컨텍스트가 없으므로 MAX_HOUR_EXCEED/UNDER_MIN_HOUR/VACATION_ATTEND 미적용.
     * 순서: 체크인 있으면 WORKING → LATE/EARLY_LEAVE/OFFSITE/UNAPPROVED_OT/MISSING_COMMUTE → (이상 없으면) NORMAL
     */
    private List<AttendanceCardType> judgeHistoricalDay(CommuteRecord c, WorkGroup wg, long approvedOt) {
        List<AttendanceCardType> out = new ArrayList<>(3);
        boolean hasCheckIn = (c.getComRecCheckIn() != null);
        boolean hasCheckOut = (c.getComRecCheckOut() != null);

        // 1. 근무중(WORKING) — 체크인 있으면 항상 포함
        if (hasCheckIn) out.add(AttendanceCardType.WORKING);

        // 2. 지각
        if (c.getCheckInStatus() == CheckInStatus.LATE) out.add(AttendanceCardType.LATE);

        // 3. 조퇴
        if (c.getCheckOutStatus() == CheckOutStatus.EARLY_LEAVE) out.add(AttendanceCardType.EARLY_LEAVE);

        // 4. 근무지 외
        if (Boolean.TRUE.equals(c.getIsOffsite())) out.add(AttendanceCardType.OFFSITE);

        // 5. 퇴근 누락 — 체크인 있고 체크아웃 없음
        if (hasCheckIn && !hasCheckOut) out.add(AttendanceCardType.MISSING_COMMUTE);

        // 6. 미승인 초과근무 — 체크아웃 > groupEndTime AND 승인 OT 없음
        if (hasCheckOut && wg != null && wg.getGroupEndTime() != null && approvedOt == 0) {
            LocalTime endTime = wg.getGroupEndTime();
            if (c.getComRecCheckOut().toLocalTime().isAfter(endTime)) {
                out.add(AttendanceCardType.UNAPPROVED_OT);
            }
        }

        // 7. 정상 — 체크인 있고 WORKING 외 이상 카드 없음
        if (hasCheckIn && out.size() == 1 && out.get(0) == AttendanceCardType.WORKING) {
            out.add(0, AttendanceCardType.NORMAL);
        }

        return out;
    }
}
