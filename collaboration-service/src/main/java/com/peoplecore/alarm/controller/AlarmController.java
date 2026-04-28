package com.peoplecore.alarm.controller;

import com.peoplecore.alarm.dto.AlarmListResponseDto;
import com.peoplecore.alarm.service.AlarmService;
import com.peoplecore.alarm.sse.AlarmSseEmitterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/alarm")
public class AlarmController {
    private final AlarmService alarmService;
    private final AlarmSseEmitterManager sseEmitterManager;

    @Autowired
    public AlarmController(AlarmService alarmService, AlarmSseEmitterManager sseEmitterManager) {
        this.alarmService = alarmService;
        this.sseEmitterManager = sseEmitterManager;
    }

    /**
     * 알림 목록 조회
     */
    @GetMapping
    public ResponseEntity<Page<AlarmListResponseDto>> getAlarms(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam(defaultValue = "all") String filter,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(alarmService.getAlarms(companyId, empId, filter, pageable));
    }

    /**
     * 안읽은 알림 개수
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(Map.of("count", alarmService.getUnreadCount(companyId, empId)));
    }

    /**
     * 단건 읽음 처리
     */
    @PatchMapping("/{alarmId}/read")
    public ResponseEntity<Void> markAsRead(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long alarmId) {
        alarmService.markAsRead(companyId, empId, alarmId);
        return ResponseEntity.ok().build();
    }

    /**
     * 전체 읽음 처리
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        alarmService.markAllAsRead(companyId, empId);
        return ResponseEntity.ok().build();
    }

    /**
     * 단건 삭제
     */
    @DeleteMapping("/{alarmId}")
    public ResponseEntity<Void> deleteAlarm(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long alarmId) {
        alarmService.deleteAlarm(companyId, empId, alarmId);
        return ResponseEntity.ok().build();
    }

    /**
     * 전체 삭제
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteAll(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        alarmService.deleteAll(companyId, empId);
        return ResponseEntity.ok().build();
    }

    /**
     * SSE 실시간 알림 스트림
     * produces = MediaType.TEXT_EVENT_STREAM_VALUE	:  응답의 contentType을 eventStream으로 변경
     * eventStream : 연결을 유지하면서 서버사 계속 데이터를 밀어넣는 sse 프로토콜
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader("X-User-Id") Long empId) {
        return sseEmitterManager.connect(empId);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<AlarmListResponseDto>> getRecent(@RequestHeader("X-User-Company") UUID companyId, @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(alarmService.getRecent(companyId, empId));
    }


}
