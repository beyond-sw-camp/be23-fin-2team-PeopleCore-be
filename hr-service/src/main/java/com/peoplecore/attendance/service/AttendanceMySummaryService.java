package com.peoplecore.attendance.service;

import com.peoplecore.attendance.dto.*;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.OvertimePolicy;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import com.peoplecore.attendance.repository.MyAttendanceQueryRepository;
import com.peoplecore.attendance.repository.OverTimePolicyRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.entity.VacationStatus;
import com.peoplecore.vacation.repository.VacationReqRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.List;
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
    private final VacationReqRepository vacationReqRepository;
    private final CommuteRecordRepository commuteRecordRepository;

    @Autowired
    public AttendanceMySummaryService(EmployeeRepository employeeRepository, OverTimePolicyRepository overTimePolicyRepository, MyAttendanceQueryRepository myAttendanceQueryRepository, VacationReqRepository vacationReqRepository, CommuteRecordRepository commuteRecordRepository) {
        this.employeeRepository = employeeRepository;
        this.overTimePolicyRepository = overTimePolicyRepository;
        this.myAttendanceQueryRepository = myAttendanceQueryRepository;
        this.vacationReqRepository = vacationReqRepository;
        this.commuteRecordRepository = commuteRecordRepository;
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

        /*오늘자 출퇴근 1건 */
        TodayCommuteDto today = loadTodayCommute(companyId, empId, baseDate);

        /* 근무 그룹 블록에 넣을 것 + 1일 근무 분 . 주 적정분 계산 */
        int dailyWorkMinutes = calcDailyWorkMinutes(wg);
        int weeklyWorkDays = countWorkDays(wg.getGroupWorkDay());
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
        int remainingDays = Math.max(0, weeklyWorkDays - (int) attendanceDays);
        long remainingMin = Math.max(0L, (long) weeklyWorkMinutes - workedMin - vacationMinutes);

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

    /* 근무 요일 수 -> 비트 마스크 중 1로 셋된 비트 카운ㅌ
     * 월 1 화 2 수 4 ...*/
    private int countWorkDays(int groupWorkDay) {
        return Integer.bitCount(groupWorkDay & WORK_DAY_BITMASK);
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


        List<VacationSlice> slices = vacationReqRepository.findApprovedSlicesInWeek(companyId, empId, VacationStatus.APPROVED, weekStartDt, weekEndDt);
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
