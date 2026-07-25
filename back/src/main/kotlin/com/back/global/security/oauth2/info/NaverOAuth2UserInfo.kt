package com.back.global.security.oauth2.info

class NaverOAuth2UserInfo(
    private val attributes: Map<String, Any>,
) : OAuth2UserInfo {
    override val providerId: String?
        get() = responseValue("id")

    override val email: String?
        get() = responseValue("email")

    override val name: String
        get() = responseValue("name")
            ?.takeIf { it.isNotBlank() }
            ?: responseValue("nickname")
                ?.takeIf { it.isNotBlank() }
            ?: "네이버사용자"

    private val response: Map<*, *>
        get() = attributes["response"] as? Map<*, *> ?: emptyMap<Any, Any>()

    private fun responseValue(key: String): String? =
        response[key]?.toString()
}
