package com.peoplecore.alram.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class AlarmSseEmitterManager {
    private final Map<Long, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    public SseEmitter connect(Long empId) {
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L);
        emitterMap.put(empId, emitter);

        emitter.onCompletion(() -> emitterMap.remove(empId));
        emitter.onTimeout(() -> emitterMap.remove(empId));
        emitter.onError(e -> emitterMap.remove(empId));

        /* 연결 직후 더미 이벤트 전송 ((503) 방지 */
        try {
            emitter.send(SseEmitter.event().name("connect").data("connected-Alarm"));
        } catch (IOException e) {
            emitterMap.remove(empId);
        }
        return emitter;
    }

    public void send(Long empId, Object data) {
        SseEmitter emitter = emitterMap.get(empId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("alarm").data(data));
            } catch (IOException e) {
                emitterMap.remove(empId);
            }
        }
    }
}
