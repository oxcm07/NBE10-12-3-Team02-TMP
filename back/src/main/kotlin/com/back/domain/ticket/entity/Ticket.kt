package com.back.domain.ticket.entity

import com.back.domain.schedule.entity.Schedule
import com.back.domain.schedule.entity.ScheduleSeat
import com.back.domain.user.entity.User
import com.back.global.jpa.entity.BaseEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
class Ticket(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    var schedule: Schedule,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_seat_id", nullable = false)
    var scheduleSeat: ScheduleSeat,

    @Column(nullable = false, unique = true)
    var ticketNumber: String,

    @Column(unique = true)
    var qrToken: String? = null,

    @Column(nullable = false)
    var ticketPrice: Int = 0,

    @Column(nullable = false)
    var isValid: Boolean = true
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val ticketId: Long? = null

    fun updateIsValid(isValid: Boolean) {
        this.isValid = isValid
    }

    companion object {
        fun create(
            user: User,
            schedule: Schedule,
            scheduleSeat: ScheduleSeat,
            ticketNumber: String,
            ticketPrice: Int
        ): Ticket {
            return Ticket(
                user = user,
                schedule = schedule,
                scheduleSeat = scheduleSeat,
                ticketNumber = ticketNumber,
                qrToken = UUID.randomUUID().toString(),
                ticketPrice = ticketPrice,
                isValid = true
            )
        }
    }
}
