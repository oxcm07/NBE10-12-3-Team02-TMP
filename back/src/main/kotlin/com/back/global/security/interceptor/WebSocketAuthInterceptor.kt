package com.back.global.security.interceptor

import com.back.global.security.jwt.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component
import java.security.Principal

@Component
class WebSocketAuthInterceptor(
    private val jwtTokenProvider: JwtTokenProvider,
) : ChannelInterceptor {
    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)

        if (accessor != null && StompCommand.CONNECT == accessor.command) {
            val authorization = accessor.getFirstNativeHeader("Authorization")

            if (authorization != null && authorization.startsWith("Bearer ")) {
                val token = authorization.substring("Bearer ".length).trim()

                try {
                    val payload = jwtTokenProvider.parseAccessToken(token)
                    accessor.user = StompPrincipal(payload.userId.toString())
                    log.info("WebSocket 인증 성공: userId={}", payload.userId)
                } catch (e: Exception) {
                    log.warn("WebSocket 인증 실패: {}", e.message)
                }
            }
        }
        return message
    }

    data class StompPrincipal(
        private val principalName: String,
    ) : Principal {
        override fun getName(): String = principalName
    }

    companion object {
        private val log = LoggerFactory.getLogger(WebSocketAuthInterceptor::class.java)
    }
}
