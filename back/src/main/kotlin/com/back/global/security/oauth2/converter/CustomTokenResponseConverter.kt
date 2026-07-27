package com.back.global.security.oauth2.converter

import org.springframework.core.convert.converter.Converter
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames

class CustomTokenResponseConverter : Converter<Map<String, Any>, OAuth2AccessTokenResponse> {
    override fun convert(tokenResponseParameters: Map<String, Any>): OAuth2AccessTokenResponse {
        val accessToken = tokenResponseParameters[OAuth2ParameterNames.ACCESS_TOKEN] as String
        val expiresIn = tokenResponseParameters[OAuth2ParameterNames.EXPIRES_IN]
            ?.toString()
            ?.toLong()
            ?: 0L
        val scopes = tokenResponseParameters[OAuth2ParameterNames.SCOPE]
            ?.toString()
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

        val additionalParameters = tokenResponseParameters
            .filterKeys { it !in TOKEN_RESPONSE_PARAMETER_NAMES }
            .toMutableMap()

        val refreshTokenObj = tokenResponseParameters[OAuth2ParameterNames.REFRESH_TOKEN]
        if (refreshTokenObj != null) {
            additionalParameters[OAuth2ParameterNames.REFRESH_TOKEN] = refreshTokenObj
        }

        val builder = OAuth2AccessTokenResponse.withToken(accessToken)
            .tokenType(OAuth2AccessToken.TokenType.BEARER)
            .expiresIn(expiresIn)
            .scopes(scopes)
            .additionalParameters(additionalParameters)

        if (refreshTokenObj is String) {
            builder.refreshToken(refreshTokenObj)
        }

        return builder.build()
    }

    companion object {
        private val TOKEN_RESPONSE_PARAMETER_NAMES = setOf(
            OAuth2ParameterNames.ACCESS_TOKEN,
            OAuth2ParameterNames.TOKEN_TYPE,
            OAuth2ParameterNames.EXPIRES_IN,
            OAuth2ParameterNames.REFRESH_TOKEN,
            OAuth2ParameterNames.SCOPE,
        )
    }
}
