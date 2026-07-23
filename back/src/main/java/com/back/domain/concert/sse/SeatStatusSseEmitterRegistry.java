package com.back.domain.concert.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 회차별 SSE Emitter 풀 관리 레지스트리.
 *
 * <p>좌석 상태 변경(AVAILABLE, HOLD, SOLD_OUT) 이벤트 발생 시
 * 해당 회차({@code scheduleId})를 구독 중인 모든 클라이언트에게 이벤트를 브로드캐스트합니다.
 *
 * <p>{@link CopyOnWriteArrayList}를 사용하여 브로드캐스트 중 연결 해제가 발생해도
 * {@code ConcurrentModificationException} 없이 안전하게 처리됩니다.
 */
@Slf4j
@Component
public class SeatStatusSseEmitterRegistry {

    /**
     * key: scheduleId, value: 해당 회차를 구독 중인 SseEmitter 목록
     */
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 새 SseEmitter를 등록합니다.
     */
    public SseEmitter register(Long scheduleId, SseEmitter emitter) {
        emitters.computeIfAbsent(scheduleId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // 연결 완료/타임아웃/오류 시 자동 제거
        Runnable cleanup = () -> {
            List<SseEmitter> list = emitters.get(scheduleId);
            if (list != null) {
                list.remove(emitter);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        log.debug("SSE 구독 등록: scheduleId={}, 총 구독자={}", scheduleId,
                emitters.getOrDefault(scheduleId, List.of()).size());

        return emitter;
    }

    /**
     * 해당 회차 구독자 전체에게 좌석 상태 변경 이벤트를 브로드캐스트합니다.
     *
     * @param scheduleId  회차 ID
     * @param seatNumber  변경된 좌석 번호
     * @param status      변경된 상태 (AVAILABLE, HOLD, SOLD_OUT)
     */
    public void broadcast(Long scheduleId, String seatNumber, String status) {
        List<SseEmitter> list = emitters.get(scheduleId);
        if (list == null || list.isEmpty()) return;

        String data = "{\"seatNumber\":\"%s\",\"status\":\"%s\"}".formatted(seatNumber, status);

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name("seat_status_changed")
                        .data(data));
            } catch (IOException e) {
                log.debug("SSE 전송 실패 (연결 끊김): scheduleId={}, seat={}", scheduleId, seatNumber);
                emitter.complete();
            }
        }
    }
}
