package com.sago.global.exception;

import com.sago.domain.auth.WithdrawnUserException;
import com.sago.global.client.oauth.OAuthApiException;
import com.sago.global.jwt.InvalidTokenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리. 컨트롤러에서 예외가 새어 나가더라도 항상 ErrorResponse 형태로 응답하도록 한다.
 *
 * 인증 관련 기능만 우선 등록해 두었다. 다른 도메인 기능이 붙으면 여기에 핸들러를 추가하면 된다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("INVALID_TOKEN", e.getMessage()));
    }

    @ExceptionHandler(WithdrawnUserException.class)
    public ResponseEntity<ErrorResponse> handleWithdrawnUser(WithdrawnUserException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("WITHDRAWN_USER", e.getMessage()));
    }

    /**
     * 소셜 제공자와의 통신 실패. 우리 서버 잘못은 아니지만 클라이언트가 재시도해도 소용없는 경우가 많아
     * 502로 내려 원인이 외부에 있음을 구분할 수 있게 한다.
     */
    @ExceptionHandler(OAuthApiException.class)
    public ResponseEntity<ErrorResponse> handleOAuthApi(OAuthApiException e) {
        log.warn("소셜 로그인 처리 실패", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ErrorResponse("OAUTH_FAILED", "소셜 로그인 처리에 실패했습니다. 잠시 후 다시 시도해주세요."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage())
            .orElse("요청 값이 올바르지 않습니다.");
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("INVALID_REQUEST", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
    }
}
