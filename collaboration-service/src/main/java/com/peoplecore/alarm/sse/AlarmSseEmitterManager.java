package com.peoplecore.alarm.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class AlarmSseEmitterManager {
    /* 한 사원이 여러 탭/디바이스에서 동시 SSE 연결 가능하므로 emitter list 로 보관.
       CopyOnWriteArrayList: send 시 iteration 중 다른 스레드의 add/remove 안전 */
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitterMap = new ConcurrentHashMap<>();

    public SseEmitter connect(Long empId) {
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L);
        emitterMap.computeIfAbsent(empId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // 종료 콜백: 자기 자신만 list 에서 제거. 다른 탭 emitter 는 영향 없음
        Runnable cleanup = () -> removeEmitter(empId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // 연결 직후 더미 이벤트 (503 방지). IOException 외 IllegalStateException/AsyncRequestNotUsableException 도 가능
        try {
            emitter.send(SseEmitter.event().name("connect").data("connected-Alarm"));
        } catch (Exception e) {
            removeEmitter(empId, emitter);
        }
        return emitter;
    }

    public void send(Long empId, Object data) {
        CopyOnWriteArrayList<SseEmitter> list = emitterMap.get(empId);
        if (list == null) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("alarm").data(data));
            } catch (Exception e) {
                // IOException(연결 끊김) + IllegalStateException(이미 complete) + AsyncRequestNotUsableException(응답 errored) 모두 처리
                // 이미 errored 상태인 emitter 는 completeWithError 자체가 또 throw 할 수 있어 한 번 더 감싸기
                try { emitter.completeWithError(e); } catch (Exception ignore) {}
                // onError 콜백이 비동기로 호출되지만, race 로 누락될 가능성 대비해 즉시 제거
                removeEmitter(empId, emitter);
            }
        }
    }

    private void removeEmitter(Long empId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitterMap.get(empId);
        if (list == null) return;
        list.remove(emitter);
        // 빈 list 는 map 에서도 정리. computeIfPresent 로 race 방지 (확인 시점에 다시 비어있을 때만 제거)
        emitterMap.computeIfPresent(empId, (k, v) -> v.isEmpty() ? null : v);
    }
}
