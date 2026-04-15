package com.peoplecore.attendance.service;

import com.peoplecore.alarm.publisher.HrAlarmPublisher;
import com.peoplecore.attendance.entity.CheckOutStatus;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.MyAttendanceQueryRepository;
import com.peoplecore.attendance.repository.WorkGroupRepository;
import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/*
 * 자동마감 배치 실행 서비스 — 근무그룹별 스케줄러가 호출.
 * 동작:
 *  1. 어제자(workDate = today - 1) 의 미체크아웃 레코드 조회
 *     (해당 근무그룹 소속 + checkIn 있음 + isAutoClosed 아님)
 *  2. 각 레코드에 markAutoClosed() 호출
 *     - checkOut 시각 = 근무그룹 종료시각 기준 LocalDateTime (workDate + groupEndTime)
 *     - actualWorkMinutes = 0, 모든 recognized_* = 0, isAutoClosed = true
 *  3. 대상 사원별 / 회사 HR 관리자에게 알림 발송 (TODO: 다음 단계에서 연결)
 *
 * 트랜잭션:
 *  - 근무그룹 단위로 @Transactional — 같은 그룹 N명은 한 트랜잭션
 *  - 한 건 실패해도 다른 건은 롤백 안 되도록 분리 처리 (REQUIRES_NEW 고려 여지)
 *
 *  멀티 인스턴스 분산 락
 * 같은 workGroupId에 targetDate에 대해 여러 인스턴스가 동시 실행되는 것 방지
 *  */
@Service
@Slf4j
public class AutoCloseBatchService {

    /**
     * 분산 락 TTL — 배치 실행 시간 여유 + 같은 날 중복 방지
     */
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final MyAttendanceQueryRepository myAttendanceQueryRepository;
    private final WorkGroupRepository workGroupRepository;
    private final EmployeeRepository employeeRepository;
    private final StringRedisTemplate redisTemplate;
    private final HrAlarmPublisher hrAlarmPublisher;

    @Autowired
    public AutoCloseBatchService(MyAttendanceQueryRepository myAttendanceQueryRepository,
                                 WorkGroupRepository workGroupRepository, EmployeeRepository employeeRepository, StringRedisTemplate redisTemplate, HrAlarmPublisher hrAlarmPublisher) {
        this.myAttendanceQueryRepository = myAttendanceQueryRepository;
        this.workGroupRepository = workGroupRepository;
        this.employeeRepository = employeeRepository;
        this.redisTemplate = redisTemplate;
        this.hrAlarmPublisher = hrAlarmPublisher;
    }

    /*
     * 특정 근무그룹에 대해 어제자 자동마감 수행.
     * 스케줄러가 근무그룹 시작시간 -2h 에 호출.
     */
    @Transactional
    public void autoCloseForWorkGroup(Long workGroupId) {

        LocalDate targetDate = LocalDate.now().minusDays(1);

        /*분산락 획득 멀티 인스턴스 중 한 곳만 진입 */
        String lockKey = buildLockKey(workGroupId, targetDate);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[AutoClose] 다른 인스턴스가 처리 중 — skip. workGroupId={}, targetDate={}",
                    workGroupId, targetDate);
            return;
        }
        log.debug("[AutoClose] 락 획득 — key={}", lockKey);


        /*근무 그룹 로드 */
        WorkGroup wg = workGroupRepository.findById(workGroupId).orElse(null);
        if (wg == null || wg.getGroupDeleteAt() != null) {
            log.info("[AutoClose] 근무그룹 미존재/삭제 — workGroupId={}", workGroupId);
            return;
        }

        /* 자동마감 대상 조회*/
        UUID companyId = wg.getCompany().getCompanyId();

        List<CommuteRecord> targets = myAttendanceQueryRepository
                .findAutoCloseTargets(companyId, workGroupId, targetDate);

        if (targets.isEmpty()) {
            log.debug("[AutoClose] 대상 없음 — workGroupId={}, targetDate={}", workGroupId, targetDate);
            return;
        }

        /* hr 관리자 empId 사전 조회 */
        List<Long> hrAdminEmpIds = employeeRepository.findByCompany_CompanyIdAndEmpRoleIn(companyId, List.of(EmpRole.HR_ADMIN, EmpRole.HR_SUPER_ADMIN)).stream().map(Employee::getEmpId).toList();


        // 강제 마감 시각 = 근무그룹 종료시각 기준 — 자정 넘어가는 야간조도 workDate 유지
        LocalDateTime closedAt = targetDate.atTime(wg.getGroupEndTime());

        /* 레코드별 마감 처리*/
        int success = 0;
        for (CommuteRecord record : targets) {
            try {
                record.markAutoClosed(closedAt, CheckOutStatus.ON_TIME);
                Employee emp = record.getEmployee();
                log.info("[AutoClose] 자동마감 — comRecId={}, empId={}, empName={}, workDate={}", record.getComRecId(), emp.getEmpId(), emp.getEmpName(), targetDate);
                publishAlarm(companyId, emp, targetDate, hrAdminEmpIds);
                success++;
            } catch (IllegalStateException e) {
                // markAutoClosed 내부 가드 위반 (중복 체크아웃 등) — 로그만 남기고 계속
                log.warn("[AutoClose] 마감 실패 — comRecId={}, reason={}",
                        record.getComRecId(), e.getMessage());
            }
        }

        log.info("[AutoClose] 완료 — workGroupId={}, targetDate={}, processed={}, total={}",
                workGroupId, targetDate, success, targets.size());
    }


    /*자동 마감 알림 발송 - 본인 + Hr 관리자
     *토픽 ->  kafka alram-event*/
    private void publishAlarm(UUID companyId, Employee emp, LocalDate targetDate, List<Long> hrAdminInEmpIds) {
        Set<Long> recipientSet = new HashSet<>(hrAdminInEmpIds);
        recipientSet.add(emp.getEmpId());
        List<Long> recipients = new ArrayList<>(recipientSet);

        AlarmEvent event = AlarmEvent.builder()
                .companyId(companyId)
                .alarmType("ATTENDANCE")
                .alarmTitle("퇴근 미체크로 자동 마감 처리")
                .alarmContent(emp.getEmpName() + " 사원의 " + targetDate + " 근무 기록이 자동 마감되었습니다. 근태 정정 신청이 필요합니다.")
                .alarmLink("/attendance")
                .alarmRefType("COMMUTE_AUTO_CLOSED")
                .alarmRefId(emp.getEmpId())
                .empIds(recipients)
                .build();
        hrAlarmPublisher.publisher(event);
    }


    /* 락키 규격 : auto-close:wg:{workGroupId}:date:{yyyy-MM-dd} */
    private String buildLockKey(Long workGroupId, LocalDate targetDate) {
        return String.format("auto-close:wg:%d:date:%s", workGroupId, targetDate);
    }
}