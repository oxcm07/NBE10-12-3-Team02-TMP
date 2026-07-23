package com.back.domain.concert.controller;

import com.back.domain.concert.service.SeatOccupyManager;
import com.back.domain.concert.sse.SeatStatusSseEmitterRegistry;
import com.back.domain.schedule.entity.ScheduleSeat;
import com.back.domain.schedule.repository.ScheduleSeatRepository;
import com.back.global.annotation.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * 실시간 좌석 상태 SSE 스트림 컨트롤러.
 *
 * <p>클라이언트가 좌석 선택 페이지 진입 시 이 엔드포인트에 연결하면,
 * 이후 다른 유저의 좌석 선점/만료/결제 완료 등의 이벤트를 실시간으로 수신할 수 있습니다.
 *
 * <h2>이벤트 형식</h2>
 * <pre>
 * event: seat_status_changed
 * data: {"seatNumber":"A-1","status":"HOLD"}
 *
 * event: seat_status_changed
 * data: {"seatNumber":"A-1","status":"AVAILABLE"}
 * </pre>
 *
 * <p>연결 시 현재 좌석 스냅샷을 {@code seat_snapshot} 이벤트로 즉시 전송합니다.
 */
@Slf4j
@ApiV1
@RestController
@RequestMapping("/concerts")
@RequiredArgsConstructor
@Tag(name = "Concert SSE", description = "실시간 좌석 상태 SSE API")
public class SeatStatusSseController {

    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30분

    private final SeatStatusSseEmitterRegistry registry;
    private final ScheduleSeatRepository scheduleSeatRepository;

    @GetMapping(value = "/{concertId}/schedules/{scheduleId}/seats/status",
            produces = "text/event-stream;charset=UTF-8")
    @Operation(
            summary = "실시간 좌석 상태 SSE 스트림",
            description = "좌석 상태 변경 이벤트(선점/만료/결제완료)를 실시간으로 수신합니다."
    )
    public SseEmitter seatStatusStream(
            @PathVariable Long concertId,
            @PathVariable Long scheduleId
    ) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        registry.register(scheduleId, emitter);

        // 연결 즉시 현재 좌석 스냅샷 전송
        try {
            List<ScheduleSeat> seats = scheduleSeatRepository.findByScheduleScheduleId(scheduleId);
            StringBuilder snapshot = new StringBuilder("[");
            for (int i = 0; i < seats.size(); i++) {
                ScheduleSeat seat = seats.get(i);
                snapshot.append("{\"seatNumber\":\"")
                        .append(seat.getSeatNumber())
                        .append("\",\"status\":\"")
                        .append(seat.getSeatStatus().name())
                        .append("\"}");
                if (i < seats.size() - 1) snapshot.append(",");
            }
            snapshot.append("]");

            emitter.send(SseEmitter.event()
                    .name("seat_snapshot")
                    .data(snapshot.toString()));
        } catch (IOException e) {
            log.warn("SSE 스냅샷 전송 실패: scheduleId={}", scheduleId, e);
            emitter.complete();
        }

        log.debug("SSE 스트림 연결: concertId={}, scheduleId={}", concertId, scheduleId);
        return emitter;
    }
}
