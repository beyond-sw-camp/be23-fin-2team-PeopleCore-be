package com.peoplecore.vacation.service;

import com.peoplecore.attendance.dto.VacationSlice;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.repository.VacationRequestQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/* 만근 판정 + 덮은 영업일 수 계산 서비스 */
/* isFullAttendance: 월차 적립 (덮은 영업일 >= 영업일) */
/* countCoveredBusinessDays: FISCAL 첫해 비례 부여 (덮은 영업일 수 자체) */
@Service
@Slf4j
@Transactional(readOnly = true)
public class AttendanceCheckService {

    private final BusinessDayCalculator businessDayCalculator;
    private final VacationRequestQueryRepository vacationRequestQueryRepository;
    private final CommuteRecordRepository commuteRecordRepository;

    @Autowired
    public AttendanceCheckService(BusinessDayCalculator businessDayCalculator,
                                  VacationRequestQueryRepository vacationRequestQueryRepository,
                                  CommuteRecordRepository commuteRecordRepository) {
        this.businessDayCalculator = businessDayCalculator;
        this.vacationRequestQueryRepository = vacationRequestQueryRepository;
        this.commuteRecordRepository = commuteRecordRepository;
    }

    /* 만근 여부 판정 - 덮은 영업일 >= 영업일 총수 */
    /* 영업일 0 (주말+공휴일만) 이면 판정 불가 → true 반환 (방어) */
    public boolean isFullAttendance(UUID companyId, Long empId, LocalDate periodStart, LocalDate periodEnd) {
        int businessDays = businessDayCalculator.countBusinessDays(companyId, periodStart, periodEnd);
        if (businessDays == 0) return true;
        int covered = countCoveredBusinessDays(companyId, empId, periodStart, periodEnd);
        log.debug("[AttendanceCheck] empId={}, period={}~{}, bizDays={}, covered={}, full={}",
                empId, periodStart, periodEnd, businessDays, covered, covered >= businessDays);
        return covered >= businessDays;
    }

    /* 덮은 영업일 수 - (출근일 ∪ 승인휴가일) ∩ 영업일 */
    /* 주말/공휴일은 출근/휴가라도 카운트 제외 (영업일만 집계) */
    public int countCoveredBusinessDays(UUID companyId, Long empId, LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null || periodStart.isAfter(periodEnd)) return 0;
        Set<LocalDate> covered = collectCoveredDates(companyId, empId, periodStart, periodEnd);
        return (int) covered.stream()
                .filter(d -> businessDayCalculator.isBusinessDay(companyId, d))
                .count();
    }

    /* 출근일 + 승인휴가일 합집합 수집 (중복 제거) */
    private Set<LocalDate> collectCoveredDates(UUID companyId, Long empId,
                                               LocalDate periodStart, LocalDate periodEnd) {
        Set<LocalDate> covered = new HashSet<>();

        /* 출근 (check_in 존재 row) */
        List<LocalDate> attendedDates = commuteRecordRepository
                .findAttendedDatesByEmpAndPeriod(companyId, empId, periodStart, periodEnd);
        covered.addAll(attendedDates);

        /* 승인 휴가 슬라이스 - 기간 교집합 클램프 후 일별 추가 */
        LocalDateTime startAt = periodStart.atStartOfDay();
        LocalDateTime endAt = periodEnd.atTime(LocalTime.MAX);
        List<VacationSlice> slices = vacationRequestQueryRepository
                .findApprovedSlicesInWeek(companyId, empId, RequestStatus.APPROVED, startAt, endAt);
        for (VacationSlice s : slices) {
            LocalDate from = s.startAt().toLocalDate();
            LocalDate to = s.endAt().toLocalDate();
            if (from.isBefore(periodStart)) from = periodStart;
            if (to.isAfter(periodEnd)) to = periodEnd;
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                covered.add(d);
            }
        }
        return covered;
    }
}