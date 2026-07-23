package com.back.domain.user.repository

import com.back.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {
    fun existsByLoginIdAndDeletedAtIsNull(loginId: String): Boolean
    fun existsByEmailAndDeletedAtIsNull(email: String): Boolean
    fun findByUserIdAndDeletedAtIsNull(userId: Long): User?

    @Query("SELECT u FROM User u WHERE u.loginId = :loginId AND u.deletedAt IS NULL")
    fun findByLoginIdAndDeletedAtIsNull(@Param("loginId") loginId: String): User?
}
