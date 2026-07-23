package com.back.global.security.oauth2.info

class KakaoOAuth2UserInfo(
    private val attributes: Map<String, Any>
) : OAuth2UserInfo {

    override val providerId: String?
        get() = attributes["id"]?.toString()

    override val email: String?
        get() = kakaoAccount["email"]?.toString()

    override val name: String
        get() {
            val profile = kakaoAccount["profile"] as? Map<*, *> ?: return "카카오사용자"
            return profile["nickname"]?.toString() ?: "카카오사용자"
        }

    private val kakaoAccount: Map<*, *>
        get() = (attributes["kakao_account"] as? Map<*, *>) ?: emptyMap<Any, Any>()
}
