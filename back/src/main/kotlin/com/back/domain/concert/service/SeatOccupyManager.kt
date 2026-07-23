package com.back.domain.concert.service

import com.back.domain.concert.dto.SeatOccupyResponse
import com.back.domain.concert.dto.SeatSelectionResponse
import com.back.domain.concert.event.SeatOccupiedEvent
import com.back.domain.schedule.entity.SeatStatus
import com.back.domain.schedule.repository.ScheduleSeatRepository
import com.back.domain.ticket.repository.TicketRepository
import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class SeatOccupyManager(
    private val concertService: ConcertService,
    private val ticketRepository: TicketRepository,
    private val scheduleSeatRepository: ScheduleSeatRepository,
    private val redissonClient: RedissonClient,
    private val eventPublisher: ApplicationEventPublisher
) {

    @Transactional
    fun seatOccupy(concertId: Long, scheduleId: Long, seatNumber: String, userId: Long): SeatOccupyResponse {
        concertService.validateConcertScheduleMatch(concertId, scheduleId)
        concertService.validateScheduleBookable(scheduleId)

        val redisKey = generateSeatOccupyKey(concertId, scheduleId, seatNumber)
        val indexKey = generateSeatOccupyIndexKey(concertId, scheduleId)
        val occupyToken = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expireAt = now + (OCCUPY_TTL_SECONDS * 1000)

        val result: Long? = redissonClient.getScript(StringCodec.INSTANCE).eval(
            RScript.Mode.READ_WRITE,
            OCCUPY_SCRIPT,
            RScript.ReturnType.LONG,
            listOf(redisKey, indexKey),
            userId.toString(),
            occupyToken,
            OCCUPY_TTL_SECONDS.toString(),
            seatNumber,
            expireAt.toString()
        )

        if (result == null || result == 0L) {
            throw ServiceException(ErrorCode.SEAT_HELD_BY_OTHER_USER)
        }

        try {
            val seat = scheduleSeatRepository
                .findWithLockByScheduleIdAndSeatNumber(scheduleId, seatNumber)
                ?: throw ServiceException(ErrorCode.SEAT_NOT_FOUND)

            if (seat.seatStatus == SeatStatus.SOLD_OUT) {
                cleanupRedis(redisKey, indexKey, seatNumber)
                throw ServiceException(ErrorCode.SEAT_ALREADY_SOLD)
            }

            if (seat.seatStatus != SeatStatus.HOLD) {
                seat.updateSeatStatus(SeatStatus.HOLD)
            }
        } catch (e: ServiceException) {
            if (e.errorCode != ErrorCode.SEAT_ALREADY_SOLD) {
                cleanupRedis(redisKey, indexKey, seatNumber)
            }
            throw e
        } catch (e: Exception) {
            cleanupRedis(redisKey, indexKey, seatNumber)
            throw e
        }

        eventPublisher.publishEvent(SeatOccupiedEvent(concertId, scheduleId, seatNumber, OCCUPY_TTL_SECONDS))

        return SeatOccupyResponse.of(occupyToken, OCCUPY_TTL_SECONDS)
    }

    @Transactional
    fun seatOccupyCancel(concertId: Long, scheduleId: Long, seatNumber: String, userId: Long) {
        concertService.validateConcertScheduleMatch(concertId, scheduleId)

        val redisKey = generateSeatOccupyKey(concertId, scheduleId, seatNumber)
        val hashMap = redissonClient.getMap<String, String>(redisKey)

        val occupyUserId = hashMap["userId"]
            ?: throw ServiceException(ErrorCode.SEAT_HOLD_EXPIRED)

        if (occupyUserId != userId.toString()) {
            throw ServiceException(ErrorCode.SEAT_HELD_BY_OTHER_USER)
        }

        cleanupRedis(redisKey, generateSeatOccupyIndexKey(concertId, scheduleId), seatNumber)

        val seat = scheduleSeatRepository.findWithLockByScheduleIdAndSeatNumber(scheduleId, seatNumber)
        if (seat?.seatStatus == SeatStatus.HOLD) {
            seat.updateSeatStatus(SeatStatus.AVAILABLE)
        }
    }

    @Transactional(readOnly = true)
    fun getSeatSelection(concertId: Long, scheduleId: Long, userId: Long): SeatSelectionResponse {
        concertService.validateConcertScheduleMatch(concertId, scheduleId)
        concertService.validateScheduleBookable(scheduleId)

        val currentTicketCount = ticketRepository
            .countByUser_UserIdAndSchedule_ScheduleIdAndIsValidTrue(userId, scheduleId)
        if (currentTicketCount >= 3) {
            throw ServiceException(ErrorCode.EXCEED_TICKET_LIMIT)
        }

        val seats = concertService.getScheduleSeats(scheduleId)
        val pricesMap = concertService.convertToPriceMap(seats)

        val seatResponses = seats.map { seat ->
            SeatSelectionResponse.SeatDetailResponse(
                seat.seatNumber,
                seat.seatStatus,
                seat.gradeName
            )
        }

        return SeatSelectionResponse.of(concertId, scheduleId, pricesMap, seatResponses)
    }

    fun cleanupRedis(redisKey: String, indexKey: String, seatNumber: String) {
        redissonClient.getMap<String, String>(redisKey).delete()
        redissonClient.getScoredSortedSet<String>(indexKey).remove(seatNumber)
    }

    companion object {
        const val OCCUPY_TTL_SECONDS: Long = 600

        private val OCCUPY_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 1 then
              if redis.call('HGET', KEYS[1], 'userId') == ARGV[1] then
                redis.call('HSET', KEYS[1], 'occupyToken', ARGV[2])
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
                redis.call('ZADD', KEYS[2], tonumber(ARGV[5]), ARGV[4])
                return 1
              else
                return 0
              end
            else
              redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'occupyToken', ARGV[2])
              redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
              redis.call('ZADD', KEYS[2], tonumber(ARGV[5]), ARGV[4])
              return 1
            end
        """.trimIndent()

        @JvmStatic
        fun generateSeatOccupyKey(concertId: Long, scheduleId: Long, seatNumber: String): String =
            "seat:occupy:$concertId:$scheduleId:$seatNumber"

        @JvmStatic
        fun generateSeatOccupyIndexKey(concertId: Long, scheduleId: Long): String =
            "seat:occupy:index:$concertId:$scheduleId"
    }
}
