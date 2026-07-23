package com.back.global.rsData

import com.fasterxml.jackson.annotation.JsonIgnore

data class RsData<T>(
    val resultCode: String,
    @get:JsonIgnore val statusCode: Int,
    val msg: String,
    val data: T?
) {
    constructor(resultCode: String, msg: String, data: T? = null) : this(
        resultCode = resultCode,
        statusCode = resultCode.split("-", limit = 2)[0].toIntOrNull() ?: 200,
        msg = msg,
        data = data
    )
}
