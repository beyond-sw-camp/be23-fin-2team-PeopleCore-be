package com.peoplecore.attendance.service;

import com.peoplecore.alarm.publisher.HrAlarmPublisher;
import com.peoplecore.attendance.cache.ApprovalFormIdCache;
import com.peoplecore.attendance.dto.*;
import com.peoplecore.attendance.entity.AttendanceModify;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.HolidayReason;
import com.peoplecore.attendance.entity.ModifyStatus;
import com.peoplecore.attendance.entity.OtExceedAction;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.entity.WorkStatus;
import com.peoplecore.attendance.publisher.AttendanceModifyRejectedByHrPublisher;
import com.peoplecore.attendance.repository.AttendanceModifyRepository;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.event.AttendanceModifyDocCreatedEvent;
import com.peoplecore.event.AttendanceModifyRejectedByHrEvent;
import com.peoplecore.event.AttendanceModifyResultEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.repository.VacationRequestQueryRepository;
import com.peoplecore.vacation.service.BusinessDayCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;

/*
 * 근태 정정 Service.
 */
@Service
@Slf4j
@Transactional
public class AttendanceModifyService {

    private static final String ALARM_TYPE_ATTENDANCE = "ATTENDANCE";
    private static final String ALARM_REF_TYPE = "ATTENDANCE_MODIFY";

    private final AttendanceModifyRepository attendanceModifyRepository;
    private final CommuteRecordRepository commuteRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final ApprovalFormIdCache approvalFormIdCache;
    private final AttendanceModifyRejectedByHrPublisher rejectedPublisher;
    private final HrAlarmPublisher hrAlarmPublisher;
    private final VacationRequestQueryRepository vacationRequestQueryRepository;
    private final HolidayReasonResolver holidayReasonResolver;
    private final BusinessDayCalculator businessDayCalculator;
    private final WorkStatusResolver workStatusResolver;
    private final OvertimeLimitChecker overtimeLimitChecker;
    private final PayrollMinutesCalculator payrollMinutesCalculator;

    @Autowired
    public AttendanceModifyService(AttendanceModifyRepository attendanceModifyRepository,
                                   CommuteRecordRepository commuteRecordRepository,
                                   EmployeeRepository employeeRepository,
                                   ApprovalFormIdCache approvalFormIdCache,
                                   AttendanceModifyRejectedByHrPublisher rejectedPublisher,
                                   HrAlarmPublisher hrAlarmPublisher,
                                   VacationRequestQueryRepository vacationRequestQueryRepository,
                                   HolidayReasonResolver holidayReasonResolver,
                                   BusinessDayCalculator businessDayCalculator,
                                   WorkStatusResolver workStatusResolver,
                                   OvertimeLimitChecker overtimeLimitChecker,
                                   PayrollMinutesCalculator payrollMinutesCalculator) {
        this.attendanceModifyRepository = attendanceModifyRepository;
        this.commuteRecordRepository = commuteRecordRepository;
        this.employeeRepository = employeeRepository;
        this.approvalFormIdCache = approvalFormIdCache;
        this.rejectedPublisher = rejectedPublisher;
        this.hrAlarmPublisher = hrAlarmPublisher;
        this.vacationRequestQueryRepository = vacationRequestQueryRepository;
        this.holidayReasonResolver = holidayReasonResolver;
        this.businessDayCalculator = businessDayCalculator;
        this.workStatusResolver = workStatusResolver;
        this.overtimeLimitChecker = overtimeLimitChecker;
        this.payrollMinutesCalculator = payrollMinutesCalculator;
    }

    /* ===================== 1) 프리필 ===================== */

