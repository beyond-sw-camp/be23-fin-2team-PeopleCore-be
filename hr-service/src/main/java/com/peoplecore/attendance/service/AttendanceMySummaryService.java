package com.peoplecore.attendance.service;

import com.peoplecore.attendance.dto.*;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.OvertimePolicy;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import com.peoplecore.attendance.repository.MyAttendanceQueryRepository;
import com.peoplecore.attendance.repository.OverTimePolicyRepository;
import com.peoplecore.attendance.repository.OvertimeRequestRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.repository.VacationRequestQueryRepository;
import com.peoplecore.vacation.service.BusinessDayCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@Transactional(readOnly = true)
/*사원 개인 주간 근태 요약 서비스
 * 응답 구조 : today - 오늘자 출퇴근 시각,workGroup - 근무그룹 정보 + 회사 저객 주간 최대 weekly - 주간 집계 박스안 4개 */
public class AttendanceMySummaryService {

    /*
     * 근무 요일 비트마스크 (7비트, 월~일)
     */
    private static final int WORK_DAY_BITMASK = 0x7F;

    private final EmployeeRepository employeeRepository;
    private final OverTimePolicyRepository overTimePolicyRepository;
    private final MyAttendanceQueryRepository myAttendanceQueryRepository;
    private final VacationRequestQueryRepository vacationRequestQueryRepository;   /* QueryDSL 로 이동됨 */
    private final CommuteRecordRepository commuteRecordRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final BusinessDayCalculator businessDayCalculator;


    @Autowired
    public AttendanceMySummaryService(EmployeeRepository employeeRepository, OverTimePolicyRepository overTimePolicyRepository, MyAttendanceQueryRepository myAttendanceQueryRepository, VacationRequestQueryRepository vacationRequestQueryRepository, CommuteRecordRepository commuteRecordRepository, OvertimeRequestRepository overtimeRequestRepository, BusinessDayCalculator businessDayCalculator) {
        this.employeeRepository = employeeRepository;
        this.overTimePolicyRepository = overTimePolicyRepository;
        this.myAttendanceQueryRepository = myAttendanceQueryRepository;
        this.vacationRequestQueryRepository = vacationRequestQueryRepository;
        this.commuteRecordRepository = commuteRecordRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.businessDayCalculator = businessDayCalculator;
    }

    /* 주간 요약 조회 */
    public AttendanceMyWeeklySummaryResDto getWeeklySummary(UUID companyId, Long empId, LocalDate date) {
        /*기준일 보정 (해당 일이 속한 주를 찾기 위해) */
        LocalDate baseDate = (date != null) ? date : LocalDate.now();
        LocalDate weekStart = baseDate.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        /*사원 조회 (회사 소속 검증 )*/
        Employee employee = employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId).orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        WorkGroup wg = employee.getWorkGroup();

        if (wg == null) {
            throw new CustomException(ErrorCode.EMPLOYEE_WORK_GROUP_NOT_ASSIGNED);
        }

        /* 회사 정책 - 주간 최대 분  /  회사 생성 시 기본 정책을 넣어주고 있기 때문에 없으면 에러가 맞음*/
        int companyWeeklyMaxMinutes = overTimePolicyRepository.findByCompany_CompanyId(companyId).map(OvertimePolicy::getOtPolicyWeeklyMaxMinutes).orElseThrow(() -> new CustomException(ErrorCode.OVERTIME_POLICY_NOT_FOUND));

        /*통합 집계 */
        WeeklyCommuteAggregate agg = myAttendanceQueryRepository.aggregateWeeklyStats(companyId, empId, weekStart, weekEnd);

        /* 오늘자 출퇴근 1건 — date 파라미터와 무관하게 항상 "오늘" 기준으로 조회 */
        TodayCommuteDto today = loadTodayCommute(companyId, empId, LocalDate.now());

