package com.sago.global.exception;

import com.sago.domain.accident.AccidentNotFoundException;
import com.sago.domain.auth.UnsupportedProviderException;
import com.sago.domain.auth.WithdrawnUserException;
import com.sago.domain.checklist.ChecklistItemNotFoundException;
import com.sago.domain.terms.RequiredTermsNotAgreedException;
import com.sago.domain.user.UserNotFoundException;
import com.sago.global.client.oauth.OAuthApiException;
import com.sago.global.client.s3.S3CommunicationException;
import com.sago.global.client.s3.S3ValidationException;
import com.sago.global.jwt.InvalidTokenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 전역 예외 처리. 컨트롤러에서 예외가 새어 나가더라도 항상 ErrorResponse 형태로 응답하도록 한다.
 *
 * 인증 관련 기능만 우선 등록해 두었다. 다른 도메인 기능이 붙으면 여기에 핸들러를 추가하면 된다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MultipartProperties multipartProperties;

    public GlobalExceptionHandler(MultipartProperties multipartProperties) {
        this.multipartProperties = multipartProperties;
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("INVALID_TOKEN", e.getMessage()));
    }

    @ExceptionHandler(UnsupportedProviderException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedProvider(UnsupportedProviderException e) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("UNSUPPORTED_PROVIDER", e.getMessage()));
    }

    @ExceptionHandler(AccidentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccidentNotFound(AccidentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("ACCIDENT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(ChecklistItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleChecklistItemNotFound(
        ChecklistItemNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("CHECKLIST_ITEM_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("USER_NOT_FOUND", e.getMessage()));
    }

    /**
     * 필수 약관 미동의. 요청 형식은 올바르므로 400이 아니라 422로 내려, 클라이언트가
     * "입력값이 잘못됨"과 "동의가 부족함"을 구분해 안내할 수 있게 한다.
     */
    @ExceptionHandler(RequiredTermsNotAgreedException.class)
    public ResponseEntity<ErrorResponse> handleRequiredTermsNotAgreed(
        RequiredTermsNotAgreedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse("REQUIRED_TERMS_NOT_AGREED", e.getMessage()));
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

    /**
     * 업로드 파일이 확장자·용량·개수 조건에 맞지 않는 경우.
     *
     * 사용자가 조치할 수 있는 실패라 메시지를 그대로 내려준다. FileCategory가 현재 용량과
     * 상한, 허용 형식처럼 무엇을 고쳐야 하는지 담아 던지므로, 일반화된 문구로 덮으면
     * 프론트가 안내할 근거를 잃는다.
     */
    @ExceptionHandler(S3ValidationException.class)
    public ResponseEntity<ErrorResponse> handleS3Validation(S3ValidationException e) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("INVALID_FILE", e.getMessage()));
    }

    /**
     * S3 통신·권한 오류. 사용자가 할 수 있는 게 없고 내부 사정이 드러나면 안 되므로,
     * 원인은 로그로만 남기고 일반화된 안내를 내려준다. 소셜 로그인 실패와 같은 방침이다.
     */
    @ExceptionHandler(S3CommunicationException.class)
    public ResponseEntity<ErrorResponse> handleS3Communication(S3CommunicationException e) {
        log.warn("파일 저장소 처리 실패", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ErrorResponse("FILE_STORAGE_FAILED", "파일 처리에 실패했습니다. 잠시 후 다시 시도해주세요."));
    }

    /**
     * multipart 상한을 넘은 요청.
     *
     * MultipartException 계열이라 Spring 기본 예외 해석기가 처리하지 않아, 등록하지 않으면
     * 500으로 나간다. FileCategory의 종류별 검증에 닿기도 전에 잘리는 경로다.
     *
     * 상한은 예외가 아니라 설정값에서 읽는다. Spring의 StandardMultipartHttpServletRequest가
     * 이 예외를 항상 maxUploadSize=-1로 만들어 던지기 때문에 getMaxUploadSize()로는 알 수 없다.
     * 또 파일 하나의 상한과 요청 전체의 상한 중 무엇에 걸렸는지도 예외로는 구분되지 않아
     * 둘 다 알려준다 — 한쪽만 말하면 다른 쪽에 걸린 사용자는 줄여도 계속 막힌다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new ErrorResponse("FILE_TOO_LARGE", describeUploadLimit()));
    }

    private String describeUploadLimit() {
        long perFile = megabytes(multipartProperties.getMaxFileSize());
        long perRequest = megabytes(multipartProperties.getMaxRequestSize());

        if (perFile > 0 && perRequest > 0) {
            return "파일 용량이 너무 큽니다. 파일 1개당 최대 " + perFile + "MB, "
                + "한 번에 최대 " + perRequest + "MB까지 올릴 수 있습니다.";
        }
        if (perFile > 0) {
            return "파일 용량이 너무 큽니다. 파일 1개당 최대 " + perFile + "MB까지 올릴 수 있습니다.";
        }
        if (perRequest > 0) {
            return "파일 용량이 너무 큽니다. 한 번에 최대 " + perRequest + "MB까지 올릴 수 있습니다.";
        }
        return "파일 용량이 너무 큽니다. 더 작은 파일로 다시 시도해주세요.";
    }

    /** 상한이 설정되지 않았거나(-1) 1MB 미만이면 0을 돌려 안내에서 뺀다. */
    private static long megabytes(DataSize size) {
        return (size == null || size.toBytes() <= 0) ? 0 : size.toMegabytes();
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
