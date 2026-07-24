package com.back.global.util

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.*

object Ut {
    object jwt {
        fun toString(secret: String, expireSeconds: Int, body: Map<String, Any>): String {
            val issuedAt = Date()
            val expiration = Date(issuedAt.time + 1000L * expireSeconds)
            val secretKey = Keys.hmacShaKeyFor(secret.toByteArray())

            val claims = Jwts.claims().apply {
                body.forEach { (key, value) -> add(key, value) }
            }.build()

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

        fun toString(obj: Any?, defaultValue: String? = null): String? = try {
            objectMapper.writeValueAsString(obj)
        } catch (e: Exception) {
            defaultValue
        }
    }
}

fun Any?.toJson(defaultValue: String? = null): String? = Ut.json.toString(this, defaultValue)
