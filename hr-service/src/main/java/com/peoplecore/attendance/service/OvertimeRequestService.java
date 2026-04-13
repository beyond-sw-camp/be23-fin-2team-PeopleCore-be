package com.peoplecore.attendance.service;

import com.peoplecore.attendance.dto.OvertimeRemainingResDto;
import com.peoplecore.attendance.dto.OvertimeSubmitRequest;
import com.peoplecore.attendance.entity.OtExceedAction;
import com.peoplecore.attendance.entity.OtStatus;
import com.peoplecore.attendance.entity.OvertimePolicy;
import com.peoplecore.attendance.entity.OvertimeRequest;
import com.peoplecore.attendance.repository.OvertimeRequestRepository;
import com.peoplecore.attendance.repository.OverTimePolicyRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.OvertimeApprovalResultEvent;
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
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class OvertimeRequestService {

    /** 정책 미존재 회사 fallback (시간 단위) */
    private static final int DEFAULT_WEEKLY_MAX_HOUR = 52;

    private final OvertimeRequestRepository overtimeRequestRepository;
    private final OverTimePolicyRepository overtimePolicyRepository;
    private final EmployeeRepository employeeRepository;

    @Autowired
    public OvertimeRequestService(OvertimeRequestRepository overtimeRequestRepository,
                                  OverTimePolicyRepository overtimePolicyRepository,
                                  EmployeeRepository employeeRepository) {
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.overtimePolicyRepository = overtimePolicyRepository;
        this.employeeRepository = employeeRepository;
    }

    /** 모달 진입 시 잔여 시간 조회 */
    @Transactional(readOnly = true)
    public OvertimeRemainingResDto getRemaining(UUID companyId, Long empId, LocalDate weekStart) {

        // 정책 조회 (없으면 52h / NOTIFY)
        OvertimePolicy policy = overtimePolicyRepository.findByCompany_CompanyId(companyId).orElse(null);
        int maxHour = (policy != null) ? policy.getOtPolicyWeeklyMaxHour() : DEFAULT_WEEKLY_MAX_HOUR;
        OtExceedAction action = (policy != null) ? policy.getOtExceedAction() : OtExceedAction.NOTIFY;
        int maxMinutes = maxHour * 60;

        // weekStart 가 월요일 아닐 수 있어 정규화 후 월~일 범위 산정
        LocalDate monday = weekStart.with(DayOfWeek.MONDAY);
        LocalDateTime weekStartAt = monday.atStartOfDay();
        LocalDateTime weekEndAt = monday.plusDays(6).atTime(LocalTime.MAX);

        // 사원 주간 PENDING+APPROVED 합계
        Long used = overtimeRequestRepository
                .sumPendingApprovedMinutesInWeek(empId, weekStartAt, weekEndAt);
        long usedMin = (used != null) ? used : 0L;

        // 잔여 — 음수 보정
        int remaining = (int) Math.max(0L, maxMinutes - usedMin);

        return OvertimeRemainingResDto.builder()
                .weeklyMaxMinutes(maxMinutes)
                .weekUsedMinutes(usedMin)
                .remainingMinutes(remaining)
                .exceedAction(action)
                .build();
    }

    /** "확인" 클릭 → OvertimeRequest insert (PENDING) → otId 반환 */
    public Long submit(UUID companyId, Long empId, OvertimeSubmitRequest req) {

        // 시간 정합성 가드 — end > start 아니면 IllegalArgumentException
        if (!req.getOtPlanEnd().isAfter(req.getOtPlanStart())) {
            throw new IllegalArgumentException(
                    "otPlanEnd 가 otPlanStart 보다 같거나 이전 - start=" + req.getOtPlanStart()
                            + ", end=" + req.getOtPlanEnd());
        }

        // 사원 조회 (FK + 무결성)
        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // 정책 조회 + BLOCK 가드
        OvertimePolicy policy = overtimePolicyRepository.findByCompany_CompanyId(companyId).orElse(null);
        if (policy != null && policy.getOtExceedAction() == OtExceedAction.BLOCK) {
            int maxMinutes = policy.getOtPolicyWeeklyMaxHour() * 60;
            // 신청 날짜 기준 주간 누적 합계
            LocalDate monday = req.getOtDate().toLocalDate().with(DayOfWeek.MONDAY);
            LocalDateTime weekStartAt = monday.atStartOfDay();
            LocalDateTime weekEndAt = monday.plusDays(6).atTime(LocalTime.MAX);
            Long used = overtimeRequestRepository
                    .sumPendingApprovedMinutesInWeek(empId, weekStartAt, weekEndAt);
            long usedMin = (used != null) ? used : 0L;
            long thisMin = Duration.between(req.getOtPlanStart(), req.getOtPlanEnd()).toMinutes();
            // 잔여 < 신청 → 차단
            if (usedMin + thisMin > maxMinutes) {
                throw new CustomException(ErrorCode.OVERTIME_EXCEEDS_WEEKLY_MAX);
            }
        }

        // OvertimeRequest 빌드 + insert (PENDING, docId=null)
        OvertimeRequest entity = OvertimeRequest.builder()
                .companyId(companyId)
                .employee(employee)
                .otDate(req.getOtDate())
                .otPlanStart(req.getOtPlanStart())
                .otPlanEnd(req.getOtPlanEnd())
                .otReason(req.getOtReason())
                .otStatus(OtStatus.PENDING)
                .build();
        OvertimeRequest saved = overtimeRequestRepository.save(entity);

        log.info("[OvertimeRequest] 신청 생성 - otId={}, empId={}, plan={}~{}",
                saved.getOtId(), empId, req.getOtPlanStart(), req.getOtPlanEnd());
        return saved.getOtId();
    }

    /** Kafka(docCreated) Consumer 진입 — approvalDocId bind */
    public void bindApprovalDoc(UUID companyId, Long otId, Long docId) {
        OvertimeRequest req = overtimeRequestRepository
                .findByCompanyIdAndOtId(companyId, otId)
                .orElseThrow(() -> new CustomException(ErrorCode.OVERTIME_REQUEST_NOT_FOUND));
        req.bindApprovalDoc(docId);
        log.info("[OvertimeRequest] docId bind - otId={}, docId={}", otId, docId);
    }

    /** Kafka(approvalResult) Consumer 진입 — 결재 결과 캐시 적용 */
    public void applyApprovalResult(OvertimeApprovalResultEvent event) {

        // 회사 + otId 라우팅 검증 조회
        OvertimeRequest req = overtimeRequestRepository
                .findByCompanyIdAndOtId(event.getCompanyId(), event.getOtId())
                .orElseThrow(() -> new CustomException(ErrorCode.OVERTIME_REQUEST_NOT_FOUND));

        // status enum 변환 (실패 시 IllegalArgumentException → consumer retry)
        OtStatus newStatus = OtStatus.valueOf(event.getStatus());

        // 최종 승인자 사원 조회
        Employee manager = employeeRepository.findById(event.getManagerId())
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // 도메인 메서드로 결재 결과 적용
        req.applyApprovalResult(newStatus, manager);

        // docId 보완
        if (req.getApprovalDocId() == null && event.getApprovalDocId() != null) {
            req.bindApprovalDoc(event.getApprovalDocId());
        }
        log.info("[OvertimeRequest] 결재 결과 반영 - otId={}, status={}", req.getOtId(), newStatus);
    }
}
