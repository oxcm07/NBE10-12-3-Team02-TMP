package com.back.global.initData.generator

import com.back.domain.schedule.entity.Schedule
import com.back.domain.schedule.entity.ScheduleSeat
import com.back.domain.schedule.entity.SeatStatus

object SeatGenerator {

    private data class SeatGrade(val gradeName: String, val price: Int, val rowLabel: String)

    private val GRADES = listOf(
        SeatGrade("VIP", 150000, "A"),
        SeatGrade("VIP", 150000, "B"),
        SeatGrade("R", 120000, "C"),
        SeatGrade("R", 120000, "D"),
        SeatGrade("R", 120000, "E"),
        SeatGrade("R", 120000, "F"),
        SeatGrade("S", 90000, "G"),
        SeatGrade("S", 90000, "H"),
        SeatGrade("S", 90000, "I"),
        SeatGrade("S", 90000, "J"),
        SeatGrade("S", 90000, "K"),
        SeatGrade("S", 90000, "L"),
        SeatGrade("A", 70000, "M"),
        SeatGrade("A", 70000, "N"),
        SeatGrade("A", 70000, "O"),
        SeatGrade("A", 70000, "P"),
        SeatGrade("A", 70000, "Q"),
        SeatGrade("A", 70000, "R"),
        SeatGrade("A", 70000, "S"),
        SeatGrade("A", 70000, "T")
    )

    private const val SEATS_PER_ROW = 30

    @JvmStatic
    fun generate(schedule: Schedule): List<ScheduleSeat> = GRADES.flatMap { grade ->
        (1..SEATS_PER_ROW).map { num ->
            ScheduleSeat.create(
                schedule = schedule,
                gradeName = grade.gradeName,
                seatNumber = "${grade.rowLabel}-$num",
                seatPrice = grade.price,
                seatStatus = SeatStatus.AVAILABLE
            )
        }
    }
}
