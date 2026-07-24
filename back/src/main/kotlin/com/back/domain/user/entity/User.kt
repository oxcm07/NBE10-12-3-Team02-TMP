package com.back.domain.user.entity

import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import com.back.global.jpa.converter.EncryptedStringConverter
import com.back.global.jpa.entity.BaseEntity
import jakarta.persistence.*
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    loginId: String,
    email: String,
    password: String,
    name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val loginType: LoginType,

    oauthRefreshToken: String? = null
) : BaseEntity() {

    @Column(name = "id", nullable = false, unique = true)
    var loginId: String = loginId
        protected set

    @Column(nullable = false, unique = true)
    var email: String = email
        protected set

    @Column(nullable = false)
    var password: String = password
        protected set

    @Column(nullable = false)
    var name: String = name
        protected set

    @Column(columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter::class)
    var oauthRefreshToken: String? = oauthRefreshToken
        protected set

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val userId: Long? = null

    var deletedAt: LocalDate? = null
        protected set

    val isDeleted: Boolean
        get() = deletedAt != null

    fun withdraw() {
        val uuid = UUID.randomUUID().toString()
        this.deletedAt = LocalDate.now()
        this.loginId = uuid
        this.email = "$uuid@deleted.local"
        this.oauthRefreshToken = null
    }

    fun updateOauthRefreshToken(oauthRefreshToken: String?) {
        this.oauthRefreshToken = oauthRefreshToken
    }

    fun updateName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.contains(" ")) {
            throw ServiceException(ErrorCode.USER_NAME_INVALID)
        }
        this.name = trimmed
    }

    fun updateEmail(email: String) {
        this.email = email
    }

    fun updatePassword(password: String) {
        this.password = password
    }

    companion object {
        fun create(
            loginId: String,
            email: String,
            password: String,
            name: String,
            loginType: LoginType
        ): User = User(
            loginId = loginId,
            email = email,
            password = password,
            name = name,
            loginType = loginType,
            oauthRefreshToken = null
        )

        fun createOAuth(
            loginId: String,
            email: String,
            password: String,
            name: String,
            loginType: LoginType,
            oauthRefreshToken: String?
        ): User = User(
            loginId = loginId,
            email = email,
            password = password,
            name = name,
            loginType = loginType,
            oauthRefreshToken = oauthRefreshToken
        )
    }
}
