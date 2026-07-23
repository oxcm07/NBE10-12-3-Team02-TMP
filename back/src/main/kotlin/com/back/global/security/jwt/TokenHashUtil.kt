package com.back.global.security.jwt

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

object TokenHashUtil {
    fun sha256(token: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(token.toByteArray(StandardCharsets.UTF_8))
            HexFormat.of().formatHex(hash)
        } catch (e: Exception) {
            throw RuntimeException("토큰 해싱 실패", e)
        }
    }
}