        /* 근무 그룹 블록에 넣을 것 + 1일 근무 분 . 주 적정분 계산 */
        int dailyWorkMinutes = calcDailyWorkMinutes(wg);
        int weeklyWorkDays = businessDayCalculator.countBusinessDays(companyId, wg, weekStart, weekEnd);
        int weeklyWorkMinutes = dailyWorkMinutes * weeklyWorkDays;

        MyWorkGroupDto workGroupDto = MyWorkGroupDto.builder()
                .workGroupId(wg.getWorkGroupId())
                .groupName(wg.getGroupName())
                .groupStartTime(wg.getGroupStartTime())
                .groupEndTime(wg.getGroupEndTime())
                .dailyWorkMinutes(dailyWorkMinutes)
                .weeklyWorkDays(weeklyWorkDays)
                .weeklyWorkMinutes(weeklyWorkMinutes)
                .companyWeeklyMaxMinutes(companyWeeklyMaxMinutes)
                .build();

        /*주간 휴가 분 */
        long vacationMinutes = calcVacationMinutes(companyId, empId, weekStart, weekEnd, wg, dailyWorkMinutes);

        /*주간 블록 조립 */
        long workedMin = agg.workedMinutes();
        long attendanceDays = agg.attendedDays();
        long remainingMin = Math.max(0L, (long) weeklyWorkMinutes - workedMin - vacationMinutes);
        // 휴가분을 일수로 환산해 잔여에서 차감 (분 잔여 계산과 동일한 근거)
        int vacationDays = (dailyWorkMinutes > 0)
                ? (int) Math.round((double) vacationMinutes / dailyWorkMinutes)
                : 0;
        int remainingDays = Math.max(0, weeklyWorkDays - (int) attendanceDays - vacationDays);

        MyWeeklyStatsDto weekly = MyWeeklyStatsDto.builder()
                .weekStart(weekStart)
                .weekEnd(weekEnd)
                .workedMinutes(workedMin)
                .vacationMinutes(vacationMinutes)
                .attendedDays((int) attendanceDays)
                .workDays(weeklyWorkDays)
                .remainingDays(remainingDays)
                .remainingMinutes(remainingMin)
                .approvedOvertimeMinutes(agg.recognizedMinutes())
                .abnormalDays(agg.autoClosedDays().intValue())
                .build();

        log.debug("[getWeeklySummary] companyId={}, empId={}, week=[{}~{}], worked={}, vac={}, remain={}",
                companyId, empId, weekStart, weekEnd, workedMin, vacationMinutes, remainingMin);

