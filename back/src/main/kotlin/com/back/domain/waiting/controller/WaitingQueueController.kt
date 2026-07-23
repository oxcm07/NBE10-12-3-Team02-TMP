package com.back.domain.waiting.controller

import com.back.domain.waiting.dto.WaitingQueueResponse
import com.back.domain.waiting.service.WaitingQueueService
import com.back.global.annotation.ApiV1
import com.back.global.requestcontext.RequestContext
import com.back.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@ApiV1
@RestController
@RequestMapping("/waiting")
@Tag(name = "Waiting", description = "Waiting API")
class WaitingQueueController(
    private val waitingQueueService: WaitingQueueService,
    private val requestContext: RequestContext
) {

    @PostMapping("/concerts/{concertId}/schedules/{scheduleId}/waiting-queue")
    @Operation(summary = "대기열 등록", description = "대기열 등록 API")
    fun registerWaiting(
        @PathVariable concertId: Long,
        @PathVariable scheduleId: Long
    ): RsData<WaitingQueueResponse> {
        val actor = requestContext.actor ?: throw IllegalStateException("Actor must not be null")
        val response = waitingQueueService.registerWaiting(
            concertId,
            scheduleId,
            actor.id
        )
        return RsData("200-1", "대기열 등록 성공", response)
    }

    @GetMapping("/concerts/{concertId}/schedules/{scheduleId}/waiting-queue/rank")
    @Operation(summary = "대기열 순번 조회", description = "대기열 순번 조회 API")
    fun showWaitingRank(
        @PathVariable concertId: Long,
        @PathVariable scheduleId: Long
    ): RsData<WaitingQueueResponse> {
        val actor = requestContext.actor ?: throw IllegalStateException("Actor must not be null")
        val response = waitingQueueService.showWaitingRank(
            concertId,
            scheduleId,
            actor.id
        )

        return RsData("200-1", "대기열 순번 조회 성공", response)
    }

    @DeleteMapping("/concerts/{concertId}/schedules/{scheduleId}/waiting-queue")
    @Operation(summary = "대기열 취소", description = "대기열 취소 API")
    fun cancelWaiting(
        @PathVariable concertId: Long,
        @PathVariable scheduleId: Long
    ): RsData<Void> {
        val actor = requestContext.actor ?: throw IllegalStateException("Actor must not be null")
        waitingQueueService.cancelWaiting(
            concertId,
            scheduleId,
            actor.id
        )

        return RsData("200-1", "대기열 취소 성공")
    }
}
