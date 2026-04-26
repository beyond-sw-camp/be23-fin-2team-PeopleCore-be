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
        /*초기화 */
        EmploymentFilter filter1 = (filter != null) ? filter : EmploymentFilter.ALL;
        LocalDate weekEnd = weekStart.plusDays(6);
        int weeklyMaxMinutes = resolveWeekMaxMinutes(companyId);

        /*3쿼리 일괄 조회 */
        List<WeekEmpRow> employees = aggregateQueryRepository.fetchEmployees(companyId, filter1);
        /*사원 0명이면 빈 지표로 조기 반환 */
        if (employees.isEmpty()) return emptyHeadline(weekStart, weekEnd);
        /* empIds 추출 */
        List<Long> empIds = employees.stream().map(WeekEmpRow::getEmpId).toList();


        List<WeekCommuteRow> commutes = aggregateQueryRepository.fetchCommutesInWeek(companyId, empIds, weekStart, weekEnd);
        List<WeekVacationRow> vacations = aggregateQueryRepository.fetApprovedVacationInWeek(companyId, empIds, weekStart, weekEnd);

        /*메모리 인덱싱 */
        /* commuteMap[empId][workDate]하루 한건 보장 (unique 제약)*/
        // 3. commutes 단일 순회 — (사원→(날짜→WorkStatus)) 맵 + 주간 누적분 맵 동시 구축
        //    체크아웃 전(minutes=null) 은 주간 누적에서 0 처리. ABSENT 레코드도 포함
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
        /* vacationMap[empId] -> List (한주에 여러 구간 가능) */
        // 4. vacations 단일 순회 — (사원, 날짜) → 최대 휴가 비율 (double)
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
        // 5. 7일 날짜/요일비트 사전 계산 — 사원 루프 내 재계산 제거
        LocalDate[] days = new LocalDate[7];
        int[] dayBits = new int[7];
        for (int i = 0; i < 7; i++) {
            days[i] = weekStart.plusDays(i);
            dayBits[i] = 1 << (days[i].getDayOfWeek().getValue() - 1); // MONDAY=1 → bit0
        }


        /*집계 */
        long denom = 0L;        // 분모: 근무예정 + 종일휴가 아님
        long normalCnt = 0L;    // 체크인 있음 + LATE 아님
        long lateCnt = 0L;      // 체크인 있음 + LATE
        long absenceCnt = 0L;   // 근무예정 + 체크인 없음 + 승인휴가 전무 (누적)
        long exceedCnt = 0L;    // 주간 최대근무시간 초과 사원 수 (사원당 1회 체크라 자연 distinct)

        for (WeekEmpRow emp : employees) {
            Long empId = emp.getEmpId();

            // 6-a. 주간 초과 판정 — 사원당 1회 (날짜 루프 밖)
            if (weekMinutesMap.getOrDefault(empId, 0L) > weeklyMaxMinutes) exceedCnt++;

            // 6-b. 근무그룹 미배정 → 분모/출결 대상 아님. 루프 스킵
            Integer gwd = emp.getGroupWorkDay();
            if (gwd == null) continue;

            // 6-c. 사원별 맵 참조 1회
            Map<LocalDate, WorkStatus> empCin = statusMap.getOrDefault(empId, Map.of());
            Map<LocalDate, Double> empVac = vacFracMap.getOrDefault(empId, Map.of());

            // 6-d. 7일 루프 — 근무예정일(bit AND) 만 내부 진입
            for (int i = 0; i < 7; i++) {
                if ((gwd & dayBits[i]) == 0) continue;
                LocalDate day = days[i];

                double frac = empVac.getOrDefault(day, 0.0);
                boolean fullDay = frac >= 1.0;

                // 분모: 종일휴가 제외 (반차/반반차는 포함)
                if (!fullDay) denom++;

                WorkStatus status = empCin.get(day);
                /* 체크인 부재: 레코드 없음(null) 또는 결근 레코드(ABSENT) */
                boolean noCheckIn = (status == null) || (status == WorkStatus.ABSENT);

                if (!noCheckIn) {
                    // 체크인 있는 레코드 — 지각 vs 정상 (LATE_AND_EARLY 도 지각으로 카운트)
                    if (status == WorkStatus.LATE || status == WorkStatus.LATE_AND_EARLY) lateCnt++;
                    else normalCnt++;
                } else if (frac == 0.0) {
                    // 체크인 없음 + 승인휴가 전무 → 결근 누적 (반차라도 휴가 있으면 제외)
                    absenceCnt++;
                }
            }
        }
        // 7. 비율 계산 (분모 0 방어)
        double attendanceRate = (denom == 0L) ? 0.0
                : roundTo1(((double) (normalCnt + lateCnt) / denom) * 100.0);
        double lateRate = (denom == 0L) ? 0.0
                : roundTo1(((double) lateCnt / denom) * 100.0);

        log.debug("[getWeeklyHeadline] companyId={}, week={}~{}, emps={}, denom={}, normal={}, late={}, absence={}, exceed={}",
                companyId, weekStart, weekEnd, employees.size(), denom, normalCnt, lateCnt, absenceCnt, exceedCnt);

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
