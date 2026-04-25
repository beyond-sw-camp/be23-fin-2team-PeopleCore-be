package com.peoplecore.attendance.service;

import com.peoplecore.attendance.dto.CheckInResDto;
import com.peoplecore.attendance.dto.CheckOutResDto;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.HolidayReason;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.entity.WorkStatus;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import com.peoplecore.attendance.repository.HolidayLookupRepository;
import com.peoplecore.company.service.CompanyAllowedIpService;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.entity.HolidayType;
import com.peoplecore.entity.Holidays;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.service.BusinessDayCalculator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/*
 * 출퇴근 체크인/아웃 서비스.
 *
 * 규칙:
 *  - 하루 1쌍. 퇴근 후 재출근 불가.
 *  - IP 허용 대역 밖 → 거부 X, 로그에만 표기 (entity isOffsite 필드 제거됨).
 *  - 휴일 → 허용, HOLIDAY_WORK + holidayReason 기록. 근무 인정은 배치.
 *  - workGroup 미배정 → 예외 (데이터 정합성).
 *  - WorkStatus 결정/전이는 WorkStatusResolver 위임.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class CommuteService {

    private final CommuteRecordRepository commuteRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyAllowedIpService companyAllowedIpService;
    private final HolidayLookupRepository holidayLookupRepository;
    private final PayrollMinutesCalculator payrollMinutesCalculator;
    /* 공휴일 판정 1차 필터 - 월 단위 캐시(Redis 6h TTL + write-through evict) 공유 */
    private final BusinessDayCalculator businessDayCalculator;
    private final WorkStatusResolver workStatusResolver;

    @Autowired
    public CommuteService(CommuteRecordRepository commuteRecordRepository,
                          EmployeeRepository employeeRepository,
                          CompanyAllowedIpService companyAllowedIpService,
                          HolidayLookupRepository holidayLookupRepository,
                          PayrollMinutesCalculator payrollMinutesCalculator,
                          BusinessDayCalculator businessDayCalculator,
                          WorkStatusResolver workStatusResolver) {
        this.commuteRecordRepository = commuteRecordRepository;
        this.employeeRepository = employeeRepository;
        this.companyAllowedIpService = companyAllowedIpService;
        this.holidayLookupRepository = holidayLookupRepository;
        this.payrollMinutesCalculator = payrollMinutesCalculator;
        this.businessDayCalculator = businessDayCalculator;
        this.workStatusResolver = workStatusResolver;
    }

    /*
     * 출근 체크인.
     * 1) IP 추출 → 2) 중복 체크 → 3) Employee/workGroup 로드
     * 4) 휴일 판정 → 5) 초기 WorkStatus 결정 → 6) 저장
     */
    @Transactional
    public CheckInResDto checkIn(UUID companyId, Long empId, HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        /* 1차 방어: 이미 오늘 기록이 있으면 즉시 409 (ABSENT 배치 레코드 포함) */
        commuteRecordRepository
                .findByCompanyIdAndEmployee_EmpIdAndWorkDate(companyId, empId, today)
                .ifPresent(r -> { throw new CustomException(ErrorCode.COMMUTE_ALREADY_CHECKED_IN); });

        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        WorkGroup wg = employee.getWorkGroup();
        if (wg == null) throw new CustomException(ErrorCode.EMPLOYEE_WORK_GROUP_NOT_ASSIGNED);

        boolean offsite = !companyAllowedIpService.matches(companyId, clientIp); // 로그용
        HolidayReason reason = resolveHolidayReason(companyId, today, wg);
        WorkStatus initialStatus = workStatusResolver.resolveInitial(
                now.toLocalTime(), wg.getGroupStartTime(), reason);

        CommuteRecord record = CommuteRecord.builder()
                .workDate(today)
                .companyId(companyId)
                .employee(employee)
                .comRecCheckIn(now)
                .checkInIp(clientIp)
                .workStatus(initialStatus)
                .holidayReason(reason)
                .build();

        /* 2차 방어: saveAndFlush 로 즉시 INSERT → UNIQUE 위반을 트랜잭션 내에서 감지 */
        try {
            CommuteRecord saved = commuteRecordRepository.saveAndFlush(record);
            log.info("[checkIn] empId={}, ip={}, offsite={}, workStatus={}, reason={}",
                    empId, clientIp, offsite, initialStatus, reason);
            return CheckInResDto.fromEntity(saved);
        } catch (DataIntegrityViolationException e) {
            /* UNIQUE(company_id, emp_id, work_date) 위반 → race condition */
            log.warn("[checkIn] UNIQUE 제약 위반 — 동시 요청 race condition. empId={}, date={}",
                    empId, today);
            throw new CustomException(ErrorCode.COMMUTE_ALREADY_CHECKED_IN);
        }
    }

    /*
     * 퇴근 체크아웃.
     * 1) 어제~오늘 범위의 open 레코드 조회 (파티션 프루닝, 최대 2개 파티션)
     * 2) 레코드는 있는데 체크인 시각이 null (ABSENT) → NOT_CHECKED_IN
     * 3) WorkStatus 전이 — WorkStatusResolver 위임
     * 4) 엔티티 checkOut + 급여분 베이스 계산
     */
    @Transactional
    public CheckOutResDto checkOut(UUID companyId, Long empId, HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        Optional<CommuteRecord> openRecord = commuteRecordRepository
                .findFirstByCompanyIdAndEmployee_EmpIdAndWorkDateBetweenAndComRecCheckOutIsNullOrderByWorkDateDesc(
                        companyId, empId, today.minusDays(1), today);

        CommuteRecord record = openRecord.orElseGet(() -> {
            /* open 레코드 없음 → 오늘 기록이 있는데 이미 퇴근한 케이스인지 확인 */
            CommuteRecord todays = commuteRecordRepository
                    .findByCompanyIdAndEmployee_EmpIdAndWorkDate(companyId, empId, today)
                    .orElseThrow(() -> new CustomException(ErrorCode.COMMUTE_NOT_CHECKED_IN));
            if (todays.getComRecCheckOut() != null) {
                throw new CustomException(ErrorCode.COMMUTE_ALREADY_CHECKED_OUT);
            }
            /* 이론상 도달 불가 — 방어적으로 NOT_CHECKED_IN */
            throw new CustomException(ErrorCode.COMMUTE_NOT_CHECKED_IN);
        });

        /* ABSENT 레코드(체크인 없는 빈 레코드)는 체크아웃 불가 */
        if (record.getComRecCheckIn() == null) {
            throw new CustomException(ErrorCode.COMMUTE_NOT_CHECKED_IN);
        }

        WorkGroup wg = record.getEmployee().getWorkGroup();
        if (wg == null) throw new CustomException(ErrorCode.EMPLOYEE_WORK_GROUP_NOT_ASSIGNED);

        WorkStatus finalStatus = workStatusResolver.resolveFinal(
                record.getWorkStatus(), now.toLocalTime(), wg.getGroupEndTime());

        /* workDate 유지, comRecCheckOut + workStatus 확정 */
        record.checkOut(now, clientIp, finalStatus);

        /* 체크아웃 시점엔 actual/overtime 만 기록 — recognized_* 는 OT 승인 시 반영 */
        payrollMinutesCalculator.applyCheckoutBase(record);

        log.info("[checkOut] empId={}, workDate={}, checkOutAt={}, ip={}, workStatus={}",
                empId, record.getWorkDate(), now, clientIp, finalStatus);
        return CheckOutResDto.fromEntity(record);
    }

    /*
     * 사후 OT 승인 시 재계산 진입점 — OvertimeRequestService.applyApprovalResult 에서 호출.
     * 체크아웃 전/없으면 no-op (향후 체크아웃 시 자동 계산됨).
     */
    @Transactional
    public void recalcPayrollMinutes(UUID companyId, Long empId, LocalDate workDate) {
        commuteRecordRepository
                .findByCompanyIdAndEmployee_EmpIdAndWorkDate(companyId, empId, workDate)
                .ifPresent(payrollMinutesCalculator::applyApprovedRecognition);
    }

    /*
     * 휴일 판정. NATIONAL > COMPANY > WEEKLY_OFF. 평일이면 null.
     * 1차: 월 단위 캐시(BusinessDayCalculator) 로 공휴일 여부 O(1) 판정 - 평일(95%)은 DB 0회
     * 2차: 공휴일 확정 시에만 findMatching 으로 NATIONAL/COMPANY 타입 구분 (연 15~20일 한정)
     */
    private HolidayReason resolveHolidayReason(UUID companyId, LocalDate date, WorkGroup wg) {
        Set<LocalDate> monthHolidays = businessDayCalculator.getHolidaysInMonth(companyId, YearMonth.from(date));
        if (monthHolidays.contains(date)) {
            /* 공휴일 확정 - 타입 구분 위해 단건 조회 (NATIONAL 우선 원칙 유지) */
            List<Holidays> matched = holidayLookupRepository.findMatching(
                    companyId, date, date.getMonthValue(), date.getDayOfMonth());
            boolean hasNational = matched.stream()
                    .anyMatch(h -> h.getHolidayType() == HolidayType.NATIONAL);
            return hasNational ? HolidayReason.NATIONAL : HolidayReason.COMPANY;
        }
        /* 주말 판정 - MONDAY(1)→bit0 ... SUNDAY(7)→bit6 비트마스크 */
        int bit = 1 << (date.getDayOfWeek().getValue() - 1);
        return (wg.getGroupWorkDay() & bit) != 0 ? null : HolidayReason.WEEKLY_OFF;
    }

    /* X-Forwarded-For 최초 토큰 > getRemoteAddr 폴백 */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}