    @Transactional(readOnly = true)
    public AttendanceModifyPrefillResDto prefill(UUID companyId, Long empId, LocalDate workDate) {
        /* 같은 날 PENDING 중복 차단 (CommuteRecord 존재 여부와 무관) */
        boolean pendingExists = attendanceModifyRepository
                .existsByEmployee_EmpIdAndWorkDateAndAttenStatus(empId, workDate, ModifyStatus.PENDING);
        if (pendingExists) {
            throw new CustomException(ErrorCode.ATTENDANCE_MODIFY_PENDING_EXISTS);
        }

        Employee emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        Long formId = approvalFormIdCache.getAttendanceModifyFormId(companyId);

        /* CommuteRecord 있으면 현재값으로 prefill, 없으면 빈 값 (휴일 근무 미입력 등) */
        Optional<CommuteRecord> opt = commuteRecordRepository
                .findByCompanyIdAndEmployee_EmpIdAndWorkDate(companyId, empId, workDate);

        AttendanceModifyPrefillResDto.AttendanceModifyPrefillResDtoBuilder b = AttendanceModifyPrefillResDto.builder()
                .formId(formId)
                .formCode(ApprovalFormIdCache.FORM_CODE_ATTENDANCE_MODIFY)
                .workDate(workDate)
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .gradeName(emp.getGrade() != null ? emp.getGrade().getGradeName() : null)
                .titleName(emp.getTitle() != null ? emp.getTitle().getTitleName() : null);

        opt.ifPresent(cr -> b
                .comRecId(cr.getComRecId())
                .currentCheckIn(cr.getComRecCheckIn())
                .currentCheckOut(cr.getComRecCheckOut())
                .isAutoClosed(cr.getWorkStatus() == WorkStatus.AUTO_CLOSED)
                .workStatus(cr.getWorkStatus())
                .workStatusLabel(cr.getWorkStatus() != null ? cr.getWorkStatus().getLabel() : null));

        /* 주간 한도 + 현재 사용 분 - 프론트가 정정 후 추정값 비교해 BLOCK 차단 */
        OvertimeLimitChecker.WeeklyUsage usage = overtimeLimitChecker.usageBefore(companyId, empId, workDate);
        b.weeklyMaxMinutes(usage.weeklyMaxMinutes())
         .weekUsedMinutes(usage.usedMinutes())
         .exceedAction(usage.exceedAction());

        return b.build();
    }

    /*
     * collab 상신 이벤트 수신 → 검증 선행 → 통과 시 AttendanceModify INSERT.
     * 검증 단계: 멱등성 → PENDING 중복 → 주간 한도 (BLOCK 시 역방향 반려).
     */
    public void createFromApproval(AttendanceModifyDocCreatedEvent event) {
        /* 멱등성 - 동일 docId 중복 수신 */
        var existing = attendanceModifyRepository
                .findByCompanyIdAndApprovalDocId(event.getCompanyId(), event.getApprovalDocId());
        if (existing.isPresent()) {
            log.info("[AttendanceModify] docCreated 중복 수신 - docId={}", event.getApprovalDocId());
            return;
        }

        /* PENDING 중복 - 같은 사원/같은 날짜 기준 */
        boolean pendingExists = attendanceModifyRepository
                .existsByEmployee_EmpIdAndWorkDateAndAttenStatus(
                        event.getEmpId(), event.getWorkDate(), ModifyStatus.PENDING);
        if (pendingExists) {
            publishRejection(event, "동일 일자 정정 신청이 이미 진행 중입니다.", "PENDING 중복");
            return;
        }

        Employee emp = employeeRepository.findById(event.getEmpId())
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        /* 주간 한도 검증 - INSERT 전 선행. BLOCK + 초과 시 역방향 반려, NOTIFY 면 통과 후 알림 표기 */
        OvertimeLimitChecker.WeeklyUsage usage = overtimeLimitChecker.usageAfterModify(
                event.getCompanyId(), event.getEmpId(), emp.getWorkGroup(),
                event.getWorkDate(), event.getAttenReqCheckIn(), event.getAttenReqCheckOut());
        if (usage.isExceeded() && usage.exceedAction() == OtExceedAction.BLOCK) {
            publishRejection(event,
                    "주간 최대 근무시간(" + (usage.weeklyMaxMinutes() / 60) + "h)을 초과합니다.",
                    "한도 초과 BLOCK");
            return;
        }

        AttendanceModify entity = AttendanceModify.builder()
                .companyId(event.getCompanyId())
                .employee(emp)
                .comRecId(event.getComRecId())
                .workDate(event.getWorkDate())
                .attenEmpName(emp.getEmpName())
                .attenEmpDeptName(emp.getDept() != null ? emp.getDept().getDeptName() : "")
                .attenEmpGrade(emp.getGrade() != null ? emp.getGrade().getGradeName() : "")
                .attenEmpTitle(emp.getTitle() != null ? emp.getTitle().getTitleName() : "")
                .attenReqCheckIn(event.getAttenReqCheckIn())
                .attenReqCheckOut(event.getAttenReqCheckOut())
                .attenReason(event.getAttenReason())
                .attenStatus(ModifyStatus.PENDING)
                .approvalDocId(event.getApprovalDocId())
                .build();
        AttendanceModify saved = attendanceModifyRepository.save(entity);
        log.info("[AttendanceModify] INSERT - attenModiId={}, docId={}",
                saved.getAttenModiId(), saved.getApprovalDocId());

        /* HR 관리자 알림 - 한도 초과 NOTIFY 면 메시지에 표기 */
        if (usage.isExceeded()) {
            notifyHrAdmins(event.getCompanyId(), saved,
                    emp.getEmpName() + " 사원의 근태 정정 신청 (주간 한도 초과)",
                    "정정 적용 시 주간 누적 " + formatHm(usage.usedMinutes())
                            + " / 한도 " + formatHm(usage.weeklyMaxMinutes()));
        } else {
            notifyHrAdmins(event.getCompanyId(), saved,
                    emp.getEmpName() + " 사원의 근태 정정 신청",
                    "신청이 접수되었습니다. 결재 진행을 확인해 주세요.");
        }
    }

