package com.back.global.security.interceptor

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.servlet.HandlerMapping

class RequestBodyParsingBenchmarkTest {
    private val objectMapper = ObjectMapper()

    @Test
    @DisplayName("URL Path Variable vs RequestBody Parsing 벤치마크")
    fun benchmarkParsingPerformance() {
        val requestCount = 50000
        val urlRequest = MockHttpServletRequest()
        val pathVariables = mapOf("scheduleId" to "12345")
        urlRequest.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVariables)
        val jsonBody = """{"concertId":1,"scheduleId":12345,"seatNumber":"A-1"}"""

        // Case A: URL 기반 추출
        val urlStartTime = System.nanoTime()
        for (i in 0 until requestCount) {
            @Suppress("UNCHECKED_CAST")
            val vars = urlRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<String, String>
            val scheduleIdStr = vars?.get("scheduleId")
            if (scheduleIdStr != null) {
                val scheduleId = scheduleIdStr.toLong()
            }
        }
        val urlEndTime = System.nanoTime()
        val urlDurationMs = (urlEndTime - urlStartTime) / 1_000_000

        // Case B: Request Body 파싱 기반 추출
        val bodyStartTime = System.nanoTime()
        for (i in 0 until requestCount) {
            val cachedBody = jsonBody.toByteArray()

            @Suppress("UNCHECKED_CAST")
            val bodyMap = objectMapper.readValue(cachedBody, Map::class.java) as Map<String, Any>
            val scheduleIdObj = bodyMap["scheduleId"]
            if (scheduleIdObj != null) {
                val scheduleId = scheduleIdObj.toString().toLong()
            }
        }
        val bodyEndTime = System.nanoTime()
        val bodyDurationMs = (bodyEndTime - bodyStartTime) / 1_000_000

        println("HTTP 인터셉터 파싱 방식 성능 측정 (요청수: ${requestCount}회)")
        println("URL Path Variable 조회 : $urlDurationMs ms")
        println("Request Body 캐싱 및 JSON 파싱 : $bodyDurationMs ms")
    }
}
