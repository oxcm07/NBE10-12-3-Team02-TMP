package com.back.global.util

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.*

object Ut {
    object jwt {
        fun toString(secret: String, expireSeconds: Int, body: Map<String, Any>): String {
            val claimsBuilder = Jwts.claims()
            body.forEach { (key, value) -> claimsBuilder.add(key, value) }
            val claims = claimsBuilder.build()

            val issuedAt = Date()
            val expiration = Date(issuedAt.time + 1000L * expireSeconds)
            val secretKey = Keys.hmacShaKeyFor(secret.toByteArray())

            return Jwts.builder()
                .claims(claims)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(secretKey)
                .compact()
        }

        fun payload(secret: String, jwtStr: String): Map<String, Any> {
            val secretKey = Keys.hmacShaKeyFor(secret.toByteArray())
            return LinkedHashMap(
                Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(jwtStr)
                    .payload
            )
        }
    }

    object json {
        val objectMapper: ObjectMapper = ObjectMapper()

        fun toString(obj: Any?, defaultValue: String? = null): String? {
            return try {
                objectMapper.writeValueAsString(obj)
            } catch (e: Exception) {
                defaultValue
            }
        }
    }
}
