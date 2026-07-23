package com.back.domain.user.entity

import com.back.global.jpa.converter.EncryptedStringConverter
import com.back.global.jpa.entity.BaseEntity
import jakarta.persistence.*
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Column(name = "id", nullable = false, unique = true)
    var loginId: String,

    @Column(nullable = false, unique = true)
    var email: String,

    @Column(nullable = false)
    var password: String,

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val loginType: LoginType,

    @Column(columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter::class)
    var oauthRefreshToken: String? = null
) : BaseEntity() {

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
        this.name = name
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
        ): User {
            return User(
                loginId = loginId,
                email = email,
                password = password,
                name = name,
                loginType = loginType,
                oauthRefreshToken = null
            )
        }

        fun createOAuth(
            loginId: String,
            email: String,
            password: String,
            name: String,
            loginType: LoginType,
            oauthRefreshToken: String?
        ): User {
            return User(
                loginId = loginId,
                email = email,
                password = password,
                name = name,
                loginType = loginType,
                oauthRefreshToken = oauthRefreshToken
            )
        }
    }
}
