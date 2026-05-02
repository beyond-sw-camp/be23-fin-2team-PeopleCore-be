package com.peoplecore.attendance.service;

import com.peoplecore.alarm.publisher.HrAlarmPublisher;
import com.peoplecore.attendance.dto.OvertimeRemainingResDto;
import com.peoplecore.attendance.dto.OvertimeWeekHistoryResDto;
import com.peoplecore.attendance.entity.OtExceedAction;
import com.peoplecore.attendance.entity.OtStatus;
import com.peoplecore.attendance.entity.OvertimePolicy;
import com.peoplecore.attendance.entity.OvertimeRequest;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.publisher.OvertimeRequestRejectedByHrPublisher;
import com.peoplecore.attendance.repository.OvertimeRequestRepository;
import com.peoplecore.attendance.repository.OverTimePolicyRepository;
import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.event.OvertimeApprovalDocCreatedEvent;
import com.peoplecore.event.OvertimeApprovalResultEvent;
import com.peoplecore.event.OvertimeRequestRejectedByHrEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class OvertimeRequestService {

    /**
     * 정책 미존재 회사 fallback (분 단위, 52h = 3120)
     */
    private static final int DEFAULT_WEEKLY_MAX_MINUTE = 3120;

    private final OvertimeRequestRepository overtimeRequestRepository;
    private final OverTimePolicyRepository overtimePolicyRepository;
    private final EmployeeRepository employeeRepository;
    private final CommuteService commuteService;
    private final HrAlarmPublisher hrAlarmPublisher;
    private final OvertimeLimitChecker overtimeLimitChecker;
    private final OvertimeRequestRejectedByHrPublisher rejectedPublisher;

    @Autowired
    public OvertimeRequestService(OvertimeRequestRepository overtimeRequestRepository,
                                  OverTimePolicyRepository overtimePolicyRepository,
                                  EmployeeRepository employeeRepository,
                                  CommuteService commuteService,
                                  HrAlarmPublisher hrAlarmPublisher,
                                  OvertimeLimitChecker overtimeLimitChecker,
                                  OvertimeRequestRejectedByHrPublisher rejectedPublisher) {
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.overtimePolicyRepository = overtimePolicyRepository;
        this.employeeRepository = employeeRepository;
        this.commuteService = commuteService;
        this.hrAlarmPublisher = hrAlarmPublisher;
        this.overtimeLimitChecker = overtimeLimitChecker;
        this.rejectedPublisher = rejectedPublisher;
    }

    /**
     * 모달 진입 시 잔여 시간 조회
     */
    @Transactional(readOnly = true)
    public OvertimeRemainingResDto getRemaining(UUID companyId, Long empId, LocalDate weekStart) {
        // 정책 조회 (없으면 52h / NOTIFY)
        OvertimePolicy policy = overtimePolicyRepository.findByCompany_CompanyId(companyId).orElse(null);
        int weeklyMaxMinutes = (policy != null) ? policy.getOtPolicyWeeklyMaxMinutes() : DEFAULT_WEEKLY_MAX_MINUTE;
        OtExceedAction action = (policy != null) ? policy.getOtExceedAction() : OtExceedAction.NOTIFY;

        // 사원 + workGroup 기반 주간 기본 근로 분 계산
        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        WorkGroup wg = employee.getWorkGroup();
        int baseWorkMinutes = calcBaseWorkMinutes(wg);
        int maxOtBuffer = Math.max(0, weeklyMaxMinutes - baseWorkMinutes);

        // workGroup 미배정/필드 결측 시 안전한 기본값 APPROVAL (결재 필요)
        WorkGroup.GroupOvertimeRecognize recognizeType =
                (wg != null && wg.getGroupOvertimeRecognize() != null)
                        ? wg.getGroupOvertimeRecognize()
                        : WorkGroup.GroupOvertimeRecognize.APPROVAL;
        boolean approvalRequired = recognizeType == WorkGroup.GroupOvertimeRecognize.APPROVAL;

        // weekStart 정규화 후 월~일 범위
        LocalDate monday = weekStart.with(DayOfWeek.MONDAY);
        LocalDateTime weekStartAt = monday.atStartOfDay();
        LocalDateTime weekEndAt = monday.plusDays(6).atTime(LocalTime.MAX);

        // 이미 신청된 PENDING+APPROVED 합계
        Long used = overtimeRequestRepository.sumPendingApprovedMinutesInWeek(empId, weekStartAt, weekEndAt);
        long usedMin = (used != null) ? used : 0L;
        int remaining = (int) Math.max(0L, maxOtBuffer - usedMin);

        return OvertimeRemainingResDto.builder()
                .weeklyMaxMinutes(weeklyMaxMinutes)
                .baseWorkMinutes(baseWorkMinutes)
                .maxOvertimeBufferMinutes(maxOtBuffer)
                .weekUsedMinutes(usedMin)
                .remainingMinutes(remaining)
                .exceedAction(action)
                .recognizeType(recognizeType)
                .approvalRequired(approvalRequired)
                .build();
    }

    /**
     * 주간 초과근무 이력 조회 — 모달 하단 이력 테이블용
     */
    @Transactional(readOnly = true)
    public OvertimeWeekHistoryResDto getWeekHistory(UUID companyId, Long empId, LocalDate weekStart) {
        LocalDate monday = weekStart.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        LocalDateTime weekStartAt = monday.atStartOfDay();
        LocalDateTime weekEndAt = sunday.atTime(LocalTime.MAX);

        var list = overtimeRequestRepository.findWeekHistoryByEmp(empId, weekStartAt, weekEndAt);

        var items = list.stream()
                .map(o -> OvertimeWeekHistoryResDto.Item.builder()
                        .otId(o.getOtId())
                        .otStatus(o.getOtStatus())
                        .otDate(o.getOtDate().toLocalDate())
                        .otPlanStart(o.getOtPlanStart())
                        .otPlanEnd(o.getOtPlanEnd())
                        .otPlanMinutes(Duration.between(o.getOtPlanStart(), o.getOtPlanEnd()).toMinutes())
                        .otReason(o.getOtReason())
                        .build())
                .toList();

        return OvertimeWeekHistoryResDto.builder()
                .weekStart(monday)
                .weekEnd(sunday)
                .items(items)
                .build();
    }

    /**
     * Kafka(docCreated) Consumer 진입 — 결재문서 상신 성공 시점에 OvertimeRequest insert.
     * BLOCK/NOTIFY 정책 검증:
     * - BLOCK: 프론트 선제 차단 가정. 우회 시 Consumer 도 insert 통과 + 경고 알림
     * - NOTIFY: 초과해도 통과 + 관리자/최종결재자 알림
     * 중복 수신 방어: (companyId, approvalDocId) 기존에 이미 있으면 no-op
     */
    public void createFromApproval(OvertimeApprovalDocCreatedEvent event) {
        // 중복 insert 가드 (Kafka at-least-once 대비)
        var existing = overtimeRequestRepository
                .findByCompanyIdAndApprovalDocId(event.getCompanyId(), event.getApprovalDocId());
        if (existing.isPresent()) {
            log.info("[OvertimeRequest] docCreated 중복 수신 — 기존 otId={}, docId={}",
                    existing.get().getOtId(), event.getApprovalDocId());
            return;
        }

        // 사원 조회 (FK)
        Employee employee = employeeRepository.findById(event.getEmpId())
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // ALL 그룹 사원은 결재 불필요 — 프론트 우회로 들어온 케이스. INSERT 는 통과시키되 경고 로그
        WorkGroup wg = employee.getWorkGroup();
        if (wg != null && wg.getGroupOvertimeRecognize() == WorkGroup.GroupOvertimeRecognize.ALL) {
            log.warn("[OvertimeRequest] ALL 그룹 사원의 결재 상신 감지 - empId={}, docId={}, workGroupId={}",
                    employee.getEmpId(), event.getApprovalDocId(), wg.getWorkGroupId());
        }

        /* 주간 한도 검증 - INSERT 전 선행. BLOCK + 초과 시 역방향 반려 (우회 차단) */
        OvertimeLimitChecker.WeeklyUsage usage = overtimeLimitChecker.usageWithNewOt(
                event.getCompanyId(), event.getEmpId(),
                event.getOtDate().toLocalDate(),
                event.getOtPlanStart(), event.getOtPlanEnd());
        if (usage.isExceeded() && usage.exceedAction() == OtExceedAction.BLOCK) {
            publishRejection(event,
                    "주간 최대 근무시간(" + (usage.weeklyMaxMinutes() / 60) + "h)을 초과합니다.");
            return;
        }

        // PENDING 으로 insert
        OvertimeRequest entity = OvertimeRequest.builder()
                .companyId(event.getCompanyId())
                .employee(employee)
                .otDate(event.getOtDate())
                .otPlanStart(event.getOtPlanStart())
                .otPlanEnd(event.getOtPlanEnd())
                .otReason(event.getOtReason())
                .otStatus(OtStatus.PENDING)
                .approvalDocId(event.getApprovalDocId())
                .build();
        OvertimeRequest saved = overtimeRequestRepository.save(entity);

        log.info("[OvertimeRequest] docCreated → insert - otId={}, docId={}, empId={}",
                saved.getOtId(), saved.getApprovalDocId(), employee.getEmpId());

        // NOTIFY + 초과면 관리자/최종결재자 알림
        if (usage.isExceeded()) {
            checkExceedAndNotify(event, employee, saved);
        }
    }

    /* 역방향 반려 이벤트 발행 - BLOCK 한도 초과 시 */
    private void publishRejection(OvertimeApprovalDocCreatedEvent event, String reason) {
        rejectedPublisher.publish(OvertimeRequestRejectedByHrEvent.builder()
                .companyId(event.getCompanyId())
                .approvalDocId(event.getApprovalDocId())
                .rejectReason(reason)
                .build());
        log.info("[OvertimeRequest] 한도 초과 BLOCK → 역방향 반려 - docId={}", event.getApprovalDocId());
    }

    /**
     * 잔여 초과 시 NOTIFY 알림 발송 (BLOCK 도 우회 케이스 대비 동일 처리).
     * 대상 = HR_ADMIN/HR_SUPER_ADMIN + 최종 결재자 (중복 제거).
     */
    private void checkExceedAndNotify(OvertimeApprovalDocCreatedEvent event,
                                      Employee employee,
                                      OvertimeRequest saved) {
        OvertimePolicy policy = overtimePolicyRepository
                .findByCompany_CompanyId(event.getCompanyId()).orElse(null);
        if (policy == null) return;

        // 버퍼 계산
        int weeklyMaxMinutes = policy.getOtPolicyWeeklyMaxMinutes();
        int baseWorkMinutes = calcBaseWorkMinutes(employee.getWorkGroup());
        int maxOtBuffer = Math.max(0, weeklyMaxMinutes - baseWorkMinutes);

        // 이번주 PENDING+APPROVED 합계 (방금 insert 한 본 건도 포함됨 — PENDING)
        LocalDate monday = saved.getOtDate().toLocalDate().with(DayOfWeek.MONDAY);
        LocalDateTime weekStartAt = monday.atStartOfDay();
        LocalDateTime weekEndAt = monday.plusDays(6).atTime(LocalTime.MAX);
        Long used = overtimeRequestRepository
                .sumPendingApprovedMinutesInWeek(employee.getEmpId(), weekStartAt, weekEndAt);
        long usedMin = (used != null) ? used : 0L;
        if (usedMin <= maxOtBuffer) return;

        // 초과 확인 — 대상자 수집 (HR 관리자 + 최종 결재자, distinct)
        List<Employee> hrAdmins = employeeRepository.findByCompany_CompanyIdAndEmpRoleIn(
                event.getCompanyId(), List.of(EmpRole.HR_ADMIN, EmpRole.HR_SUPER_ADMIN));
        List<Long> recipients = new ArrayList<>(hrAdmins.stream().map(Employee::getEmpId).toList());
        if (event.getFinalApproverEmpId() != null && !recipients.contains(event.getFinalApproverEmpId())) {
            recipients.add(event.getFinalApproverEmpId());
        }
        if (recipients.isEmpty()) return;

        // 알람 페이로드 — alarmType=ATTENDANCE, refId=otId
        AlarmEvent alarm = AlarmEvent.builder()
                .companyId(event.getCompanyId())
                .empIds(recipients)
                .alarmType("ATTENDANCE")
                .alarmTitle(employee.getEmpName() + " 사원의 주간 최대 근무시간 초과 신청")
                .alarmContent("주간 누적 " + (usedMin / 60) + "h " + (usedMin % 60) + "m / 한도 "
                        + (maxOtBuffer / 60) + "h " + (maxOtBuffer % 60) + "m")
                .alarmLink("/attendance/admin")
                .alarmRefType("OVERTIME_REQUEST")
                .alarmRefId(saved.getOtId())
                .build();
        hrAlarmPublisher.publisher(alarm);
        log.info("[OvertimeRequest] NOTIFY 알림 발행 - otId={}, recipients={}",
                saved.getOtId(), recipients.size());
    }

    /**
     * Kafka(approvalResult) Consumer 진입 — 결재 결과 캐시 적용 + CommuteRecord 분 컬럼 재계산.
     * 재계산 트리거:
     *  - 새 상태가 APPROVED → 인정구간 신규 반영
     *  - 이전 상태가 APPROVED 였으나 REJECTED/CANCELED 로 이탈 → 잔존 recognized_* 정리
     * applyApprovedRecognition 은 해당 일자 APPROVED OT 만 재조회해 분 컬럼을 다시 산출하므로
     * 이탈 시점에는 이 OT 가 빠진 결과로 자동 정리됨.
     */
    public void applyApprovalResult(OvertimeApprovalResultEvent event) {
        // 회사 + otId 라우팅 검증 조회 — docId 기준으로도 찾을 수 있으나 otId 우선
        OvertimeRequest req = (event.getApprovalDocId() != null
                ? overtimeRequestRepository.findByCompanyIdAndApprovalDocId(
                event.getCompanyId(), event.getApprovalDocId())
                : overtimeRequestRepository.findByCompanyIdAndOtId(
                event.getCompanyId(), event.getOtId()))
                .orElseThrow(() -> new CustomException(ErrorCode.OVERTIME_REQUEST_NOT_FOUND));

        OtStatus newStatus = OtStatus.valueOf(event.getStatus());
        // 상태 갱신 전 이전 상태 캡쳐 — APPROVED 이탈 검출용
        OtStatus previousStatus = req.getOtStatus();

        // manager 는 REJECTED/APPROVED 에서만 의미 있음. CANCELED/회수에서 null 허용
        Employee manager = (event.getManagerId() != null)
                ? employeeRepository.findById(event.getManagerId()).orElse(null)
                : null;

        req.applyApprovalResult(newStatus, manager);

        // APPROVED 진입 또는 APPROVED 이탈 케이스 모두 재계산 필요
        boolean needsRecalc = (newStatus == OtStatus.APPROVED)
                || (previousStatus == OtStatus.APPROVED && newStatus != OtStatus.APPROVED);
        if (needsRecalc) {
            commuteService.recalcPayrollMinutes(
                    event.getCompanyId(),
                    req.getEmployee().getEmpId(),
                    req.getOtDate().toLocalDate());
        }
        log.info("[OvertimeRequest] 결재 결과 반영 - otId={}, prev={}, new={}, recalc={}",
                req.getOtId(), previousStatus, newStatus, needsRecalc);
    }

    /**
     * 사원 근무그룹 기반 주간 기본 근로 분.
     * daily = (groupEnd - groupStart) - (breakEnd - breakStart)
     * workDays = bitCount(groupWorkDay)
     * return daily × workDays
     * workGroup 미배정/필드 결측 시 0
     */
    private int calcBaseWorkMinutes(WorkGroup wg) {
        if (wg == null
                || wg.getGroupStartTime() == null || wg.getGroupEndTime() == null
                || wg.getGroupWorkDay() == null) {
            return 0;
        }
        long dayWork = Duration.between(wg.getGroupStartTime(), wg.getGroupEndTime()).toMinutes();
        long breakMin = (wg.getGroupBreakStart() != null && wg.getGroupBreakEnd() != null)
                ? Duration.between(wg.getGroupBreakStart(), wg.getGroupBreakEnd()).toMinutes()
                : 0L;
        long dailyEffective = Math.max(0L, dayWork - breakMin);
        int workDays = Integer.bitCount(wg.getGroupWorkDay());
        return (int) (dailyEffective * workDays);
    }
}
