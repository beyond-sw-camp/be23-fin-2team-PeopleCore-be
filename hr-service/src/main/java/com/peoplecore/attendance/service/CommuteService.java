package com.peoplecore.attendance.service;

import com.peoplecore.attendance.dto.CheckInResDto;
import com.peoplecore.attendance.dto.CheckOutResDto;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.HolidayReason;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.entity.WorkStatus;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import com.peoplecore.attendance.util.ClientIpExtractor;
import com.peoplecore.company.service.CompanyAllowedIpService;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/*
 * 출퇴근 체크인/아웃 서비스.
 *
 * 규칙:
 *  - 하루 1쌍. 퇴근 후 재출근 불가.
 *  - IP 허용 대역 밖 → 거부 (활성 IP 미등록 회사는 정책 미적용 → 모든 IP 허용).
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
    private final PayrollMinutesCalculator payrollMinutesCalculator;
    private final WorkStatusResolver workStatusResolver;
    private final HolidayReasonResolver holidayReasonResolver;
    private final ClientIpExtractor clientIpExtractor;

    @Autowired
    public CommuteService(CommuteRecordRepository commuteRecordRepository,
                          EmployeeRepository employeeRepository,
                          CompanyAllowedIpService companyAllowedIpService,
                          PayrollMinutesCalculator payrollMinutesCalculator,
                          WorkStatusResolver workStatusResolver,
                          HolidayReasonResolver holidayReasonResolver,
                          ClientIpExtractor clientIpExtractor) {
        this.commuteRecordRepository = commuteRecordRepository;
        this.employeeRepository = employeeRepository;
        this.companyAllowedIpService = companyAllowedIpService;
        this.payrollMinutesCalculator = payrollMinutesCalculator;
        this.workStatusResolver = workStatusResolver;
        this.holidayReasonResolver = holidayReasonResolver;
        this.clientIpExtractor = clientIpExtractor;
    }

    /*
     * 출근 체크인.
     * 1) IP 추출 → 2) 중복 체크 → 3) Employee/workGroup 로드
     * 4) 휴일 판정 → 5) 초기 WorkStatus 결정 → 6) 저장
     */
    @Transactional
    public CheckInResDto checkIn(UUID companyId, Long empId, HttpServletRequest request) {
        String clientIp = clientIpExtractor.extract(request);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // TODO: [배포 검증 후 제거] 운영에서 clientIp 가 회사 외부 공인 IP 로 도달하는지 확인용 임시 로그
        log.info("[checkIn-DEBUG] empId={}, clientIp={}, xff={}, remoteAddr={}",
                empId, clientIp, request.getHeader("X-Forwarded-For"), request.getRemoteAddr());

        /* IP 정책 선검증: 회사가 활성 IP를 등록했다면 해당 대역 밖에서 출근 불가 */
        if (!companyAllowedIpService.isAllowed(companyId, clientIp)) {
            throw new CustomException(ErrorCode.COMMUTE_IP_NOT_ALLOWED);
        }

        /* 1차 방어: 이미 오늘 기록이 있으면 즉시 409 (ABSENT 배치 레코드 포함) */
        commuteRecordRepository
                .findByCompanyIdAndEmployee_EmpIdAndWorkDate(companyId, empId, today)
                .ifPresent(r -> { throw new CustomException(ErrorCode.COMMUTE_ALREADY_CHECKED_IN); });

        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        WorkGroup wg = employee.getWorkGroup();
        if (wg == null) throw new CustomException(ErrorCode.EMPLOYEE_WORK_GROUP_NOT_ASSIGNED);

        HolidayReason reason = holidayReasonResolver.resolve(companyId, today, wg);
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
            log.info("[checkIn] empId={}, ip={}, workStatus={}, reason={}",
                    empId, clientIp, initialStatus, reason);
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
        String clientIp = clientIpExtractor.extract(request);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // TODO: [배포 검증 후 제거] 운영에서 clientIp 가 회사 외부 공인 IP 로 도달하는지 확인용 임시 로그
        log.info("[checkOut-DEBUG] empId={}, clientIp={}, xff={}, remoteAddr={}",
                empId, clientIp, request.getHeader("X-Forwarded-For"), request.getRemoteAddr());

        /* IP 정책 선검증: 회사가 활성 IP를 등록했다면 해당 대역 밖에서 퇴근 불가 */
        if (!companyAllowedIpService.isAllowed(companyId, clientIp)) {
            throw new CustomException(ErrorCode.COMMUTE_IP_NOT_ALLOWED);
        }

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
        /* 시계 역행 등 비정상 케이스 방어 */
        if (now.isBefore(record.getComRecCheckIn())) {
            throw new IllegalStateException(
                    "체크아웃 시각이 체크인보다 이전 - comRecId=" + record.getComRecId()
                            + ", checkIn=" + record.getComRecCheckIn() + ", checkOut=" + now);
        }

        WorkGroup wg = record.getEmployee().getWorkGroup();
        if (wg == null) throw new CustomException(ErrorCode.EMPLOYEE_WORK_GROUP_NOT_ASSIGNED);

        /* 1. workStatus 결정 */
        WorkStatus finalStatus = workStatusResolver.resolveFinal(
                record.getWorkStatus(), now.toLocalTime(), wg.getGroupEndTime());

        /* 2. 분 컬럼 산출 (mutate/persist 없음) - APPROVAL 은 0, ALL 은 overtime 전체 자동 인정 */
        PayrollMinutesCalculator.PayrollMinutes m = payrollMinutesCalculator.computeForCheckout(record, now);

        /* 3. 단일 native UPDATE - 9 컬럼 + work_date 포함(파티션 프루닝). 가드 WHERE 로 atomic check */
        int affected = commuteRecordRepository.applyCheckOut(
                record.getComRecId(), record.getWorkDate(),
                now, clientIp, finalStatus.name(),
                m.actual(), m.overtime(), m.unrecognizedOt(),
                m.recExt(), m.recNight(), m.recHoliday());
        if (affected != 1) {
            log.warn("[checkOut] UPDATE 실패 - comRecId={}, affected={} (이미 체크아웃됨/race)",
                    record.getComRecId(), affected);
            throw new CustomException(ErrorCode.COMMUTE_ALREADY_CHECKED_OUT);
        }

        log.info("[checkOut] empId={}, workDate={}, checkOutAt={}, ip={}, workStatus={}",
                empId, record.getWorkDate(), now, clientIp, finalStatus);

        /* 4. DTO 직접 빌드 (엔티티는 native UPDATE 후 stale - 읽지 않음) */
        return CheckOutResDto.builder()
                .comRecId(record.getComRecId())
                .workDate(record.getWorkDate())
                .checkInAt(record.getComRecCheckIn())
                .checkOutAt(now)
                .checkOutIp(clientIp)
                .workedMinutes(Duration.between(record.getComRecCheckIn(), now).toMinutes())
                .workStatus(finalStatus)
                .holidayReason(record.getHolidayReason())
                .build();
    }

    /*
     * 사후 OT 승인 시 재계산 진입점 — OvertimeRequestService.applyApprovalResult 에서 호출.
     * 체크아웃 전/없으면 no-op (향후 체크아웃 시 자동 계산됨).
     */
    @Transactional
    public void recalcPayrollMinutes(UUID companyId, Long empId, LocalDate workDate) {
        commuteRecordRepository
                .findByCompanyIdAndEmployee_EmpIdAndWorkDate(companyId, empId, workDate)
                .ifPresent(record -> payrollMinutesCalculator.applyApprovedRecognition(
                        record, PayrollMinutesCalculator.RecognitionSource.OT_REQUEST));
    }

}
