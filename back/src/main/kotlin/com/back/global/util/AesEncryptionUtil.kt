package com.back.global.util

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class AesEncryptionUtil(
    @Value("\${custom.oauth.token.encryption-key}") encryptionKey: String
) {
    private val secretKeySpec: SecretKeySpec =
        SecretKeySpec(Base64.getDecoder().decode(encryptionKey), "AES")

    fun encrypt(plainText: String): String = runCatching {
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.ENCRYPT_MODE, secretKeySpec, IvParameterSpec(iv))
        }

        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        Base64.getEncoder().encodeToString(iv + encrypted)
    }.getOrElse { throw RuntimeException("암호화 실패", it) }

    fun decrypt(encryptedText: String): String = runCatching {
        val combined = Base64.getDecoder().decode(encryptedText)
        val iv = combined.copyOfRange(0, 16)
        val encrypted = combined.copyOfRange(16, combined.size)

        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.DECRYPT_MODE, secretKeySpec, IvParameterSpec(iv))
        }

        String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }.getOrElse { throw RuntimeException("복호화 실패", it) }

    companion object {
        private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    }
}
