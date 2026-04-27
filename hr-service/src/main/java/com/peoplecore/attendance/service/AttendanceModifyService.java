package com.peoplecore.attendance.service;

import com.peoplecore.alarm.publisher.HrAlarmPublisher;
import com.peoplecore.attendance.cache.ApprovalFormIdCache;
import com.peoplecore.attendance.dto.*;
import com.peoplecore.attendance.entity.AttendanceModify;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.HolidayReason;
import com.peoplecore.attendance.entity.ModifyStatus;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
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

    @Autowired
    public AttendanceModifyService(AttendanceModifyRepository attendanceModifyRepository,
                                   CommuteRecordRepository commuteRecordRepository,
                                   EmployeeRepository employeeRepository,
                                   ApprovalFormIdCache approvalFormIdCache,
                                   AttendanceModifyRejectedByHrPublisher rejectedPublisher,
                                   HrAlarmPublisher hrAlarmPublisher,
                                   VacationRequestQueryRepository vacationRequestQueryRepository) {
        this.attendanceModifyRepository = attendanceModifyRepository;
        this.commuteRecordRepository = commuteRecordRepository;
        this.employeeRepository = employeeRepository;
        this.approvalFormIdCache = approvalFormIdCache;
        this.rejectedPublisher = rejectedPublisher;
        this.hrAlarmPublisher = hrAlarmPublisher;
        this.vacationRequestQueryRepository = vacationRequestQueryRepository;
    }

    /* ===================== 1) 프리필 ===================== */

    @Transactional(readOnly = true)
    public AttendanceModifyPrefillResDto prefill(UUID companyId, Long empId, LocalDate workDate) {
        // 해당 날짜 CommuteRecord — (company, emp, workDate) unique, 파티션 프루닝
        CommuteRecord cr = commuteRecordRepository
                .findByCompanyIdAndEmployee_EmpIdAndWorkDate(companyId, empId, workDate)
                .orElseThrow(() -> new CustomException(ErrorCode.ATTENDANCE_RECORD_NOT_FOUND));

        // 동일 CommuteRecord 에 PENDING 신청이 이미 있으면 차단
        boolean pendingExists = attendanceModifyRepository
                .existsByEmployee_EmpIdAndComRecIdAndAttenStatus(empId, cr.getComRecId(), ModifyStatus.PENDING);
        if (pendingExists) {
            throw new CustomException(ErrorCode.ATTENDANCE_MODIFY_PENDING_EXISTS);
        }

        Employee emp = cr.getEmployee();
        Long formId = approvalFormIdCache.getAttendanceModifyFormId(companyId);

        return AttendanceModifyPrefillResDto.builder()
                .formId(formId)
                .formCode(ApprovalFormIdCache.FORM_CODE_ATTENDANCE_MODIFY)
                .comRecId(cr.getComRecId())
                .workDate(cr.getWorkDate())
                .currentCheckIn(cr.getComRecCheckIn())
                .currentCheckOut(cr.getComRecCheckOut())
                .isAutoClosed(cr.getWorkStatus() == WorkStatus.AUTO_CLOSED)
                .workStatus(cr.getWorkStatus())
                .workStatusLabel(cr.getWorkStatus() != null ? cr.getWorkStatus().getLabel() : null)
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .gradeName(emp.getGrade() != null ? emp.getGrade().getGradeName() : null)
                .titleName(emp.getTitle() != null ? emp.getTitle().getTitleName() : null)
                .build();
    }

    /*
     * collab 상신 이벤트 수신 → AttendanceModify INSERT.
     * 중복 PENDING 감지 시 역방향 이벤트 발행으로 collab 측 자동 반려 트리거.
     */
    public void createFromApproval(AttendanceModifyDocCreatedEvent event) {
        // 멱등성: 동일 docId 로 이미 INSERT 된 경우 no-op
        var existing = attendanceModifyRepository
                .findByCompanyIdAndApprovalDocId(event.getCompanyId(), event.getApprovalDocId());
        if (existing.isPresent()) {
            log.info("[AttendanceModify] docCreated 중복 수신 - docId={}", event.getApprovalDocId());
            return;
        }

        // PENDING 중복 체크 — 있으면 역방향 이벤트 발행 후 종료
        boolean pendingExists = attendanceModifyRepository
                .existsByEmployee_EmpIdAndComRecIdAndAttenStatus(
                        event.getEmpId(), event.getComRecId(), ModifyStatus.PENDING);
        if (pendingExists) {
            rejectedPublisher.publish(AttendanceModifyRejectedByHrEvent.builder()
                    .companyId(event.getCompanyId())
                    .approvalDocId(event.getApprovalDocId())
                    .rejectReason("동일 출퇴근 기록에 대한 정정 신청이 이미 진행 중입니다.")
                    .build());
            log.info("[AttendanceModify] 중복 PENDING → 역방향 반려 이벤트 발행 - docId={}",
                    event.getApprovalDocId());
            return;
        }

        // 사원 스냅샷 조회
        Employee emp = employeeRepository.findById(event.getEmpId())
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

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

        // HR 관리자 알림 — 근태 전용 (alarmRefType=ATTENDANCE_MODIFY)
        notifyHrAdmins(event.getCompanyId(), saved,
                emp.getEmpName() + " 사원의 근태 정정 신청",
                "신청이 접수되었습니다. 결재 진행을 확인해 주세요.");
    }

    /*
     * collab 결재 결과 이벤트 수신 → 상태 반영.
     * 승인 시 CommuteRecord native UPDATE (파티션 프루닝 보장).
     * 멱등성: 이미 전이된 상태면
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
                // CommuteRecord 갱신 — native UPDATE (com_rec_id + work_date WHERE)
                int updated = commuteRecordRepository.applyAttendanceModify(
                        am.getComRecId(), am.getWorkDate(),
                        am.getAttenReqCheckIn(), am.getAttenReqCheckOut());
                if (updated != 1) {
                    log.error("[AttendanceModify] CommuteRecord UPDATE 실패 - attenModiId={}, affected={}",
                            am.getAttenModiId(), updated);
                    throw new CustomException(ErrorCode.ATTENDANCE_MODIFY_APPLY_FAILED);
                }
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
     * CommuteRecord 없는 날도 빈 Day 로 포함 (토/일은 기본 WEEKLY_OFF).
     * 같은 날 출근 + 휴가(반차 등) 공존 가능 — 두 정보를 동시에 응답.
     */
    @Transactional(readOnly = true)
    public AttendanceModifyWeekResDto getWeek(UUID companyId, Long empId, LocalDate weekStartParam) {
        LocalDate monday = weekStartParam.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        // CommuteRecord 주간 조회 — (company_id, emp_id, work_date) 인덱스 + 파티션 프루닝
        List<CommuteRecord> records = commuteRecordRepository
                .findByCompanyIdAndEmployee_EmpIdAndWorkDateBetweenOrderByWorkDateDesc(
                        companyId, empId, monday, sunday,
                        org.springframework.data.domain.Pageable.unpaged())
                .getContent();
        Map<LocalDate, CommuteRecord> recordMap = new HashMap<>();
        for (CommuteRecord cr : records) recordMap.put(cr.getWorkDate(), cr);

        // 승인 휴가 슬라이스 — 일자별 매핑. 한 날짜에 여러 건이면 useDay 큰 쪽 우선 (종일 > 반차)
        List<VacationSlice> vacSlices = vacationRequestQueryRepository.findApprovedSlicesInWeek(
                companyId, empId, RequestStatus.APPROVED,
                monday.atStartOfDay(), sunday.atTime(LocalTime.MAX));
        Map<LocalDate, VacationSlice> vacationMap = new HashMap<>();
        for (VacationSlice s : vacSlices) {
            LocalDate from = s.startAt().toLocalDate();
            LocalDate to = s.endAt().toLocalDate();
            if (from.isBefore(monday)) from = monday;
            if (to.isAfter(sunday)) to = sunday;
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                VacationSlice prev = vacationMap.get(d);
                if (prev == null
                        || (s.useDay() != null
                            && (prev.useDay() == null
                                || s.useDay().compareTo(prev.useDay()) > 0))) {
                    vacationMap.put(d, s);
                }
            }
        }

        // 7일 슬롯 빌드
        List<AttendanceModifyWeekResDto.Day> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            DayOfWeek dow = date.getDayOfWeek();
            CommuteRecord cr = recordMap.get(date);
            VacationSlice vac = vacationMap.get(date);

            AttendanceModifyWeekResDto.Day.DayBuilder b = AttendanceModifyWeekResDto.Day.builder()
                    .workDate(date)
                    .dayOfWeek(dow);

            if (cr != null) {
                long overtime = cr.getOvertimeMinutes() != null ? cr.getOvertimeMinutes() : 0L;
                long recognized = cr.getRecognizedExtendedMinutes() != null
                        ? cr.getRecognizedExtendedMinutes() : 0L;
                long unrecognized = Math.max(0L, overtime - recognized);

                b.isHoliday(cr.getHolidayReason() != null)
                        .holidayReason(cr.getHolidayReason())
                        .comRecId(cr.getComRecId())
                        .checkIn(cr.getComRecCheckIn())
                        .checkOut(cr.getComRecCheckOut())
                        .actualWorkMinutes(cr.getActualWorkMinutes() != null
                                ? cr.getActualWorkMinutes() : 0L)
                        .recognizedOvertimeMinutes(recognized)
                        .unrecognizedOvertimeMinutes(unrecognized)
                        .workStatus(cr.getWorkStatus());
            } else {
                boolean isWeekend = (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);
                b.isHoliday(isWeekend)
                        .holidayReason(isWeekend ? HolidayReason.WEEKLY_OFF : null)
                        .comRecId(null)
                        .checkIn(null)
                        .checkOut(null)
                        .actualWorkMinutes(0L)
                        .recognizedOvertimeMinutes(0L)
                        .unrecognizedOvertimeMinutes(0L)
                        .workStatus(null);
            }

            if (vac != null) {
                b.isVacation(true)
                        .vacationTypeName(vac.typeName())
                        .vacationStart(vac.startAt())
                        .vacationEnd(vac.endAt())
                        .vacationUseDay(vac.useDay());
            } else {
                b.isVacation(false);
            }

            days.add(b.build());
        }

        return AttendanceModifyWeekResDto.builder()
                .weekStart(monday)
                .weekEnd(sunday)
                .days(days)
                .build();
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