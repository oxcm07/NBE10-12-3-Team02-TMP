package com.back.domain.concert.service

import com.back.domain.concert.dto.ConcertDetailResponse
import com.back.domain.concert.dto.ConcertListResponse
import com.back.domain.concert.entity.Concert
import com.back.domain.concert.enums.ConcertSortType
import com.back.domain.concert.repository.ConcertDeatilRepository
import com.back.domain.concert.repository.ConcertRepository
import com.back.domain.schedule.entity.ScheduleSeat
import com.back.domain.schedule.entity.SeatStatus
import com.back.domain.schedule.repository.ScheduleRepository
import com.back.domain.schedule.repository.ScheduleSeatRepository
import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class ConcertService(
    private val scheduleSeatRepository: ScheduleSeatRepository,
    private val scheduleRepository: ScheduleRepository,
    private val concertRepository: ConcertRepository,
    private val concertDeatilRepository: ConcertDeatilRepository
) {

    fun getConcerts(keyword: String?, sort: ConcertSortType?, date: LocalDate?): List<ConcertListResponse> {
        var concerts = concertRepository.findByKeyword(keyword)

        val concertIds = concerts.mapNotNull { it.concertId }

        val schedules = scheduleRepository.findAllWithVenueByConcertIds(concertIds)

        if (date != null) {
            val concertIdsWithMatchingSchedule = schedules
                .filter { it.scheduleDate.toLocalDate() == date }
                .mapNotNull { it.concert.concertId }
                .toSet()

            concerts = concerts.filter { concertIdsWithMatchingSchedule.contains(it.concertId) }
        }

        val venueNameMap = schedules
            .mapNotNull { s -> s.concert.concertId?.let { cid -> cid to s.venue.venueName } }
            .toMap()

        val comparator = if (sort == ConcertSortType.latest) {
            compareByDescending<Concert> { it.startDate }
        } else {
            compareBy<Concert> { it.endDate }
        }

        return concerts.sortedWith(comparator)
            .map { concert ->
                val venueName = venueNameMap[concert.concertId] ?: ""
                ConcertListResponse.of(concert, venueName)
            }
    }

    fun getConcertDetail(concertId: Long): ConcertDetailResponse {
        val concert = concertRepository.findById(concertId)
            .orElseThrow { ServiceException(ErrorCode.CONCERT_NOT_FOUND) }

        val schedule = scheduleRepository.findWithVenueByConcertId(concertId)
            .firstOrNull()
            ?: throw ServiceException(ErrorCode.CONCERT_SCHEDULE_EMPTY)

        val detailUrlList = concertDeatilRepository
            .findByConcertConcertId(concertId)
            .mapNotNull { it.urlDetail }

        val scheduleId = schedule.scheduleId ?: throw IllegalStateException("Schedule ID null")
        val scheduleSeats = scheduleSeatRepository.findByScheduleScheduleId(scheduleId)
        val prices = convertToPriceMap(scheduleSeats)

        val bookable = concert.endDate.isAfter(LocalDateTime.now())

        return ConcertDetailResponse.of(
            concert = concert,
            venueName = schedule.venue.venueName,
            location = schedule.venue.location,
            detailUrlList = detailUrlList,
            prices = prices,
            bookable = bookable
        )
    }

    fun getScheduleSeats(scheduleId: Long): List<ScheduleSeat> {
        return scheduleSeatRepository.findByScheduleScheduleId(scheduleId)
    }

    @Transactional
    fun validateSeatAvailable(scheduleId: Long, seatNumber: String) {
        val seat = scheduleSeatRepository
            .findWithLockByScheduleIdAndSeatNumber(scheduleId, seatNumber)
            .orElseThrow { ServiceException(ErrorCode.SEAT_NOT_FOUND) }

        if (seat.seatStatus == SeatStatus.SOLD_OUT) {
            throw ServiceException(ErrorCode.SEAT_ALREADY_SOLD)
        }
        if (seat.seatStatus == SeatStatus.HOLD) {
            throw ServiceException(ErrorCode.SEAT_HELD_BY_OTHER_USER)
        }
    }

    fun validateConcertScheduleMatch(concertId: Long, scheduleId: Long) {
        scheduleRepository.findByScheduleIdAndConcert_ConcertId(scheduleId, concertId)
            .orElseThrow { ServiceException(ErrorCode.INVALID_CONCERT_SCHEDULE) }
    }

    fun convertToPriceMap(scheduleSeats: List<ScheduleSeat>): Map<String, Int> {
        return scheduleSeats.associate { it.gradeName to it.seatPrice }
    }

    fun validateScheduleBookable(scheduleId: Long) {
        val schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow { ServiceException(ErrorCode.CONCERT_SCHEDULE_EMPTY) }

        if (LocalDateTime.now().isAfter(schedule.scheduleDate)) {
            throw ServiceException(ErrorCode.EXPIRED_BOOKING_DEADLINE)
        }
    }
}
