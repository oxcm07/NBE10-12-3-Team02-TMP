package com.back.domain.user.dto

import com.back.domain.user.entity.User

data class MyPageResponse(
    val name: String,
    val id: String,
    val email: String,
    val loginType: String,
    val ticketGroups: List<TicketGroupInfo>
) {
    companion object {
        fun from(user: User, ticketGroups: List<TicketGroupInfo>): MyPageResponse {
            return MyPageResponse(
                name = user.name,
                id = user.loginId,
                email = user.email,
                loginType = user.loginType.name,
                ticketGroups = ticketGroups
            )
        }
    }
}