    /* 역방향 반려 이벤트 발행 - PENDING 중복 / 한도 초과 BLOCK 공통 */
    private void publishRejection(AttendanceModifyDocCreatedEvent event, String reason, String tag) {
        rejectedPublisher.publish(AttendanceModifyRejectedByHrEvent.builder()
                .companyId(event.getCompanyId())
                .approvalDocId(event.getApprovalDocId())
                .rejectReason(reason)
                .build());
        log.info("[AttendanceModify] {} → 역방향 반려 - docId={}", tag, event.getApprovalDocId());
    }

    /* "Xh YYm" 라벨 헬퍼 */
    private String formatHm(long minutes) {
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    /*
     * collab 결재 결과 이벤트 수신 → 상태 반영.
     * 승인 시 comRecId 있으면 native UPDATE, 없으면 신규 CommuteRecord INSERT (휴일근무 미입력 정정 등).
     */
    public void applyApprovalResult(AttendanceModifyResultEvent event) {
        AttendanceModify am = attendanceModifyRepository
                .findByCompanyIdAndApprovalDocId(event.getCompanyId(), event.getApprovalDocId())
                .orElseThrow(() -> new CustomException(ErrorCode.ATTENDANCE_MODIFY_NOT_FOUND));

        Employee manager = (event.getManagerId() != null)
                ? employeeRepository.findById(event.getManagerId()).orElse(null)
                : null;

        switch (event.getStatus()) {
            case "승인" -> {
                am.approve(manager);
                applyApprovedRecord(am);
                notifyRequester(am, "근태 정정 신청이 승인되었습니다.",
                        "수정된 출퇴근 시각이 반영되었습니다.");
            }
            case "반려" -> {
                am.reject(manager, event.getRejectReason());
                notifyRequester(am, "근태 정정 신청이 반려되었습니다.",
                        event.getRejectReason() != null ? event.getRejectReason() : "반려 사유 없음");
            }
            case "취소" -> {
                am.cancel();
                notifyRequester(am, "근태 정정 신청이 회수되었습니다.",
                        "기안자가 문서를 회수했습니다.");
            }
            default -> log.warn("[AttendanceModify] 알 수 없는 status - {}", event.getStatus());
        }
        log.info("[AttendanceModify] 결과 반영 - attenModiId={}, status={}",
                am.getAttenModiId(), event.getStatus());
    }

    /* 정정 승인 반영
     *  - comRecId 있으면 native UPDATE (check-in/out + workStatus 일괄, work_date 포함)
     *  - 없으면 신규 INSERT (휴일근무 미입력 정정 등)
     * 두 케이스 모두 끝에 PayrollMinutesCalculator.applyApprovedRecognition 호출 → native UPDATE 로 분 컬럼 재계산.
     * workStatus: 새 시간 기준 final 까지 자바에서 산출(NORMAL/LATE/EARLY_LEAVE/LATE_AND_EARLY/HOLIDAY_WORK). */
    private void applyApprovedRecord(AttendanceModify am) {
        Employee emp = am.getEmployee();
        WorkGroup wg = emp.getWorkGroup();
        HolidayReason reason = holidayReasonResolver.resolve(am.getCompanyId(), am.getWorkDate(), wg);
        /* 새 시간 기준 final WorkStatus 자바에서 미리 산출 - DB 일률 변환 금지 */
        WorkStatus newStatus = resolveCorrectedStatus(
                am.getAttenReqCheckIn(), am.getAttenReqCheckOut(), wg, reason);

        if (am.getComRecId() != null) {
            /* native UPDATE - check-in/out + status 일괄, work_date 포함(파티션 프루닝) */
            int updated = commuteRecordRepository.applyAttendanceModify(
                    am.getComRecId(), am.getWorkDate(),
                    am.getAttenReqCheckIn(), am.getAttenReqCheckOut(),
                    newStatus.name());
            if (updated != 1) {
                log.error("[AttendanceModify] CommuteRecord UPDATE 실패 - attenModiId={}, affected={}",
                        am.getAttenModiId(), updated);
                throw new CustomException(ErrorCode.ATTENDANCE_MODIFY_APPLY_FAILED);
            }
            /* native UPDATE 직후 영속성 컨텍스트 비워진 상태 → 최신값 다시 로드해 분 컬럼 재계산 */
            CommuteRecord reloaded = commuteRecordRepository
                    .findByComRecIdAndWorkDate(am.getComRecId(), am.getWorkDate())
                    .orElseThrow(() -> new CustomException(ErrorCode.ATTENDANCE_MODIFY_APPLY_FAILED));
            payrollMinutesCalculator.applyApprovedRecognition(
                    reloaded, PayrollMinutesCalculator.RecognitionSource.ATTENDANCE_MODIFY);
            log.info("[AttendanceModify] UPDATE + 상태/분 재판정 - attenModiId={}, comRecId={}, status={}",
                    am.getAttenModiId(), am.getComRecId(), newStatus);
            return;
        }

        /* INSERT 케이스 - 신규 record (INSERT 는 work_date 컬럼값으로 partition routing 자동) */
        CommuteRecord saved = commuteRecordRepository.save(CommuteRecord.builder()
                .workDate(am.getWorkDate())
                .companyId(am.getCompanyId())
                .employee(emp)
                .comRecCheckIn(am.getAttenReqCheckIn())
                .comRecCheckOut(am.getAttenReqCheckOut())
                .holidayReason(reason)
                .workStatus(newStatus)
                .build());
        payrollMinutesCalculator.applyApprovedRecognition(
                saved, PayrollMinutesCalculator.RecognitionSource.ATTENDANCE_MODIFY);
        log.info("[AttendanceModify] INSERT + 상태/분 재판정 - attenModiId={}, comRecId={}, status={}",
                am.getAttenModiId(), saved.getComRecId(), newStatus);
    }

    /* 새 checkIn/checkOut 기준 final WorkStatus 산출.
     * checkIn null → NORMAL 폴백(체크인 없는 정정은 비정상 케이스).
     * checkOut null → initial 만 반환(조퇴 판정 불가). */
    private WorkStatus resolveCorrectedStatus(LocalDateTime ci, LocalDateTime co,
                                              WorkGroup wg, HolidayReason reason) {
        if (ci == null) return WorkStatus.NORMAL;
        WorkStatus initial = workStatusResolver.resolveInitial(
                ci.toLocalTime(), wg.getGroupStartTime(), reason);
        if (co == null) return initial;
        return workStatusResolver.resolveFinal(initial, co.toLocalTime(), wg.getGroupEndTime());
    }


    @Transactional(readOnly = true)
    public AttendanceModifyResDto getDetail(UUID companyId, Long attenModiId) {
        AttendanceModify am = attendanceModifyRepository
                .findByCompanyIdAndAttenModiId(companyId, attenModiId)
                .orElseThrow(() -> new CustomException(ErrorCode.ATTENDANCE_MODIFY_NOT_FOUND));
        return toResDto(am);
    }

    @Transactional(readOnly = true)
    public Page<AttendanceModifyListResDto> getListForAdmin(UUID companyId,
                                                            ModifyStatus status,
                                                            Pageable pageable) {
        Page<AttendanceModify> page = (status == null)
                ? attendanceModifyRepository.findByCompanyId(companyId, pageable)
                : attendanceModifyRepository.findByCompanyIdAndAttenStatus(companyId, status, pageable);
        return page.map(this::toListDto);
    }

    @Transactional(readOnly = true)
    public Page<AttendanceModifyListResDto> getMyHistory(Long empId, Pageable pageable) {
        return attendanceModifyRepository.findByEmployee_EmpId(empId, pageable).map(this::toListDto);
    }

    /* ===================== 내부 알림 헬퍼 ===================== */

    private void notifyHrAdmins(UUID companyId, AttendanceModify am, String title, String content) {
        List<Employee> hrAdmins = employeeRepository.findByCompany_CompanyIdAndEmpRoleIn(
                companyId, List.of(EmpRole.HR_ADMIN, EmpRole.HR_SUPER_ADMIN));
        if (hrAdmins.isEmpty()) return;
        hrAlarmPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .empIds(hrAdmins.stream().map(Employee::getEmpId).toList())
                .alarmType(ALARM_TYPE_ATTENDANCE)
                .alarmTitle(title)
                .alarmContent(content)
                .alarmLink("/attendance/admin")
                .alarmRefType(ALARM_REF_TYPE)
                .alarmRefId(am.getAttenModiId())
                .build());
    }

