package com.peoplecore.alram.service;


import com.peoplecore.alram.dto.AlarmListResponseDto;
import com.peoplecore.alram.repository.CommonAlarmRepository;
import com.peoplecore.alram.sse.AlarmSseEmitterManager;
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

    /*카프카 컨슈머에서 호출 -> db 저장 -> sse 푸시 */
    @Transactional
    public void createAndPush(AlarmEvent event) {
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

        /*sse 실시간 푸시 결재 라인에 많아봤자 3~4명이기 때문에 for문으로 해도 괜찮을거라 생각함 */
        for (CommonAlarm alarm : alarms) {
            sseEmitterManager.send(alarm.getAlarmEmpId(), AlarmListResponseDto.from(alarm));
        }
    }

    /*안 읽은 알림 개수 */
    public long getUnreadCount(UUID companyId, Long empId) {
        return alarmRepository.countByCompanyIdAndAlarmEmpIdAndAlarmIsReadFalse(companyId, empId);
    }

    /*단건 읽음 처리 */
    @Transactional
    public void markAsRead(UUID companyId, Long empId, Long alarmId) {
        CommonAlarm alarm = alarmRepository.findById(alarmId).orElseThrow(() -> new BusinessException("알림을 찾을 수 없습니다. ", HttpStatus.NOT_FOUND));

        if (!alarm.getCompanyId().equals(companyId) || !alarm.getAlarmEmpId().equals(empId)) {
            throw new BusinessException("본인의 알림만 처리할 수 있습니다. ");
        }
        alarm.markAsRead();
    }

    /*전체 읽음 처리 */
    @Transactional
    public void markAllAsRead(UUID companyId, Long empId) {
        alarmRepository.markAllAsRead(companyId, empId);
    }

    /*단건 삭제 */
    @Transactional
    public void deleteAlarm(UUID companyId, Long empId, Long alarmId) {
        CommonAlarm alarm = alarmRepository.findById(alarmId).orElseThrow(() -> new BusinessException("알림을 찾을 수 없습니다. ", HttpStatus.NOT_FOUND));
        if (!alarm.getCompanyId().equals(companyId) || !alarm.getAlarmEmpId().equals(empId)) {
            throw new BusinessException("본인의 알림만 삭제할 수 있습니다. ", HttpStatus.FORBIDDEN);
        }
        alarmRepository.delete(alarm);
    }

    /*전체 삭제*/
    @Transactional
    public void deleteAll(UUID companyId, Long empId) {
        alarmRepository.deleteByCompanyIdAndAlarmEmpId(companyId, empId);
    }

    /*알림 목록조회 */
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
