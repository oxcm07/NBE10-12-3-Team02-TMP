package com.back.global.security

import org.springframework.security.core.userdetails.User

class SecurityUser(
    val id: Long,
    val name: String
) : User(id.toString(), "", emptyList())
