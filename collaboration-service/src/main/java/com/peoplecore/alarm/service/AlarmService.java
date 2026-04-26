package com.peoplecore.alarm.service;


import com.peoplecore.alarm.dto.AlarmListResponseDto;
import com.peoplecore.alarm.repository.CommonAlarmRepository;
import com.peoplecore.alarm.sse.AlarmSseEmitterManager;
import com.peoplecore.entity.CommonAlarm;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@Transactional(readOnly = true)
public class AlarmService {
    private final CommonAlarmRepository alarmRepository;
    private final AlarmSseEmitterManager sseEmitterManager;

    @Autowired
    public AlarmService(CommonAlarmRepository alarmRepository, AlarmSseEmitterManager sseEmitterManager) {
        this.alarmRepository = alarmRepository;
        this.sseEmitterManager = sseEmitterManager;
    }

    /* persist consumer 전용: DB 저장만. 같은 group 내 1 pod 만 실행되어 중복 insert 방지 */
    @Transactional
    public void persist(AlarmEvent event) {
        List<CommonAlarm> alarms = event.getEmpIds().stream()
                .map(empId -> CommonAlarm.builder()
                        .companyId(event.getCompanyId())
                        .alarmEmpId(empId)
                        .alarmType(event.getAlarmType())
                        .alarmTitle(event.getAlarmTitle())
                        .alarmContent(event.getAlarmContent())
                        .alarmLink(event.getAlarmLink())
                        .alarmRefType(event.getAlarmRefType())
                        .alarmRefId(event.getAlarmRefId())
                        .build())
                .toList();
        alarmRepository.saveAll(alarms);
    }

    /* push consumer 전용: pod-local emitterMap 에만 push. DB 안 건드림.
       alarmId/createdAt 은 DB 거치지 않으므로 트랜잭션과 무관한 화면 표시용 값으로 채움 */
    public void push(AlarmEvent event) {
        LocalDateTime now = LocalDateTime.now();
        for (Long empId : event.getEmpIds()) {
            AlarmListResponseDto dto = AlarmListResponseDto.builder()
                    .alarmId(null)                       // push 시점엔 PK 모름
                    .alarmType(event.getAlarmType())
                    .alarmTitle(event.getAlarmTitle())
                    .alarmContent(event.getAlarmContent())
                    .alarmLink(event.getAlarmLink())
                    .alarmRefType(event.getAlarmRefType())
                    .alarmRefId(event.getAlarmRefId())
                    .alarmIsRead(false)                  // 방금 도착
                    .createdAt(now)
                    .build();
            sseEmitterManager.send(empId, dto);          // 이 pod 의 emitterMap 에 없으면 그냥 통과
        }
    }

    /* 안 읽은 알림 개수 */
    public long getUnreadCount(UUID companyId, Long empId) {
        return alarmRepository.countByCompanyIdAndAlarmEmpIdAndAlarmIsReadFalse(companyId, empId);
    }

    /* 단건 읽음 처리 */
    @Transactional
    public void markAsRead(UUID companyId, Long empId, Long alarmId) {
        CommonAlarm alarm = alarmRepository.findById(alarmId).orElseThrow(() -> new BusinessException("알림을 찾을 수 없습니다. ", HttpStatus.NOT_FOUND));

        if (!alarm.getCompanyId().equals(companyId) || !alarm.getAlarmEmpId().equals(empId)) {
            throw new BusinessException("본인의 알림만 처리할 수 있습니다. ");
        }
        alarm.markAsRead();
    }

    /* 전체 읽음 처리 */
    @Transactional
    public void markAllAsRead(UUID companyId, Long empId) {
        alarmRepository.markAllAsRead(companyId, empId);
    }

    /* 단건 삭제 */
    @Transactional
    public void deleteAlarm(UUID companyId, Long empId, Long alarmId) {
        CommonAlarm alarm = alarmRepository.findById(alarmId).orElseThrow(() -> new BusinessException("알림을 찾을 수 없습니다. ", HttpStatus.NOT_FOUND));
        if (!alarm.getCompanyId().equals(companyId) || !alarm.getAlarmEmpId().equals(empId)) {
            throw new BusinessException("본인의 알림만 삭제할 수 있습니다. ", HttpStatus.FORBIDDEN);
        }
        alarmRepository.delete(alarm);
    }

    /* 전체 삭제*/
    @Transactional
    public void deleteAll(UUID companyId, Long empId) {
        alarmRepository.deleteByCompanyIdAndAlarmEmpId(companyId, empId);
    }

    /* 알림 목록조회 */
    public Page<AlarmListResponseDto> getAlarms(UUID companyId, Long empId, String filter, Pageable pageable) {
        Page<CommonAlarm> page;
        if ("unread".equals(filter)) {
            page = alarmRepository.findByCompanyIdAndAlarmEmpIdAndAlarmIsReadFalseOrderByCreatedAtDesc(companyId, empId, pageable);
        } else {
            page = alarmRepository.findByCompanyIdAndAlarmEmpIdOrderByCreatedAtDesc(companyId, empId, pageable);
        }
        return page.map(AlarmListResponseDto::from);
    }
}
