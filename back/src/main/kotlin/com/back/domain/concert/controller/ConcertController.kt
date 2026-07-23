package com.back.domain.concert.controller

import com.back.domain.concert.dto.*
import com.back.domain.concert.enums.ConcertSortType
import com.back.domain.concert.service.ConcertService
import com.back.domain.concert.service.SeatOccupyManager
import com.back.global.annotation.ApiV1
import com.back.global.requestcontext.RequestContext
import com.back.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@ApiV1
@RestController
@RequestMapping("/concerts")
@Tag(name = "Concert", description = "Concert API")
class ConcertController(
    private val concertService: ConcertService,
    private val seatOccupyManager: SeatOccupyManager,
    private val requestContext: RequestContext
) {

    @GetMapping
    @Operation(summary = "콘서트 목록 조회", description = "콘서트 목록 조회 API")
    fun getConcerts(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "closingSoon") sort: ConcertSortType,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?
    ): RsData<List<ConcertListResponse>> {
        val data = concertService.getConcerts(keyword, sort, date)
        return RsData("200-1", "콘서트 목록 조회 성공", data)
    }

    @GetMapping("/{concertId}")
    @Operation(summary = "콘서트 상세 조회", description = "콘서트 상세 정보 조회 API")
    fun getConcertDetail(
        @PathVariable concertId: Long
    ): RsData<ConcertDetailResponse> {
        val data = concertService.getConcertDetail(concertId)
        return RsData("200-1", "콘서트 상세 정보 조회 성공", data)
    }

    @GetMapping("/{concertId}/schedules/{scheduleId}/seats")
    @Operation(summary = "좌석 선택 페이지", description = "좌석 선택 페이지 API")
    fun getSeatSelection(
        @PathVariable concertId: Long,
        @PathVariable scheduleId: Long
    ): RsData<SeatSelectionResponse> {
        val actor = requestContext.actor ?: throw IllegalStateException("Actor must not be null")
        val response = seatOccupyManager.getSeatSelection(
            concertId,
            scheduleId,
            actor.id
        )

        return RsData(
            "200-1",
            "좌석 선택 페이지 조회 성공",
            response
        )
    }

    @PostMapping("/{concertId}/schedules/{scheduleId}/seats/occupy")
    @Operation(summary = "Redis 실시간 좌석 선점 요청", description = "Redis 실시간 좌석 선점 요청 API")
    fun seatOccupy(
        @PathVariable concertId: Long,
        @PathVariable scheduleId: Long,
        @RequestBody request: SeatOccupyRequest
    ): RsData<SeatOccupyResponse> {
        val actor = requestContext.actor ?: throw IllegalStateException("Actor must not be null")
        val response = seatOccupyManager.seatOccupy(
            concertId,
            scheduleId,
            request.seatNumber,
            actor.id
        )

        return RsData(
            "200-1",
            "좌석 임시 선점에 성공했습니다.",
            response
        )
    }

    @DeleteMapping("/{concertId}/schedules/{scheduleId}/seats/occupy")
    @Operation(summary = "Redis 실시간 좌석 선점 취소", description = "Redis 실시간 좌석 선점 취소 API")
    fun seatOccupyCancel(
        @PathVariable concertId: Long,
        @PathVariable scheduleId: Long,
        @RequestBody request: SeatOccupyRequest
    ): RsData<Void> {
        val actor = requestContext.actor ?: throw IllegalStateException("Actor must not be null")
        seatOccupyManager.seatOccupyCancel(
            concertId,
            scheduleId,
            request.seatNumber,
            actor.id
        )

        return RsData(
            "200-1",
            "좌석 선점이 정상적으로 취소되었습니다."
        )
    }
}
