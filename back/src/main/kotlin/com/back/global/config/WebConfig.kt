package com.back.global.config

import com.back.global.annotation.ApiV1
import com.back.global.ratelimit.RateLimitInterceptor
import com.back.global.security.interceptor.QueueInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.method.HandlerTypePredicate
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    @Value("\${custom.cors.allowed-origins:http://localhost:3000}") private val allowedOrigins: Array<String>,
    private val rateLimitInterceptor: RateLimitInterceptor,
    private val queueInterceptor: QueueInterceptor
) : WebMvcConfigurer {

    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix("/api/v1", HandlerTypePredicate.forAnnotation(ApiV1::class.java))
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = this@WebConfig.allowedOrigins.toList()
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf("Authorization")
            allowCredentials = true
        }

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/**", config)
        return source
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/api/v1/schedules/*/seats/status")

        registry.addInterceptor(queueInterceptor)
            .addPathPatterns(
                "/api/v1/concerts/*/schedules/*/seats",
                "/api/v1/concerts/*/schedules/*/seats/occupy",
                "/api/v1/tickets/reserve/schedule/*"
            )
    }
}
