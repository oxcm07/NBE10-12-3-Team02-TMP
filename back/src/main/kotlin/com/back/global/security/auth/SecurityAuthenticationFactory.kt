package com.back.global.security.auth

import com.back.global.security.SecurityUser
import com.back.global.security.jwt.payload.AccessTokenPayload
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class SecurityAuthenticationFactory {
    fun create(payload: AccessTokenPayload): Authentication {
        val securityUser = SecurityUser(
            id = payload.userId,
            name = payload.name
        )

        return UsernamePasswordAuthenticationToken(
            securityUser,
            null,
            securityUser.authorities
        )
    }
}
