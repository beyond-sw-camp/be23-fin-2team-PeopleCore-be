package com.peoplecore.attendance.service;

import com.peoplecore.attendance.dto.CheckInResDto;
import com.peoplecore.attendance.dto.CheckOutResDto;
import com.peoplecore.attendance.entity.*;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import com.peoplecore.attendance.repository.HolidayLookupRepository;
import com.peoplecore.company.service.CompanyAllowedIpService;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.entity.HolidayType;
import com.peoplecore.entity.Holidays;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
 * 출퇴근 체크인/아웃 서비스.
 *
 * 규칙:
 *  - 하루 1쌍. 퇴근 후 재출근 불가.
 *  - IP 허용 대역 밖 → 거부 X, isOffsite=true 플래그만.
 *  - 휴일 → 허용, HOLIDAY_WORK + holidayReason 기록. 근무 인정은 배치.
 *  - workGroup 미배정 → 예외 (데이터 정합성).
 *  - 판정: groupStartTime/groupEndTime 직접 비교. 임계값 없음.
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

    @Autowired
    public CommuteService(CommuteRecordRepository commuteRecordRepository,
                          EmployeeRepository employeeRepository,
                          CompanyAllowedIpService companyAllowedIpService,
                          HolidayLookupRepository holidayLookupRepository,
                          PayrollMinutesCalculator payrollMinutesCalculator) {
        this.commuteRecordRepository = commuteRecordRepository;
        this.employeeRepository = employeeRepository;
        this.companyAllowedIpService = companyAllowedIpService;
        this.holidayLookupRepository = holidayLookupRepository;
        this.payrollMinutesCalculator = payrollMinutesCalculator;
    }

    /*
     * 출근 체크인.
     * 1) IP 추출 → 2) 중복 체크 → 3) Employee/workGroup 로드
     * 4) offsite 결정 → 5) 휴일 판정 → 6) 상태 분류 → 7) 저장
     */
    @Transactional
    public CheckInResDto checkIn(UUID companyId, Long empId, HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        /* 1차 방어 : 이미 오늘 기록이 있으면 즉시 409*/
        commuteRecordRepository
                .findByCompanyIdAndEmployee_EmpIdAndWorkDate(companyId, empId, today)
                .ifPresent(r -> {
                    throw new CustomException(ErrorCode.COMMUTE_ALREADY_CHECKED_IN);
                });

        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        WorkGroup wg = employee.getWorkGroup();
        if (wg == null) throw new CustomException(ErrorCode.EMPLOYEE_WORK_GROUP_NOT_ASSIGNED);

        boolean offsite = !companyAllowedIpService.matches(companyId, clientIp);
        HolidayReason reason = resolveHolidayReason(companyId, today, wg);
        CheckInStatus status = (reason != null)
                ? CheckInStatus.HOLIDAY_WORK
                : CheckInStatus.classifyWorkingDay(now.toLocalTime(), wg.getGroupStartTime());

        CommuteRecord record = CommuteRecord.builder()
                .workDate(today)
                .companyId(companyId)
                .employee(employee)
                .comRecCheckIn(now)
                .checkInIp(clientIp)
                .isOffsite(offsite)
                .checkInStatus(status)
                .holidayReason(reason)
                .build();

        /*2차 방어 : saveAndFlush로 즉시 Insert 실행 -> UNIQUE 위반을 현재 트랜잭션 내에서 감지*/
        try {
            CommuteRecord saved = commuteRecordRepository.saveAndFlush(record);
            log.info("[checkIn] empId={}, ip={}, offsite={}, status={}, reason={}",
                    empId, clientIp, offsite, status, reason);
            return CheckInResDto.fromEntity(saved);
        } catch (DataIntegrityViolationException e) {
            /*UNIQUE(company_id, emp_id, work_date) 위반 → race condition 로 간주*/
            log.warn("[checkIn] UNIQUE 제약 위반 감지 - 동시 요청 race condition. empId={}, date={}",
                    empId, today);
            throw new CustomException(ErrorCode.COMMUTE_ALREADY_CHECKED_IN);
        }
    }

    /**
     * 퇴근 체크아웃.
     * 1) 오늘 기록 조회 → 없으면 COMMUTE_NOT_CHECKED_IN
     * 2) 이미 퇴근 → COMMUTE_ALREADY_CHECKED_OUT
     * 3) 체크인이 HOLIDAY_WORK 면 HOLIDAY_WORK_END, 아니면 시각 비교
     */
    @Transactional
    public CheckOutResDto checkOut(UUID companyId, Long empId, HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // 1) 어제~오늘 범위의 open 레코드(체크아웃 안 된) 최신 1건
        //    파티션 프루닝: workDate BETWEEN (어제, 오늘) → 최대 2개 파티션만 스캔
        Optional<CommuteRecord> openRecord = commuteRecordRepository
                .findFirstByCompanyIdAndEmployee_EmpIdAndWorkDateBetweenAndComRecCheckOutIsNullOrderByWorkDateDesc(
                        companyId, empId, today.minusDays(1), today);


        CommuteRecord record = openRecord.orElseGet(() -> {
            /*open 레코드 없음 -> 오늘 기록이 있는데 이미 퇴근한 케이스인지 확인 */
            CommuteRecord todays = commuteRecordRepository.findByCompanyIdAndEmployee_EmpIdAndWorkDate(companyId, empId, today).orElseThrow(() -> new CustomException(ErrorCode.COMMUTE_NOT_CHECKED_IN));
            if (todays.getComRecCheckOut() != null) {
                throw new CustomException(ErrorCode.COMMUTE_ALREADY_CHECKED_OUT);
            }
            /* 이론상 도달 불가
             * 방어적으로 NOT_CHECKED_IN 던짐*/
            throw new CustomException(ErrorCode.COMMUTE_NOT_CHECKED_IN);
        });

        /* 체크아웃 상태 판정 */
        CheckOutStatus status;
        if (record.getCheckInStatus() == CheckInStatus.HOLIDAY_WORK) {
            status = CheckOutStatus.HOLIDAY_WORK_END;
        } else {
            WorkGroup wg = record.getEmployee().getWorkGroup();
            if (wg == null) throw new CustomException(ErrorCode.EMPLOYEE_WORK_GROUP_NOT_ASSIGNED);
            status = CheckOutStatus.classifyWorkingDay(now.toLocalTime(), wg.getGroupEndTime());
        }

        /* workDate 유지, comRecCheckOut 만 세팅*/
        record.checkOut(now, clientIp, status);

        /* 체크아웃 시점엔 actual/overtime 만 기록 — recognized_* 는 OT 승인 시 반영 */
        payrollMinutesCalculator.applyCheckoutBase(record);

        log.info("[checkOut] empId={}, workDate={}, checkOutAt={}, ip={}, status={}", empId, record.getWorkDate(), now, clientIp, status);
        return CheckOutResDto.fromEntity(record);

    }

    /**
     * 사후 OT 승인 시 재계산 진입점 — OvertimeRequestService.applyApprovalResult 에서 호출.
     * 해당 날짜의 체크아웃 완료된 CommuteRecord 찾아서 recognized_* 재산출.
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
     */
    private HolidayReason resolveHolidayReason(UUID companyId, LocalDate date, WorkGroup wg) {
        List<Holidays> matched = holidayLookupRepository.findMatching(
                companyId, date, date.getMonthValue(), date.getDayOfMonth());
        if (!matched.isEmpty()) {
            boolean hasNational = matched.stream()
                    .anyMatch(h -> h.getHolidayType() == HolidayType.NATIONAL);
            return hasNational ? HolidayReason.NATIONAL : HolidayReason.COMPANY;
        }
        // MONDAY(1)→bit0 ... SUNDAY(7)→bit6
        int bit = 1 << (date.getDayOfWeek().getValue() - 1);
        return (wg.getGroupWorkDay() & bit) != 0 ? null : HolidayReason.WEEKLY_OFF;
    }

    /*X-Forwarded-For 최초 토큰 > getRemoteAddr 폴백 */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}