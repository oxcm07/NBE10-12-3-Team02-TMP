package com.back.global.initData.fixture

import com.back.domain.schedule.entity.Schedule
import com.back.domain.schedule.entity.ScheduleSeat
import com.back.domain.schedule.repository.ScheduleSeatRepository
import com.back.global.initData.generator.SeatGenerator
import org.springframework.stereotype.Component

@Component
class ScheduleSeatFixture(
    private val scheduleSeatRepository: ScheduleSeatRepository
) {
    fun createSeats(schedules: List<Schedule>) {
        val allSeats = mutableListOf<ScheduleSeat>()
        for (schedule in schedules) {
            allSeats.addAll(SeatGenerator.generate(schedule))
        }
        scheduleSeatRepository.saveAll(allSeats)
    }
}
