package com.back.global.initData

import com.back.domain.concert.repository.ConcertRepository
import com.back.global.initData.fixture.*
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Profile("!test")
class BaseInitData(
    private val concertRepository: ConcertRepository,
    private val venueFixture: VenueFixture,
    private val concertFixture: ConcertFixture,
    private val concertDetailFixture: ConcertDetailFixture,
    private val scheduleFixture: ScheduleFixture,
    private val scheduleSeatFixture: ScheduleSeatFixture
) : CommandLineRunner {

    @Transactional
    override fun run(vararg args: String?) {
        if (concertRepository.count() > 0) {
            return
        }

        val venues = venueFixture.createVenues()
        val concerts = concertFixture.createConcerts()
        concertDetailFixture.createDetails(concerts)
        val schedules = scheduleFixture.createSchedules(concerts, venues)
        scheduleSeatFixture.createSeats(schedules)
    }
}