        return AttendanceMyWeeklySummaryResDto.builder()
                .today(today)
                .workGroup(workGroupDto)
                .weekly(weekly)
                .build();
    }


    /* 월간 요약 조회 — 지각/인증 초과근무 일자별 상세 + 헤더 카운트 */
    public AttendanceMyMonthlySummaryResDto getMonthlySummary(UUID companyId, Long empId, YearMonth yearMonth) {
        YearMonth ym = (yearMonth != null) ? yearMonth : YearMonth.from(LocalDate.now());
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        /* 사원 + 근무 그룹 */
        Employee employee = employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        WorkGroup wg = employee.getWorkGroup();
        if (wg == null) {
            throw new CustomException(ErrorCode.EMPLOYEE_WORK_GROUP_NOT_ASSIGNED);
        }
        LocalTime groupStart = wg.getGroupStartTime();
        LocalTime groupEnd = wg.getGroupEndTime();

        /* 지각 행 */
        List<CommuteRecord> lateRecords = myAttendanceQueryRepository
                .findMonthlyLateRecords(companyId, empId, monthStart, monthEnd);

        List<MonthlyLateDayDto> lateDays = new ArrayList<>(lateRecords.size());
        for (CommuteRecord c : lateRecords) {
            lateDays.add(MonthlyLateDayDto.builder()
                    .workDate(c.getWorkDate())
                    .checkInAt(c.getComRecCheckIn())
                    .lateMinutes(calcLateMinutes(c.getComRecCheckIn(), groupStart))
                    .build());
        }

        /* 초과근무 행 + 승인 OT 시작시각 매핑 */
        List<CommuteRecord> otRecords = myAttendanceQueryRepository
                .findMonthlyOvertimeRecords(companyId, empId, monthStart, monthEnd);
        Map<LocalDate, LocalDateTime> approvedPlanStartByDate =
                loadApprovedPlanStartByDate(empId, monthStart, monthEnd);

        long overtimeMinutesSum = 0L;
        List<MonthlyOvertimeDayDto> overtimeDays = new ArrayList<>(otRecords.size());
        for (CommuteRecord c : otRecords) {
            long approvedMin = recognizedTotal(c);
            overtimeMinutesSum += approvedMin;
            // APPROVED OT 가 있으면 otPlanStart, 없으면 (workDate + groupEndTime) 폴백
            LocalDateTime startAt = approvedPlanStartByDate.getOrDefault(
                    c.getWorkDate(), LocalDateTime.of(c.getWorkDate(), groupEnd));
            overtimeDays.add(MonthlyOvertimeDayDto.builder()
                    .workDate(c.getWorkDate())
                    .overtimeStartAt(startAt)
                    .checkOutAt(c.getComRecCheckOut())
                    .approvedOvertimeMinutes(approvedMin)
                    .build());
        }

        log.debug("[getMonthlySummary] companyId={}, empId={}, ym={}, lateCnt={}, otDays={}, otMin={}",
                companyId, empId, ym, lateDays.size(), overtimeDays.size(), overtimeMinutesSum);

        return AttendanceMyMonthlySummaryResDto.builder()
                .yearMonth(ym.toString())
                .lateCount(lateDays.size())
                .overtimeMinutes(overtimeMinutesSum)
                .overtimeDayCount(overtimeDays.size())
                .lateDays(lateDays)
                .overtimeDays(overtimeDays)
                .build();
    }

    /* 지각 분 = max(0, checkIn.time - groupStart) */
    private long calcLateMinutes(LocalDateTime checkInAt, LocalTime groupStart) {
        if (checkInAt == null || groupStart == null) return 0L;
        long diff = Duration.between(groupStart, checkInAt.toLocalTime()).toMinutes();
        return Math.max(0L, diff);
    }

    /* 인정 초과 합 = extended + night + holiday */
    private long recognizedTotal(CommuteRecord c) {
        long ext = c.getRecognizedExtendedMinutes() != null ? c.getRecognizedExtendedMinutes() : 0L;
        long night = c.getRecognizedNightMinutes() != null ? c.getRecognizedNightMinutes() : 0L;
        long holi = c.getRecognizedHolidayMinutes() != null ? c.getRecognizedHolidayMinutes() : 0L;
        return ext + night + holi;
    }

    /* 월 구간 APPROVED OT 의 일자별 가장 이른 시작 시각 매핑 */
    private Map<LocalDate, LocalDateTime> loadApprovedPlanStartByDate(Long empId,
                                                                      LocalDate monthStart,
                                                                      LocalDate monthEnd) {
        LocalDateTime from = monthStart.atStartOfDay();
        LocalDateTime to = monthEnd.atTime(LocalTime.MAX);
        List<Object[]> rows = overtimeRequestRepository.findApprovedPlanStartByDate(empId, from, to);
        Map<LocalDate, LocalDateTime> map = new HashMap<>(rows.size() * 2);
        for (Object[] r : rows) {
            LocalDate d = ((java.sql.Date) r[0]).toLocalDate();
            LocalDateTime planStart = ((java.sql.Timestamp) r[1]).toLocalDateTime();
            map.put(d, planStart);
        }
        return map;
    }

    /* 오늘자 CommuteRecord 조회 후 출퇴근 시간만 추출 두 값 전부 null일 수 도 있음  */
    private TodayCommuteDto loadTodayCommute(UUID companyId, Long empId, LocalDate today) {
        return commuteRecordRepository.findByCompanyIdAndEmployee_EmpIdAndWorkDate(companyId, empId, today).map(this::toTodayDto).orElseGet(() ->
                TodayCommuteDto.builder().checkIn(null).checkOut(null).build());
    }

    private TodayCommuteDto toTodayDto(CommuteRecord c) {
        return TodayCommuteDto.builder()
                .checkIn(c.getComRecCheckIn() != null ? c.getComRecCheckIn().toLocalTime() : null)
                .checkOut(c.getComRecCheckOut() != null ? c.getComRecCheckOut().toLocalTime() : null)
                .build();
    }

    /*1일 근무 분 = groupEnd - groupStart ) - groupBreakEnd - groupBreakStart
     * 휴게 구간 미 지정시 차감 0, */
    private int calcDailyWorkMinutes(WorkGroup wg) {
        long span = Duration.between(wg.getGroupStartTime(), wg.getGroupEndTime()).toMinutes();
        long breakMin = (wg.getGroupBreakStart() != null && wg.getGroupBreakEnd() != null) ? Duration.between(wg.getGroupBreakStart(),wg.getGroupBreakEnd()).toMinutes() : 0L;
        return (int) Math.max(0L, span - breakMin);
    }

    /* 주간 휴가 분 useDay  * (주 교집합 근무이ㅣㄹ수 / 휴가 전체 근무일 수 ) * dailyWorkMinutes
     * 주 교집합 ㅣ 휴가 구간과 해당 주가 겹치는 요일
     * 전체 휴가 구간 전체 중 근무 요일만 카운트
     * */
    private long calcVacationMinutes(UUID companyId, Long empId, LocalDate weekStart, LocalDate weekEnd, WorkGroup wg, int dailyWorkMinutes) {
        if (dailyWorkMinutes <= 0) return 0L;
        int gwd = wg.getGroupWorkDay();
        if ((gwd & WORK_DAY_BITMASK) == 0) return 0L; // 근무 요일 0개면 휴가 환산 의미 없음
        /* 주구간 LocalDate 변환  */
        LocalDateTime weekStartDt = weekStart.atStartOfDay();
        LocalDateTime weekEndDt = weekEnd.atTime(LocalTime.MAX);


        List<VacationSlice> slices = vacationRequestQueryRepository.findApprovedSlicesInWeek(
                companyId, empId, RequestStatus.APPROVED, weekStartDt, weekEndDt);

        if (slices.isEmpty()) return 0L;

        long total = 0L;
        for (VacationSlice s : slices) {
            LocalDate sliceStart = s.startAt().toLocalDate();
            LocalDate sliceEnd = s.endAt().toLocalDate();

            /* 휴가 구간 전체 근무일 수 */
            int totalWorkDays = countWorkDaysInRange(sliceStart, sliceEnd, gwd);
            if (totalWorkDays == 0) continue; // 전부 비 근무요일

            /*조회 후 교집합 구간 근무일 수 */
            LocalDate clampStart = sliceStart.isBefore(weekStart) ? weekStart : sliceStart;
            LocalDate clampEnd = sliceEnd.isAfter(weekEnd) ? weekEnd : sliceEnd;
            if (clampStart.isAfter(clampEnd)) continue;
            int weekWorkDays = countWorkDaysInRange(clampStart, clampEnd, gwd);
            if (weekWorkDays == 0) continue;

            /* 분계산 */
            BigDecimal useDay = (s.useDay() != null) ? s.useDay() : BigDecimal.ZERO;
            BigDecimal ratio = BigDecimal.valueOf(weekWorkDays).divide(BigDecimal.valueOf(totalWorkDays), 6, RoundingMode.HALF_UP);

            long minutes = useDay.multiply(ratio).multiply(BigDecimal.valueOf(dailyWorkMinutes)).setScale(0, RoundingMode.HALF_UP).longValue();
            total += Math.max(0L, minutes);
        }
        return total;
    }


    /*from -> to 구간 중 근무요일인 날짜 개수 */
    private int countWorkDaysInRange(LocalDate from, LocalDate to, int groupWorkDay) {
        int count = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            int bit = 1 << (d.getDayOfWeek().getValue() - 1);
            if ((groupWorkDay & bit) != 0) count++;
        }
        return count;
    }
}
