package com.back.domain.waiting.service

import com.back.domain.schedule.repository.ScheduleRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class WaitingQueueScheduler(
    private val waitingQueueService: WaitingQueueService,
    private val waitingQueueManager: WaitingQueueManager,
    private val scheduleRepository: ScheduleRepository
) {

    @Scheduled(fixedDelay = 1000)
    @Transactional
    fun processExpiredActiveUsers() {
        val activeScheduleIds = waitingQueueManager.getActiveScheduleIds()
        if (activeScheduleIds.isEmpty()) return

        for (idStr in activeScheduleIds) {
            val scheduleId = idStr.toLong()
            val expired = waitingQueueManager.removeExpiredActiveUsers(scheduleId)

            if (expired > 0) {
                val schedule = scheduleRepository.findById(scheduleId).orElse(null) ?: continue
                val concertId = schedule.concert.concertId ?: continue
                waitingQueueService.allowEntry(concertId, scheduleId)
            }

            if (waitingQueueManager.isQueueEmpty(scheduleId)) {
                waitingQueueManager.removeFromActiveSchedules(idStr)
            }
        }
    }
}
