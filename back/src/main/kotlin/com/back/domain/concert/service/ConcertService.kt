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
import org.springframework.data.repository.findByIdOrNull
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
        val concerts = concertRepository.findByKeyword(keyword)
        val concertIds = concerts.mapNotNull { it.concertId }
        val schedules = scheduleRepository.findAllWithVenueByConcertIds(concertIds)

        val filteredConcerts = date?.let { targetDate ->
            val matchingIds = schedules
                .filter { it.scheduleDate.toLocalDate() == targetDate }
                .mapNotNull { it.concert.concertId }
                .toSet()
            concerts.filter { matchingIds.contains(it.concertId) }
        } ?: concerts

        val venueNameMap = schedules
            .mapNotNull { s -> s.concert.concertId?.let { cid -> cid to s.venue.venueName } }
            .toMap()

        val comparator = when (sort) {
            ConcertSortType.latest -> compareByDescending<Concert> { it.startDate }
            else -> compareBy<Concert> { it.endDate }
        }

        return filteredConcerts.sortedWith(comparator)
            .map { concert ->
                val venueName = venueNameMap[concert.concertId].orEmpty()
                ConcertListResponse.of(concert, venueName)
            }
    }

    fun getConcertDetail(concertId: Long): ConcertDetailResponse {
        val concert = concertRepository.findByIdOrNull(concertId)
            ?: throw ServiceException(ErrorCode.CONCERT_NOT_FOUND)

        val schedule = scheduleRepository.findWithVenueByConcertId(concertId)
            .firstOrNull()
            ?: throw ServiceException(ErrorCode.CONCERT_SCHEDULE_EMPTY)

        val detailUrlList = concertDeatilRepository
            .findByConcertConcertId(concertId)
            .mapNotNull { it.urlDetail }

        val scheduleId = checkNotNull(schedule.scheduleId) { "Schedule ID null" }
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

    fun getScheduleSeats(scheduleId: Long): List<ScheduleSeat> =
        scheduleSeatRepository.findByScheduleScheduleId(scheduleId)

    @Transactional
    fun validateSeatAvailable(scheduleId: Long, seatNumber: String) {
        val seat = scheduleSeatRepository
            .findWithLockByScheduleIdAndSeatNumber(scheduleId, seatNumber)
            ?: throw ServiceException(ErrorCode.SEAT_NOT_FOUND)

        when (seat.seatStatus) {
            SeatStatus.SOLD_OUT -> throw ServiceException(ErrorCode.SEAT_ALREADY_SOLD)
            SeatStatus.HOLD -> throw ServiceException(ErrorCode.SEAT_HELD_BY_OTHER_USER)
            else -> Unit
        }
    }

    fun validateConcertScheduleMatch(concertId: Long, scheduleId: Long) {
        scheduleRepository.findByScheduleIdAndConcert_ConcertId(scheduleId, concertId)
            ?: throw ServiceException(ErrorCode.INVALID_CONCERT_SCHEDULE)
    }

    fun convertToPriceMap(scheduleSeats: List<ScheduleSeat>): Map<String, Int> =
        scheduleSeats.associate { it.gradeName to it.seatPrice }

    fun validateScheduleBookable(scheduleId: Long) {
        val schedule = scheduleRepository.findByIdOrNull(scheduleId)
            ?: throw ServiceException(ErrorCode.CONCERT_SCHEDULE_EMPTY)

        if (LocalDateTime.now().isAfter(schedule.scheduleDate)) {
            throw ServiceException(ErrorCode.EXPIRED_BOOKING_DEADLINE)
        }
    }
}
