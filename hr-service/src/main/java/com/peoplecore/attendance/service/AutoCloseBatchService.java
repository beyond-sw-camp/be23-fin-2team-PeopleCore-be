package com.peoplecore.attendance.service;

import com.peoplecore.alarm.publisher.HrAlarmPublisher;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.event.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/*
 * 자동마감 + 결근 처리 Service — Spring Batch Step 의 ItemWriter 가 단위 호출.
 *
 * 단위 처리 메서드 (REQUIRES_NEW 격리 — 한 사원 실패가 청크 전체 롤백 차단):
 *  - closeOne(CommuteRecord, companyId, closedAt) : 자동마감 1건
 *  - markAbsentOne(Employee, companyId, targetDate) : 결근 1건
 *
 * 멀티 인스턴스 fire 차단: Quartz JDBC 클러스터링 (QRTZ_LOCKS row lock) 이 보장.
 * 회사·WorkGroup·날짜 단위 멱등 차단: Spring Batch JobInstance UNIQUE 제약 (JobConfig 에서 보장).
 *
 * 가드/분기 위치 (Reader 로 이동):
 *  - WorkGroup 미존재/삭제 → autoClose/absent Reader 가 빈 List 반환
 *  - 소정근무요일 + 공휴일 가드 → absentReader 안에서 처리
 *  - 휴가자 제외 → absentReader 안에서 처리
 */
@Slf4j
@Service
public class AutoCloseBatchService {

    private final CommuteRecordRepository commuteRecordRepository;
    private final HrAlarmPublisher hrAlarmPublisher;

    @Autowired
    public AutoCloseBatchService(CommuteRecordRepository commuteRecordRepository,
                                 HrAlarmPublisher hrAlarmPublisher) {
        this.commuteRecordRepository = commuteRecordRepository;
        this.hrAlarmPublisher = hrAlarmPublisher;
    }

    /*
     * 자동마감 1건 처리 — Spring Batch Writer 가 사원당 호출.
     * native UPDATE 의 가드(체크인 + 미체크아웃) 위반 시 affected=0 → skip 로그 후 정상 종료.
     * 예외: applyAutoClose 자체 실패 → 상위 전파 (Spring Batch skip 한도 적용)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeOne(CommuteRecord record, UUID companyId, LocalDateTime closedAt) {
        int affected = commuteRecordRepository.applyAutoClose(
                record.getComRecId(), record.getWorkDate(), closedAt);
        if (affected != 1) {
            // 가드 위반 또는 다른 노드/수동 트리거가 이미 처리 — 정상 흐름
            log.warn("[AutoClose] 마감 스킵 - comRecId={}, affected={} (가드 위반/이미 처리됨)",
                    record.getComRecId(), affected);
            return;
        }
        Employee emp = record.getEmployee();
        log.info("[AutoClose] 자동마감 - comRecId={}, empId={}, workDate={}",
                record.getComRecId(), emp.getEmpId(), record.getWorkDate());
        publishAutoCloseAlarm(companyId, emp, record.getWorkDate(), record.getComRecId());
    }

    /*
     * 결근 1건 처리 — Spring Batch Writer 가 사원당 호출.
     * 가드 (소정근무요일/공휴일/휴가자) 는 absentReader 가 사전 차단하므로 여기선 무조건 INSERT.
     * 예외: save 실패 → 상위 전파 (Spring Batch skip 한도 적용)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAbsentOne(Employee emp, UUID companyId, LocalDate targetDate) {
        CommuteRecord saved = commuteRecordRepository.save(
                CommuteRecord.absent(emp, targetDate, companyId));
        log.info("[AutoClose] 결근 삽입 - comRecId={}, empId={}, empName={}, workDate={}",
                saved.getComRecId(), emp.getEmpId(), emp.getEmpName(), targetDate);
        publishAbsentAlarm(companyId, emp, targetDate, saved.getComRecId());
    }

    /* 자동마감 알림 — 본인에게만 발송. refId 는 CommuteRecord PK (codebase 알림 컨벤션) */
    private void publishAutoCloseAlarm(UUID companyId, Employee emp, LocalDate targetDate,
                                       Long comRecId) {
        hrAlarmPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .alarmType("ATTENDANCE")
                .alarmTitle("퇴근 미체크로 자동 마감 처리되었습니다.")
                .alarmContent(emp.getEmpName() + " 사원의 " + targetDate
                        + " 근무 기록이 자동 마감되었습니다. 근태 정정 신청이 필요합니다.")
                .alarmLink("/attendance?date=" + targetDate + "&empId=" + emp.getEmpId())
                .alarmRefType("COMMUTE_AUTO_CLOSED")
                .alarmRefId(comRecId)
                .empIds(List.of(emp.getEmpId()))
                .build());
    }

    /* 결근 알림 — 본인에게만 발송. refId 는 CommuteRecord PK (codebase 알림 컨벤션) */
    private void publishAbsentAlarm(UUID companyId, Employee emp, LocalDate targetDate,
                                    Long comRecId) {
        hrAlarmPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .alarmType("ATTENDANCE")
                .alarmTitle("결근 처리 안내")
                .alarmContent(emp.getEmpName() + " 사원이 " + targetDate
                        + " 근무 예정일에 출근 기록이 없어 결근 처리되었습니다.")
                .alarmLink("/attendance?date=" + targetDate + "&empId=" + emp.getEmpId())
                .alarmRefType("COMMUTE_ABSENT")
                .alarmRefId(comRecId)
                .empIds(List.of(emp.getEmpId()))
                .build());
    }
}
