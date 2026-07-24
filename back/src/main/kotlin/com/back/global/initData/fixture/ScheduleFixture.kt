package com.back.global.initData.fixture

import com.back.domain.concert.entity.Concert
import com.back.domain.schedule.entity.Schedule
import com.back.domain.schedule.repository.ScheduleRepository
import com.back.domain.venue.entity.Venue
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class ScheduleFixture(
    private val scheduleRepository: ScheduleRepository
) {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun dt(value: String): LocalDateTime = LocalDateTime.parse(value, fmt)

    fun createSchedules(concerts: List<Concert>, venues: List<Venue>): List<Schedule> {
        val schedules = buildList {
            add(Schedule.create(concerts[0], venues[0], dt("2026-06-30 14:00:00"), 1))
            add(Schedule.create(concerts[0], venues[1], dt("2026-07-22 18:00:00"), 2))
            add(Schedule.create(concerts[1], venues[21], dt("2026-07-10 15:00:00"), 1))
            add(Schedule.create(concerts[1], venues[4], dt("2026-07-25 19:00:00"), 2))
            add(Schedule.create(concerts[1], venues[1], dt("2026-08-07 20:00:00"), 3))
            add(Schedule.create(concerts[2], venues[1], dt("2026-07-09 14:00:00"), 1))
            add(Schedule.create(concerts[2], venues[2], dt("2026-07-20 18:00:00"), 2))
            add(Schedule.create(concerts[3], venues[3], dt("2026-06-25 19:00:00"), 1))
            add(Schedule.create(concerts[3], venues[3], dt("2026-08-20 19:00:00"), 2))
            add(Schedule.create(concerts[4], venues[4], dt("2026-06-30 16:00:00"), 1))
            add(Schedule.create(concerts[5], venues[5], dt("2026-07-02 17:00:00"), 1))
            add(Schedule.create(concerts[5], venues[5], dt("2026-09-09 19:00:00"), 2))
            add(Schedule.create(concerts[6], venues[6], dt("2026-07-15 14:00:00"), 1))
            add(Schedule.create(concerts[6], venues[6], dt("2026-07-30 16:00:00"), 2))
            add(Schedule.create(concerts[7], venues[7], dt("2026-07-05 18:00:00"), 1))
            add(Schedule.create(concerts[8], venues[2], dt("2026-07-20 15:00:00"), 1))
            add(Schedule.create(concerts[8], venues[8], dt("2026-10-22 18:00:00"), 2))
            add(Schedule.create(concerts[8], venues[10], dt("2026-12-11 19:00:00"), 3))
            add(Schedule.create(concerts[9], venues[11], dt("2026-07-16 13:00:00"), 1))
            add(Schedule.create(concerts[9], venues[9], dt("2026-10-30 15:00:00"), 2))
            add(Schedule.create(concerts[9], venues[8], dt("2027-02-24 17:00:00"), 3))
            add(Schedule.create(concerts[10], venues[10], dt("2026-05-20 14:00:00"), 1))
            add(Schedule.create(concerts[10], venues[9], dt("2026-07-01 16:00:00"), 2))
            add(Schedule.create(concerts[11], venues[11], dt("2026-06-25 18:00:00"), 1))
            add(Schedule.create(concerts[11], venues[11], dt("2026-08-01 19:00:00"), 2))
            add(Schedule.create(concerts[12], venues[12], dt("2026-07-15 19:00:00"), 1))
            add(Schedule.create(concerts[12], venues[12], dt("2026-07-28 20:00:00"), 2))
            add(Schedule.create(concerts[13], venues[13], dt("2026-07-01 17:00:00"), 1))
            add(Schedule.create(concerts[13], venues[13], dt("2026-08-15 19:00:00"), 2))
            add(Schedule.create(concerts[14], venues[14], dt("2026-07-20 18:00:00"), 1))
            add(Schedule.create(concerts[14], venues[14], dt("2026-08-20 19:00:00"), 2))
            add(Schedule.create(concerts[15], venues[15], dt("2026-07-10 14:00:00"), 1))
            add(Schedule.create(concerts[15], venues[15], dt("2026-09-10 16:00:00"), 2))
            add(Schedule.create(concerts[16], venues[16], dt("2026-07-05 18:00:00"), 1))
            add(Schedule.create(concerts[16], venues[16], dt("2026-07-11 19:00:00"), 2))
            add(Schedule.create(concerts[17], venues[18], dt("2026-07-15 15:00:00"), 1))
            add(Schedule.create(concerts[17], venues[17], dt("2026-08-30 18:00:00"), 2))
            add(Schedule.create(concerts[18], venues[18], dt("2026-07-23 19:00:00"), 1))
            add(Schedule.create(concerts[19], venues[20], dt("2026-06-30 19:00:00"), 1))
            add(Schedule.create(concerts[19], venues[19], dt("2026-07-23 20:00:00"), 2))
            add(Schedule.create(concerts[20], venues[20], dt("2026-07-15 18:00:00"), 1))
            add(Schedule.create(concerts[20], venues[20], dt("2026-09-02 19:00:00"), 2))
            add(Schedule.create(concerts[21], venues[21], dt("2026-08-12 19:00:00"), 1))
            add(Schedule.create(concerts[22], venues[22], dt("2026-07-22 14:00:00"), 1))
            add(Schedule.create(concerts[22], venues[22], dt("2026-09-01 16:00:00"), 2))
            add(Schedule.create(concerts[23], venues[23], dt("2026-07-11 18:00:00"), 1))
            add(Schedule.create(concerts[23], venues[23], dt("2026-07-29 19:00:00"), 2))
            add(Schedule.create(concerts[24], venues[24], dt("2026-07-20 14:00:00"), 1))
            add(Schedule.create(concerts[24], venues[24], dt("2026-08-05 16:00:00"), 2))
            add(Schedule.create(concerts[25], venues[25], dt("2026-08-10 15:00:00"), 1))
            add(Schedule.create(concerts[26], venues[25], dt("2026-07-26 19:00:00"), 1))
            add(Schedule.create(concerts[27], venues[26], dt("2026-07-30 18:00:00"), 1))
            add(Schedule.create(concerts[27], venues[27], dt("2026-09-08 19:00:00"), 2))
            add(Schedule.create(concerts[28], venues[24], dt("2026-07-22 18:00:00"), 1))
            add(Schedule.create(concerts[28], venues[28], dt("2026-08-25 19:00:00"), 2))
            add(Schedule.create(concerts[28], venues[26], dt("2026-09-22 20:00:00"), 3))
        }

        return scheduleRepository.saveAll(schedules)
    }
}
