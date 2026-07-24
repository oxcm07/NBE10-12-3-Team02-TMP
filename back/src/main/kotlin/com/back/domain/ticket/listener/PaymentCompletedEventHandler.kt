package com.back.domain.ticket.listener

import com.back.domain.ticket.event.PaymentCompletedEvent
import com.back.domain.waiting.service.WaitingQueueManager
import com.back.domain.waiting.service.WaitingQueueService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PaymentCompletedEventHandler(
    private val waitingQueueManager: WaitingQueueManager,
    private val waitingQueueService: WaitingQueueService
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentCompleted(event: PaymentCompletedEvent) {
        waitingQueueManager.removeActiveUser(event.scheduleId, event.userId)
        waitingQueueService.allowEntry(event.concertId, event.scheduleId)
    }
}
