package com.peoplecore.attendance.service;

import com.peoplecore.alarm.publisher.HrAlarmPublisher;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
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
 * 자동마감 + 결근 배치 서비스 — 근무그룹별 스케줄러가 호출.
 *
 * 자동마감 동작:
 *  1. 어제자(workDate = today - 1) 미체크아웃 레코드 조회
 *  2. markAutoClosed() — checkOut = groupEndTime, workStatus = AUTO_CLOSED
 *  3. 대상 사원 + HR 관리자에게 알림 발송
 *
 * 결근 처리 동작 (소정근무요일인 경우만):
 *  1. CommuteRecord 없는 소속 사원 조회 (findAbsentTargets)
 *  2. ABSENT 상태 CommuteRecord INSERT
 *  3. 대상 사원 + HR 관리자에게 알림 발송
 *
 * 멀티 인스턴스 분산 락: 같은 workGroupId + targetDate 조합에 한 번만 실행
 */
@Service
@Slf4j
public class AutoCloseBatchService {

    /* 분산 락 TTL — 배치 실행 시간 여유 + 같은 날 중복 방지 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final MyAttendanceQueryRepository myAttendanceQueryRepository;
    private final WorkGroupRepository workGroupRepository;
    private final EmployeeRepository employeeRepository;
    private final CommuteRecordRepository commuteRecordRepository;
    private final StringRedisTemplate redisTemplate;
    private final HrAlarmPublisher hrAlarmPublisher;

    @Autowired
    public AutoCloseBatchService(MyAttendanceQueryRepository myAttendanceQueryRepository,
                                 WorkGroupRepository workGroupRepository,
                                 EmployeeRepository employeeRepository,
                                 CommuteRecordRepository commuteRecordRepository,
                                 StringRedisTemplate redisTemplate,
                                 HrAlarmPublisher hrAlarmPublisher) {
        this.myAttendanceQueryRepository = myAttendanceQueryRepository;
        this.workGroupRepository = workGroupRepository;
        this.employeeRepository = employeeRepository;
        this.commuteRecordRepository = commuteRecordRepository;
        this.redisTemplate = redisTemplate;
        this.hrAlarmPublisher = hrAlarmPublisher;
    }

    /*
     * 특정 근무그룹에 대해 어제자 자동마감 + 결근 처리 수행.
     * 스케줄러가 근무그룹 시작시간 -2h 에 호출.
     */
    @Transactional
    public void autoCloseForWorkGroup(Long workGroupId) {

        LocalDate targetDate = LocalDate.now().minusDays(1);

        /* 분산락 획득 — 멀티 인스턴스 중 한 곳만 진입 */
        String lockKey = buildLockKey(workGroupId, targetDate);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[AutoClose] 다른 인스턴스가 처리 중 — skip. workGroupId={}, targetDate={}",
                    workGroupId, targetDate);
            return;
        }
        log.debug("[AutoClose] 락 획득 — key={}", lockKey);

        /* 근무 그룹 로드 */
        WorkGroup wg = workGroupRepository.findById(workGroupId).orElse(null);
        if (wg == null || wg.getGroupDeleteAt() != null) {
            log.info("[AutoClose] 근무그룹 미존재/삭제 — workGroupId={}", workGroupId);
            return;
        }

        UUID companyId = wg.getCompany().getCompanyId();

        /* HR 관리자 empId 사전 조회 */
        List<Long> hrAdminEmpIds = employeeRepository
                .findByCompany_CompanyIdAndEmpRoleIn(companyId, List.of(EmpRole.HR_ADMIN, EmpRole.HR_SUPER_ADMIN))
                .stream().map(Employee::getEmpId).toList();

        /* ── 1. 자동마감 처리 ── */
        processAutoClose(companyId, workGroupId, wg, targetDate, hrAdminEmpIds);

        /* ── 2. 결근 처리 (소정근무요일인 경우만) ── */
        int bit = 1 << (targetDate.getDayOfWeek().getValue() - 1);
        if ((wg.getGroupWorkDay() & bit) != 0) {
            processAbsent(companyId, workGroupId, targetDate, hrAdminEmpIds);
        }
    }

    /* 미체크아웃 레코드 자동마감 */
    private void processAutoClose(UUID companyId, Long workGroupId, WorkGroup wg,
                                  LocalDate targetDate, List<Long> hrAdminEmpIds) {
        List<CommuteRecord> targets = myAttendanceQueryRepository
                .findAutoCloseTargets(companyId, workGroupId, targetDate);

        if (targets.isEmpty()) {
            log.debug("[AutoClose] 자동마감 대상 없음 — workGroupId={}, targetDate={}", workGroupId, targetDate);
            return;
        }

        /* 강제 마감 시각 = 근무그룹 종료시각. 야간조도 workDate 유지 */
        LocalDateTime closedAt = targetDate.atTime(wg.getGroupEndTime());

        int success = 0;
        for (CommuteRecord record : targets) {
            try {
                record.markAutoClosed(closedAt);
                Employee emp = record.getEmployee();
                log.info("[AutoClose] 자동마감 — comRecId={}, empId={}, workDate={}",
                        record.getComRecId(), emp.getEmpId(), targetDate);
                publishAutoCloseAlarm(companyId, emp, targetDate, hrAdminEmpIds);
                success++;
            } catch (IllegalStateException e) {
                /* markAutoClosed 내부 가드 위반(중복 체크아웃 등) — 로그만 남기고 계속 */
                log.warn("[AutoClose] 마감 실패 — comRecId={}, reason={}",
                        record.getComRecId(), e.getMessage());
            }
        }
        log.info("[AutoClose] 자동마감 완료 — workGroupId={}, targetDate={}, success={}/{}",
                workGroupId, targetDate, success, targets.size());
    }

    /* CommuteRecord 없는 소속 사원에게 ABSENT 레코드 INSERT */
    private void processAbsent(UUID companyId, Long workGroupId,
                               LocalDate targetDate, List<Long> hrAdminEmpIds) {
        List<Employee> absentTargets = myAttendanceQueryRepository
                .findAbsentTargets(companyId, workGroupId, targetDate);

        if (absentTargets.isEmpty()) {
            log.debug("[AutoClose] 결근 대상 없음 — workGroupId={}, targetDate={}", workGroupId, targetDate);
            return;
        }

        for (Employee emp : absentTargets) {
            commuteRecordRepository.save(CommuteRecord.absent(emp, targetDate, companyId));
            log.info("[AutoClose] 결근 삽입 — empId={}, empName={}, workDate={}",
                    emp.getEmpId(), emp.getEmpName(), targetDate);
            publishAbsentAlarm(companyId, emp, targetDate, hrAdminEmpIds);
        }
        log.info("[AutoClose] 결근 처리 완료 — workGroupId={}, targetDate={}, count={}",
                workGroupId, targetDate, absentTargets.size());
    }

    /* 자동마감 알림 — 본인 + HR 관리자 */
    private void publishAutoCloseAlarm(UUID companyId, Employee emp, LocalDate targetDate,
                                       List<Long> hrAdminEmpIds) {
        Set<Long> recipientSet = new HashSet<>(hrAdminEmpIds);
        recipientSet.add(emp.getEmpId());

        hrAlarmPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .alarmType("ATTENDANCE")
                .alarmTitle("퇴근 미체크로 자동 마감 처리")
                .alarmContent(emp.getEmpName() + " 사원의 " + targetDate
                        + " 근무 기록이 자동 마감되었습니다. 근태 정정 신청이 필요합니다.")
                .alarmLink("/attendance")
                .alarmRefType("COMMUTE_AUTO_CLOSED")
                .alarmRefId(emp.getEmpId())
                .empIds(new ArrayList<>(recipientSet))
                .build());
    }

    /* 결근 알림 — 본인 + HR 관리자 */
    private void publishAbsentAlarm(UUID companyId, Employee emp, LocalDate targetDate,
                                    List<Long> hrAdminEmpIds) {
        Set<Long> recipientSet = new HashSet<>(hrAdminEmpIds);
        recipientSet.add(emp.getEmpId());

        hrAlarmPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .alarmType("ATTENDANCE")
                .alarmTitle("결근 처리 안내")
                .alarmContent(emp.getEmpName() + " 사원이 " + targetDate
                        + " 근무 예정일에 출근 기록이 없어 결근 처리되었습니다.")
                .alarmLink("/attendance")
                .alarmRefType("COMMUTE_ABSENT")
                .alarmRefId(emp.getEmpId())
                .empIds(new ArrayList<>(recipientSet))
                .build());
    }

    /* 락키 규격: auto-close:wg:{workGroupId}:date:{yyyy-MM-dd} */
    private String buildLockKey(Long workGroupId, LocalDate targetDate) {
        return String.format("auto-close:wg:%d:date:%s", workGroupId, targetDate);
    }
}
