package com.back.global.exception

import com.back.global.rsData.RsData

class ServiceException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message) {

    val rsData: RsData<Void>
        get() = RsData(
            resultCode = errorCode.resultCode,
            msg = errorCode.message,
            data = null
        )
}
