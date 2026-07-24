package com.back.global.requestcontext

import com.back.global.security.SecurityUser
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class RequestContext(
    private val reqProvider: ObjectProvider<HttpServletRequest>,
    private val respProvider: ObjectProvider<HttpServletResponse>,
    @Value("\${custom.cookie.secure:false}") private val cookieSecure: Boolean,
    @Value("\${custom.cookie.same-site:Lax}") private val cookieSameSite: String,
    @Value("\${custom.jwt.refreshToken.expirationSeconds}") private val refreshTokenExpireSeconds: Int
) {
    private fun req(): HttpServletRequest = reqProvider.getObject()
    private fun resp(): HttpServletResponse = respProvider.getObject()

    val actor: SecurityUser?
        get() = SecurityContextHolder.getContext().authentication?.principal as? SecurityUser

    fun getHeader(name: String, defaultValue: String = ""): String {
        return req().getHeader(name)?.takeIf { it.isNotBlank() } ?: defaultValue
    }

    fun setHeader(name: String, value: String?) {
        val finalValue = value ?: ""
        if (finalValue.isBlank()) {
            req().removeAttribute(name)
        } else {
            resp().setHeader(name, finalValue)
        }
    }

    fun getCookieValue(name: String, defaultValue: String = ""): String {
        val cookies = req().cookies ?: emptyArray()
        return cookies.firstOrNull { it.name == name }
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?: defaultValue
    }

    fun setCookieWithMaxAge(name: String, value: String?, path: String, maxAge: Int) {
        val finalValue = value ?: ""
        val cookie = Cookie(name, finalValue).apply {
            this.path = path
            isHttpOnly = true
            secure = cookieSecure
            setAttribute("SameSite", cookieSameSite)
            this.maxAge = if (finalValue.isBlank()) 0 else maxAge
        }
        resp().addCookie(cookie)
    }

    fun setCookie(name: String, value: String?, path: String) {
        setCookieWithMaxAge(name, value, path, refreshTokenExpireSeconds)
    }

    fun deleteCookie(name: String, path: String) {
        setCookie(name, null, path)
    }

    val clientIp: String
        get() {
            val forwardedFor = getHeader("X-Forwarded-For", "")
            return if (forwardedFor.isNotBlank()) {
                forwardedFor.split(",")[0].trim()
            } else {
                req().remoteAddr
            }
        }
}
