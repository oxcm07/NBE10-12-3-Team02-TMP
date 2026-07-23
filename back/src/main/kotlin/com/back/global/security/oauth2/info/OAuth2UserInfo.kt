package com.back.global.security.oauth2.info

interface OAuth2UserInfo {
    val providerId: String?
    val email: String?
    val name: String
}