    private void notifyRequester(AttendanceModify am, String title, String content) {
        hrAlarmPublisher.publisher(AlarmEvent.builder()
                .companyId(am.getCompanyId())
                .empIds(List.of(am.getEmployee().getEmpId()))
                .alarmType(ALARM_TYPE_ATTENDANCE)
                .alarmTitle(title)
                .alarmContent(content)
                .alarmLink("/attendance/my")
                .alarmRefType(ALARM_REF_TYPE)
                .alarmRefId(am.getAttenModiId())
                .build());
    }

    /* ===================== DTO 매핑 ===================== */

    private AttendanceModifyResDto toResDto(AttendanceModify am) {
        return AttendanceModifyResDto.builder()
                .attenModiId(am.getAttenModiId())
                .approvalDocId(am.getApprovalDocId())
                .comRecId(am.getComRecId())
                .workDate(am.getWorkDate())
                .empId(am.getEmployee().getEmpId())
                .attenEmpName(am.getAttenEmpName())
                .attenEmpDeptName(am.getAttenEmpDeptName())
                .attenEmpGrade(am.getAttenEmpGrade())
                .attenEmpTitle(am.getAttenEmpTitle())
                .attenReqCheckIn(am.getAttenReqCheckIn())
                .attenReqCheckOut(am.getAttenReqCheckOut())
                .attenReason(am.getAttenReason())
                .attenStatus(am.getAttenStatus())
                .managerId(am.getManager() != null ? am.getManager().getEmpId() : null)
                .managerName(am.getManager() != null ? am.getManager().getEmpName() : null)
                .attenRejectReason(am.getAttenRejectReason())
                .createdAt(am.getCreatedAt())
                .updatedAt(am.getUpdatedAt())
                .build();
    }

