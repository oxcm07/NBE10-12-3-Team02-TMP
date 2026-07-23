package com.back.global.exception

enum class ErrorCode(
    val resultCode: String,
    val message: String
) {
    // global
    BAD_REQUEST("400-4", "잘못된 요청입니다."),

    // Auth
    AUTH_PASSWORD_MISMATCH("401-2", "비밀번호가 일치하지 않습니다."),
    AUTH_INVALID_BEARER_HEADER("401-3", "Authorization 헤더가 Bearer 형식이 아닙니다."),
    AUTH_INVALID_REFRESH_TOKEN("401-5", "유효하지 않은 리프레시 토큰입니다."),
    AUTH_REFRESH_TOKEN_MISMATCH("401-7", "리프레시 토큰이 일치하지 않습니다."),
    AUTH_LOGIN_REQUIRED("401-8", "로그인 후 이용해주세요."),
    AUTH_EXPIRED_ACCESS_TOKEN("401-9", "Access Token이 만료되었습니다."),
    AUTH_INVALID_ACCESS_TOKEN("401-10", "유효하지 않은 Access Token입니다."),
    AUTH_FORBIDDEN("403-1", "권한이 없습니다."),
    AUTH_REFRESH_TOKEN_ROTATION_FAILED("500-1", "리프레시 토큰 교체 처리 중 오류가 발생했습니다."),

    // User
    USER_NOT_FOUND("404-1", "회원이 존재하지 않습니다."),
    USER_ID_ALREADY_EXISTS("409-1", "이미 사용 중인 아이디입니다."),
    USER_EMAIL_ALREADY_EXISTS("409-2", "이미 사용 중인 이메일입니다."),
    USER_NOT_FOUND_OR_DELETED("404-2", "존재하지 않거나 이미 탈퇴한 회원입니다."),
    USER_NAME_INVALID("400-6", "이름에 공백을 포함할 수 없습니다."),

    // Concert
    CONCERT_NOT_FOUND("404-3", "존재하지 않는 콘서트입니다."),

    // Schedule
    CONCERT_SCHEDULE_EMPTY("404-4", "등록된 회차가 없습니다."),
    INVALID_CONCERT_SCHEDULE("400-1", "해당 콘서트의 회차가 아닙니다."),
    CONCERT_NOT_FOUND_OR_MISMATCH("400-4", "콘서트 정보가 없거나 일치하는 콘서트가 아닙니다."),
    EXPIRED_BOOKING_DEADLINE("400-9", "해당 회차의 예매 가능 시간이 경과되었습니다."),

    // Seat
    SEAT_NOT_FOUND("404-7", "존재하지 않는 좌석입니다."),
    SEAT_ALREADY_SOLD("409-3", "이미 판매 완료된 좌석입니다."),
    SEAT_HELD_BY_OTHER_USER("409-5", "다른 사용자가 선택 중인 좌석입니다."),
    SEAT_HOLD_EXPIRED("409-6", "좌석 점유가 만료되었습니다."),
    INVALID_OCCUPY_TOKEN("409-7", "유효하지 않은 점유 토큰입니다."),

    // Ticket
    TICKET_NOT_FOUND("404-5", "존재하지 않는 티켓입니다."),
    TICKET_NOT_FOUND_FOR_USER("404-6", "해당 유저의 티켓이 존재하지 않습니다."),
    TICKET_ALREADY_CANCELLED("400-3", "이미 취소된 티켓입니다."),
    EXCEED_TICKET_LIMIT("400-2", "회차당 최대 3매까지 예매 가능합니다."),

    // bucket4j
    TOO_MANY_REQUESTS("429-1", "요청이 너무 많습니다."),

    // QUEUE
    QUEUE_TOKEN_NOT_FOUND("401-11", "대기열 접속 정보가 올바르지 않습니다."),
    QUEUE_SESSION_EXPIRED("403-3", "대기열 접속 시간이 만료되었습니다."),

    // WAITING
    WAITING_QUEUE_NOT_FOUND("404-8", "대기열에 등록되지 않은 사용자입니다."),
    WAITING_QUEUE_REGISTER_FAILED("500-2", "대기열 등록 처리 중 오류가 발생했습니다."),
    CONCERT_SOLD_OUT("400-5", "콘서트가 매진되어 대기열이 종료되었습니다.");

    val statusCode: Int
        get() = resultCode.split("-", limit = 2)[0].toInt()
}
