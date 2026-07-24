package com.back.global.exception

import com.back.global.rsData.RsData
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ServiceException::class)
    fun handle(ex: ServiceException, response: HttpServletResponse): RsData<Void> =
        ex.rsData.also { response.status = it.statusCode }
}
