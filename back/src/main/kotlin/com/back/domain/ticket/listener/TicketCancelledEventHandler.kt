package com.back.domain.ticket.listener

import com.back.domain.ticket.event.TicketCancelledEvent
import com.back.domain.waiting.service.WaitingQueueManager
import com.back.domain.waiting.service.WaitingQueueService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class TicketCancelledEventHandler(
    private val waitingQueueService: WaitingQueueService,
    private val waitingQueueManager: WaitingQueueManager
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleTicketCancelled(event: TicketCancelledEvent) {
        waitingQueueManager.removeActiveUser(event.scheduleId, event.userId)
        waitingQueueService.allowEntry(event.concertId, event.scheduleId)
    }
}
