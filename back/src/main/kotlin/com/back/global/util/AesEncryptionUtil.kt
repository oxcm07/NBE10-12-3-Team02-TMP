package com.back.global.util

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class AesEncryptionUtil(
    @Value("\${custom.oauth.token.encryption-key}") encryptionKey: String
) {
    private val secretKeySpec: SecretKeySpec

    init {
        val keyBytes = Base64.getDecoder().decode(encryptionKey)
        this.secretKeySpec = SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainText: String): String {
        try {
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec)

            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)

            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

            return Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            throw RuntimeException("암호화 실패", e)
        }
    }

    fun decrypt(encryptedText: String): String {
        try {
            val combined = Base64.getDecoder().decode(encryptedText)
            val iv = ByteArray(16)
            val encrypted = ByteArray(combined.size - 16)

            System.arraycopy(combined, 0, iv, 0, 16)
            System.arraycopy(combined, 16, encrypted, 0, encrypted.size)

            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec)

            return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw RuntimeException("복호화 실패", e)
        }
    }

    companion object {
        private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    }
}