    private AttendanceModifyListResDto toListDto(AttendanceModify am) {
        return AttendanceModifyListResDto.builder()
                .attenModiId(am.getAttenModiId())
                .approvalDocId(am.getApprovalDocId())
                .workDate(am.getWorkDate())
                .attenEmpName(am.getAttenEmpName())
                .attenEmpDeptName(am.getAttenEmpDeptName())
                .attenEmpGrade(am.getAttenEmpGrade())
                .attenReqCheckIn(am.getAttenReqCheckIn())
                .attenReqCheckOut(am.getAttenReqCheckOut())
                .attenReason(am.getAttenReason())
                .attenStatus(am.getAttenStatus())
                .createdAt(am.getCreatedAt())
                .build();
    }

    /*
     * 사원의 주간 근태 + 미인증 초과근무 + 승인 휴가 조회.
     * weekStart 는 어느 요일이 들어와도 해당 주 월요일로 정규화.
     * 결근 배치 도입 후 평일+비휴가+비공휴일은 항상 CommuteRecord 존재 → else 분기는 휴일/주말/휴가일 fallback.
     */
    @Transactional(readOnly = true)
    public AttendanceModifyWeekResDto getWeek(UUID companyId, Long empId, LocalDate weekStartParam) {
        LocalDate monday = weekStartParam.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        WorkGroup wg = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND))
                .getWorkGroup();

        Map<LocalDate, CommuteRecord> recordMap = loadCommuteRecords(companyId, empId, monday, sunday);
        Map<LocalDate, VacationSlice> vacationMap = loadApprovedVacations(companyId, empId, monday, sunday);
        Set<LocalDate> monthHolidays = loadHolidaysForWeek(companyId, monday, sunday);

        List<AttendanceModifyWeekResDto.Day> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            days.add(buildDay(date, recordMap.get(date), vacationMap.get(date), wg, monthHolidays));
        }

        return AttendanceModifyWeekResDto.builder()
                .weekStart(monday)
                .weekEnd(sunday)
                .days(days)
                .build();
    }

    /* 주간 CommuteRecord → 날짜별 Map. 인덱스 + 파티션 프루닝 */
    private Map<LocalDate, CommuteRecord> loadCommuteRecords(UUID companyId, Long empId,
                                                             LocalDate monday, LocalDate sunday) {
        List<CommuteRecord> records = commuteRecordRepository
                .findByCompanyIdAndEmployee_EmpIdAndWorkDateBetweenOrderByWorkDateDesc(
                        companyId, empId, monday, sunday,
                        org.springframework.data.domain.Pageable.unpaged())
                .getContent();
        Map<LocalDate, CommuteRecord> map = new HashMap<>();
        for (CommuteRecord cr : records) map.put(cr.getWorkDate(), cr);
        return map;
    }

    /* 승인 휴가 슬라이스 → 날짜별 Map. 한 날 여러 건이면 useDay 큰 쪽(종일 > 반차) 우선 */
    private Map<LocalDate, VacationSlice> loadApprovedVacations(UUID companyId, Long empId,
                                                                LocalDate monday, LocalDate sunday) {
        List<VacationSlice> slices = vacationRequestQueryRepository.findApprovedSlicesInWeek(
                companyId, empId, RequestStatus.APPROVED,
                monday.atStartOfDay(), sunday.atTime(LocalTime.MAX));
        Map<LocalDate, VacationSlice> map = new HashMap<>();
        for (VacationSlice s : slices) {
            LocalDate from = s.startAt().toLocalDate();
            LocalDate to = s.endAt().toLocalDate();
            if (from.isBefore(monday)) from = monday;
            if (to.isAfter(sunday)) to = sunday;
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                VacationSlice prev = map.get(d);
                if (prev == null
                        || (s.useDay() != null
                            && (prev.useDay() == null
                                || s.useDay().compareTo(prev.useDay()) > 0))) {
                    map.put(d, s);
                }
            }
        }
        return map;
    }

    /* 주가 두 달 걸치면 두 달 공휴일 합집합 - 캐시 1~2회 조회로 끝 */
    private Set<LocalDate> loadHolidaysForWeek(UUID companyId, LocalDate monday, LocalDate sunday) {
        Set<LocalDate> holidays = new HashSet<>(
                businessDayCalculator.getHolidaysInMonth(companyId, YearMonth.from(monday)));
        if (!YearMonth.from(monday).equals(YearMonth.from(sunday))) {
            holidays.addAll(businessDayCalculator.getHolidaysInMonth(companyId, YearMonth.from(sunday)));
        }
        return holidays;
    }

    /* 단일 일자 Day 슬롯 - cr 분기 + 휴가 정보 합성 */
    private AttendanceModifyWeekResDto.Day buildDay(LocalDate date, CommuteRecord cr, VacationSlice vac,
                                                    WorkGroup wg, Set<LocalDate> monthHolidays) {
        AttendanceModifyWeekResDto.Day.DayBuilder b = AttendanceModifyWeekResDto.Day.builder()
                .workDate(date)
                .dayOfWeek(date.getDayOfWeek());

        if (cr != null) applyCommuteRecord(b, cr);
        else applyEmptyFallback(b, date, wg, monthHolidays);

        applyVacation(b, vac);
        return b.build();
    }

    /* CommuteRecord 실데이터 분기 - holidayReason 은 INSERT 시점 결정값 그대로 사용 */
    private void applyCommuteRecord(AttendanceModifyWeekResDto.Day.DayBuilder b, CommuteRecord cr) {
        long overtime = cr.getOvertimeMinutes() != null ? cr.getOvertimeMinutes() : 0L;
        long recognized = cr.getRecognizedExtendedMinutes() != null ? cr.getRecognizedExtendedMinutes() : 0L;
        long unrecognized = Math.max(0L, overtime - recognized);
        b.isHoliday(cr.getHolidayReason() != null)
                .holidayReason(cr.getHolidayReason())
                .comRecId(cr.getComRecId())
                .checkIn(cr.getComRecCheckIn())
                .checkOut(cr.getComRecCheckOut())
                .actualWorkMinutes(cr.getActualWorkMinutes() != null ? cr.getActualWorkMinutes() : 0L)
                .recognizedOvertimeMinutes(recognized)
                .unrecognizedOvertimeMinutes(unrecognized)
                .workStatus(cr.getWorkStatus());
    }

    /* 빈 슬롯 fallback - 결근 배치가 INSERT 안 한 케이스 (휴일/주말/휴가일) */
    private void applyEmptyFallback(AttendanceModifyWeekResDto.Day.DayBuilder b, LocalDate date,
                                    WorkGroup wg, Set<LocalDate> monthHolidays) {
        HolidayReason hr = holidayReasonResolver.resolve(date, wg, monthHolidays);
        b.isHoliday(hr != null)
                .holidayReason(hr)
                .comRecId(null)
                .checkIn(null)
                .checkOut(null)
                .actualWorkMinutes(0L)
                .recognizedOvertimeMinutes(0L)
                .unrecognizedOvertimeMinutes(0L)
                .workStatus(null);
    }

    /* 휴가 정보 - 슬라이스 있으면 채우고, 없으면 false */
    private void applyVacation(AttendanceModifyWeekResDto.Day.DayBuilder b, VacationSlice vac) {
        if (vac != null) {
            b.isVacation(true)
                    .vacationTypeName(vac.typeName())
                    .vacationStart(vac.startAt())
                    .vacationEnd(vac.endAt())
                    .vacationUseDay(vac.useDay());
        } else {
            b.isVacation(false);
        }
    }

    /**
     * 회사의 HR_ADMIN + HR_SUPER_ADMIN 사원 목록.
     * 용도: 결재선 선택 UI / 상신 검증 훅.
     * 정렬: empId ASC (안정 정렬, 프론트 추가 정렬 자유).
     */
    @Transactional(readOnly = true)
    public AttendanceModifyHrMemberResDto getHrMembers(UUID companyId) {
        List<Employee> hrs = employeeRepository.findByCompany_CompanyIdAndEmpRoleIn(
                companyId, List.of(EmpRole.HR_ADMIN, EmpRole.HR_SUPER_ADMIN));

        List<AttendanceModifyHrMemberResDto.HrMember> mapped = hrs.stream()
                .sorted((a, b) -> Long.compare(a.getEmpId(), b.getEmpId()))
                .map(e -> AttendanceModifyHrMemberResDto.HrMember.builder()
                        .empId(e.getEmpId())
                        .empName(e.getEmpName())
                        .deptName(e.getDept() != null ? e.getDept().getDeptName() : null)
                        .gradeName(e.getGrade() != null ? e.getGrade().getGradeName() : null)
                        .titleName(e.getTitle() != null ? e.getTitle().getTitleName() : null)
                        .empRole(e.getEmpRole() != null ? e.getEmpRole().name() : null)
                        .build())
                .toList();

        return AttendanceModifyHrMemberResDto.builder()
                .hrMembers(mapped)
                .build();
    }
}