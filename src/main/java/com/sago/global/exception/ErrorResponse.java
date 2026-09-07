package com.sago.global.exception;

/**
 * 에러 응답 공통 형태.
 *
 * @param code    클라이언트가 분기 처리할 수 있는 식별자 (예: INVALID_TOKEN)
 * @param message 사용자에게 보여줄 수 있는 한국어 설명
 */
public record ErrorResponse(String code, String message) {
}
