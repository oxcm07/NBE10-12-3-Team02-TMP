package com.back.global.security.oauth2.converter

import org.springframework.core.convert.converter.Converter
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames

class CustomTokenResponseConverter : Converter<Map<String, Any>, OAuth2AccessTokenResponse> {

    override fun convert(tokenResponseParameters: Map<String, Any>): OAuth2AccessTokenResponse {
        val accessToken = tokenResponseParameters[OAuth2ParameterNames.ACCESS_TOKEN] as String
        val tokenType = OAuth2AccessToken.TokenType.BEARER

        var expiresIn = 0L
        if (tokenResponseParameters.containsKey(OAuth2ParameterNames.EXPIRES_IN)) {
            expiresIn = tokenResponseParameters[OAuth2ParameterNames.EXPIRES_IN].toString().toLong()
        }

        var scopes: Set<String> = emptySet()
        if (tokenResponseParameters.containsKey(OAuth2ParameterNames.SCOPE)) {
            val scope = tokenResponseParameters[OAuth2ParameterNames.SCOPE] as String
            scopes = scope.split(" ").toSet()
        }

        val additionalParameters = LinkedHashMap<String, Any>()
        tokenResponseParameters.forEach { (key, value) ->
            if (!TOKEN_RESPONSE_PARAMETER_NAMES.contains(key)) {
                additionalParameters[key] = value
            }
        }

        if (tokenResponseParameters.containsKey(OAuth2ParameterNames.REFRESH_TOKEN)) {
            additionalParameters[OAuth2ParameterNames.REFRESH_TOKEN] =
                tokenResponseParameters[OAuth2ParameterNames.REFRESH_TOKEN]!!
        }

        val builder = OAuth2AccessTokenResponse.withToken(accessToken)
            .tokenType(tokenType)
            .expiresIn(expiresIn)
            .scopes(scopes)
            .additionalParameters(additionalParameters)

        if (tokenResponseParameters.containsKey(OAuth2ParameterNames.REFRESH_TOKEN)) {
            builder.refreshToken(tokenResponseParameters[OAuth2ParameterNames.REFRESH_TOKEN] as String)
        }

        return builder.build()
    }

    companion object {
        private val TOKEN_RESPONSE_PARAMETER_NAMES = setOf(
            OAuth2ParameterNames.ACCESS_TOKEN,
            OAuth2ParameterNames.TOKEN_TYPE,
            OAuth2ParameterNames.EXPIRES_IN,
            OAuth2ParameterNames.REFRESH_TOKEN,
            OAuth2ParameterNames.SCOPE
        )
    }
}
