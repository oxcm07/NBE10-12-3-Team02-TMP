package com.back.domain.waiting.service;

import com.back.domain.schedule.entity.Schedule;
import com.back.domain.schedule.repository.ScheduleRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 대기열 스케줄러.
 *
 * <p>1초마다 실행되어 다음 작업을 수행합니다:
 * <ol>
 *   <li>트래픽이 활성화된 회차(Active Schedules)만 대상으로 루프</li>
 *   <li>진입열에서 만료된 유저 제거</li>
 *   <li>빈 정원만큼 대기열 유저 진입 허가</li>
 *   <li>큐가 비어있으면 활성 회차 목록에서 제거 (스케줄러 부하 절감)</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class WaitingQueueScheduler {

    private final WaitingQueueService waitingQueueService;
    private final WaitingQueueManager waitingQueueManager;
    private final ScheduleRepository scheduleRepository;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processExpiredActiveUsers() {
        Set<String> activeScheduleIds = waitingQueueManager.getActiveScheduleIds();
        if (activeScheduleIds == null || activeScheduleIds.isEmpty()) return;

        for (String idStr : activeScheduleIds) {
            Long scheduleId = Long.parseLong(idStr);
            long expired = waitingQueueManager.removeExpiredActiveUsers(scheduleId);

            if (expired > 0) {
                Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
                if (schedule == null) continue;
                waitingQueueService.allowEntry(
                        schedule.getConcert().getConcertId(),
                        scheduleId
                );
            }

            if (waitingQueueManager.isQueueEmpty(scheduleId)) {
                waitingQueueManager.removeFromActiveSchedules(idStr);
            }
        }
    }
}