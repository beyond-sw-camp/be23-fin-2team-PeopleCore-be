package com.peoplecore.attendance.service;

import com.peoplecore.attendance.dto.AttendanceWeeklyHeadlineResDto;
import com.peoplecore.attendance.dto.WeekCommuteRow;
import com.peoplecore.attendance.dto.WeekEmpRow;
import com.peoplecore.attendance.dto.WeekVacationRow;
import com.peoplecore.attendance.entity.EmploymentFilter;
import com.peoplecore.attendance.entity.WorkStatus;
import com.peoplecore.attendance.entity.OvertimePolicy;
import com.peoplecore.attendance.repository.AttendanceAggregateQueryRepository;
import com.peoplecore.attendance.repository.OverTimePolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/*근태 현황 탭에서 집계
 * 계산 기준
 *  주 -> weekStart ~ weekEnd + 6
 * 분모 = 주 7일 각 일별 근무 예정자 수 합게 ()휴가자 제외
 * 나머지 계산식은 AttendenceAggregateQueryRepo에 있는 계산식이랑 같음 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class AttendanceAggregateService {

    /* 주 최대 근무 분 정책 미적용 회사용 기본값 (52h = 3120) */
    private static final int DEFAULT_WEEKLY_MAX_MINUTE = 3120;

    private final AttendanceAggregateQueryRepository aggregateQueryRepository;
    private final OverTimePolicyRepository overTimePolicyRepository;

    @Autowired
    public AttendanceAggregateService(AttendanceAggregateQueryRepository aggregateQueryRepository, OverTimePolicyRepository overTimePolicyRepository) {
        this.aggregateQueryRepository = aggregateQueryRepository;
        this.overTimePolicyRepository = overTimePolicyRepository;
    }

    /* 집계에서의 상단 4개 카드 계산 */
    public AttendanceWeeklyHeadlineResDto getWeeklyHeadline(UUID companyId, LocalDate weekStart, EmploymentFilter filter) {
        EmploymentFilter filter1 = (filter != null) ? filter : EmploymentFilter.ALL;
        LocalDate weekEnd = weekStart.plusDays(6);

        /* 마감일 분리
         *  - absenceEnd: 결근 누적 마감 = 오늘 (오늘 미출근자도 결근으로 잡힘)
         *  - denomEnd:   비율 분모/정상·지각 마감 = 어제 (오늘은 진행 중이라 비율 왜곡 방지)
         *  - 미래 일자는 양쪽 모두 제외 */
        LocalDate today = LocalDate.now();
        LocalDate absenceEnd = today.isAfter(weekEnd) ? weekEnd : today;
        LocalDate denomEnd = today.minusDays(1).isAfter(weekEnd) ? weekEnd : today.minusDays(1);
        /* 주가 시작 전(미래 주차) → 빈 결과 */
        if (absenceEnd.isBefore(weekStart) && denomEnd.isBefore(weekStart)) {
            return emptyHeadline(weekStart, weekEnd);
        }

        int weeklyMaxMinutes = resolveWeekMaxMinutes(companyId);

        List<WeekEmpRow> employees = aggregateQueryRepository.fetchEmployees(companyId, filter1);
        if (employees.isEmpty()) return emptyHeadline(weekStart, weekEnd);
        List<Long> empIds = employees.stream().map(WeekEmpRow::getEmpId).toList();

        /* 주간 최대근무 초과 판정용 누적은 주 전체 기준 유지 */
        List<WeekCommuteRow> commutes = aggregateQueryRepository.fetchCommutesInWeek(companyId, empIds, weekStart, weekEnd);
        List<WeekVacationRow> vacations = aggregateQueryRepository.fetApprovedVacationInWeek(companyId, empIds, weekStart, weekEnd);

        // (사원→(날짜→WorkStatus)) + 주간 누적분 맵 동시 구축. minutes=null(체크아웃 전) 은 0 처리
        Map<Long, Map<LocalDate, WorkStatus>> statusMap = new HashMap<>();
        Map<Long, Long> weekMinutesMap = new HashMap<>();
        for (WeekCommuteRow c : commutes) {
            statusMap
                    .computeIfAbsent(c.getEmpId(), k -> new HashMap<>())
                    .put(c.getWorkDate(), c.getWorkStatus());
            if (c.getMinutes() != null) {
                weekMinutesMap.merge(c.getEmpId(), c.getMinutes(), Long::sum);
            }
        }
        // (사원, 날짜) → 최대 휴가 비율 (반차/반반차 = <1.0, 종일 = 1.0)
        Map<Long, Map<LocalDate, Double>> vacFracMap = new HashMap<>();
        for (WeekVacationRow v : vacations) {
            LocalDate s = v.getStartAt().toLocalDate();
            LocalDate e = v.getEndAt().toLocalDate();
            boolean oneDay = s.equals(e);
            BigDecimal useDay = v.getVacReqUseDay();
            double rowFrac = (oneDay && useDay != null) ? useDay.doubleValue() : 1.0;

            LocalDate clampStart = s.isBefore(weekStart) ? weekStart : s;
            LocalDate clampEnd = e.isAfter(weekEnd) ? weekEnd : e;
            Map<LocalDate, Double> inner =
                    vacFracMap.computeIfAbsent(v.getEmpId(), k -> new HashMap<>());
            for (LocalDate d = clampStart; !d.isAfter(clampEnd); d = d.plusDays(1)) {
                inner.merge(d, rowFrac, Math::max);
            }
        }

        /* 루프 범위: weekStart ~ absenceEnd (둘 중 늦은 마감 기준). 분모는 내부에서 denomEnd 로 한 번 더 컷 */
        int aggDays = (int) (absenceEnd.toEpochDay() - weekStart.toEpochDay()) + 1;
        if (aggDays < 0) aggDays = 0;
        LocalDate[] days = new LocalDate[aggDays];
        int[] dayBits = new int[aggDays];
        for (int i = 0; i < aggDays; i++) {
            days[i] = weekStart.plusDays(i);
            dayBits[i] = 1 << (days[i].getDayOfWeek().getValue() - 1); // MONDAY=1 → bit0
        }

        long denom = 0L;        // 분모: 근무예정 + 종일휴가 아님 + ≤ denomEnd
        long normalCnt = 0L;    // 체크인 있음 + LATE 아님 + ≤ denomEnd
        long lateCnt = 0L;      // 체크인 있음 + LATE + ≤ denomEnd
        long absenceCnt = 0L;   // 근무예정 + 체크인 없음 + 승인휴가 전무 (≤ absenceEnd, 오늘 포함)
        long exceedCnt = 0L;    // 주간 최대근무시간 초과 사원 수 (주 전체 누적 기준)

        for (WeekEmpRow emp : employees) {
            Long empId = emp.getEmpId();

            // 주간 초과 판정 — 사원당 1회 (날짜 루프 밖)
            if (weekMinutesMap.getOrDefault(empId, 0L) > weeklyMaxMinutes) exceedCnt++;

            Integer gwd = emp.getGroupWorkDay();
            if (gwd == null) continue; // 근무그룹 미배정 → 분모/출결 대상 아님

            Map<LocalDate, WorkStatus> empCin = statusMap.getOrDefault(empId, Map.of());
            Map<LocalDate, Double> empVac = vacFracMap.getOrDefault(empId, Map.of());

            for (int i = 0; i < aggDays; i++) {
                if ((gwd & dayBits[i]) == 0) continue; // 근무예정일 아님
                LocalDate day = days[i];

                double frac = empVac.getOrDefault(day, 0.0);
                boolean fullDay = frac >= 1.0;
                boolean inDenomRange = !day.isAfter(denomEnd); // 비율 계산 기준일(어제까지)

                // 분모: 종일휴가 제외 + 어제까지
                if (inDenomRange && !fullDay) denom++;

                WorkStatus status = empCin.get(day);
                boolean noCheckIn = (status == null) || (status == WorkStatus.ABSENT);

                if (!noCheckIn) {
                    if (!inDenomRange) continue; // 오늘 체크인은 비율 분자에 안 넣음
                    if (status == WorkStatus.LATE || status == WorkStatus.LATE_AND_EARLY) lateCnt++;
                    else normalCnt++;
                } else if (frac == 0.0) {
                    absenceCnt++; // 결근은 오늘까지 누적 (반차라도 휴가 있으면 제외)
                }
            }
        }

        double attendanceRate = (denom == 0L) ? 0.0
                : roundTo1(((double) (normalCnt + lateCnt) / denom) * 100.0);
        double lateRate = (denom == 0L) ? 0.0
                : roundTo1(((double) lateCnt / denom) * 100.0);

        log.debug("[getWeeklyHeadline] companyId={}, week={}~{}, denomEnd={}, absenceEnd={}, emps={}, denom={}, normal={}, late={}, absence={}, exceed={}",
                companyId, weekStart, weekEnd, denomEnd, absenceEnd, employees.size(), denom, normalCnt, lateCnt, absenceCnt, exceedCnt);

        return AttendanceWeeklyHeadlineResDto.builder()
                .weekStart(weekStart)
                .weekEnd(weekEnd)
                .attendanceRate(attendanceRate)
                .lateRate(lateRate)
                .absentCount((int) absenceCnt)
                .weeklyMaxExceedCount((int) exceedCnt)
                .build();
    }


    /*회사별 주간 최대 근무시간 조회 */
    private int resolveWeekMaxMinutes(UUID companyId) {
        return overTimePolicyRepository.findByCompany_CompanyId(companyId)
                .map(OvertimePolicy::getOtPolicyWeeklyMaxMinutes)
                .orElse(DEFAULT_WEEKLY_MAX_MINUTE);

    }

    /* 소수 한자리 반올림
     * */
    private double roundTo1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /* 사원 0명 회사 등 빈 결과 대용용 초기 반환 Dto*/
    private AttendanceWeeklyHeadlineResDto emptyHeadline(LocalDate weekStart, LocalDate weekEnd) {
        return AttendanceWeeklyHeadlineResDto.builder()
                .weekStart(weekStart)
                .weekEnd(weekEnd)
                .attendanceRate(0.0)
                .lateRate(0.0)
                .absentCount(0)
                .weeklyMaxExceedCount(0)
                .build();

    }
}
