package com.peoplecore.attendance.service;

import com.peoplecore.attendance.dto.AttendanceAdminRow;
import com.peoplecore.attendance.dto.AttendanceDailyCardRowResDto;
import com.peoplecore.attendance.dto.AttendanceDailyListRowResDto;
import com.peoplecore.attendance.dto.AttendanceDailySummaryResDto;
import com.peoplecore.attendance.dto.PagedResDto;
import com.peoplecore.attendance.entity.AttendanceCardType;
import com.peoplecore.attendance.entity.EmploymentFilter;
import com.peoplecore.attendance.entity.OvertimePolicy;
import com.peoplecore.attendance.repository.AttendanceAdminQueryRepository;
import com.peoplecore.attendance.repository.OverTimePolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
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

    /** 시각 표시용 HH:mm 포맷터 (LATE/EARLY_LEAVE detail 등에 사용) */
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceAdminQueryRepository queryRepository;
    private final OverTimePolicyRepository overtimePolicyRepository;
    private final AttendanceStatusJudge judge;

    @Autowired
    public AttendanceAdminService(AttendanceAdminQueryRepository queryRepository,
                                  OverTimePolicyRepository overtimePolicyRepository,
                                  AttendanceStatusJudge judge) {
        this.queryRepository = queryRepository;
        this.overtimePolicyRepository = overtimePolicyRepository;
        this.judge = judge;
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
}
