package com.back.global.security.filter

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationFilterSkipMatcher {

    fun shouldSkip(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        val method = request.method

        return method == "OPTIONS" ||
                path.startsWith("/oauth2/") ||
                path.startsWith("/login/oauth2/") ||
                (method == "GET" && (
                        path.matches(Regex("/api/[^/]+/concerts")) ||
                                path.matches(Regex("/api/[^/]+/concerts/\\d+")) ||
                                path.matches(Regex("/api/[^/]+/schedules/.*")) ||
                                path.matches(Regex("/api/[^/]+/users/check-id"))
                        )) ||
                (method == "POST" && (
                        path.matches(Regex("/api/[^/]+/auth/login")) ||
                                path.matches(Regex("/api/[^/]+/auth/refresh")) ||
                                path.matches(Regex("/api/[^/]+/auth/logout")) ||
                                path.matches(Regex("/api/[^/]+/users/signup")) ||
                                path.matches(Regex("/api/[^/]+/auth/restore"))
                        ))
    }
}
