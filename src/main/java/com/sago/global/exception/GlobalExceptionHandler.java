package com.sago.global.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.sago.domain.accident.AccidentNotFoundException;
import com.sago.domain.auth.UnsupportedProviderException;
import com.sago.domain.auth.WithdrawnUserException;
import com.sago.domain.checklist.ChecklistItemNotFoundException;
import com.sago.domain.report.ReportAlreadyConfirmedException;
import com.sago.domain.report.ReportGenerationFailedException;
import com.sago.domain.report.ReportNotFoundException;
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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 전역 예외 처리. 컨트롤러에서 예외가 새어 나가더라도 항상 ErrorResponse 형태로 응답하도록 한다.
 *
 * 도메인 예외는 각자 핸들러를 두고, 등록하지 않은 예외는 맨 아래 {@link #handleUnexpected}가 받는다.
 * 새 도메인 예외에 알맞은 상태 코드가 있으면 여기에 핸들러를 추가하면 된다 — 추가하지 않으면 500이다.
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

    @ExceptionHandler(ReportNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleReportNotFound(ReportNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("REPORT_NOT_FOUND", e.getMessage()));
    }

    /**
     * 확정된 경위서를 고치려 한 경우. 요청 형식은 올바르고 지금 상태에서만 안 되는 일이라 409로 내린다.
     */
    @ExceptionHandler(ReportAlreadyConfirmedException.class)
    public ResponseEntity<ErrorResponse> handleReportAlreadyConfirmed(ReportAlreadyConfirmedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("REPORT_ALREADY_CONFIRMED", e.getMessage()));
    }

    /**
     * AI가 경위서를 만들지 못한 경우. 우리 서버 잘못은 아니라 502로 내려 원인이 외부에 있음을 알리고,
     * 사용자가 할 수 있는 일(다시 시도 또는 직접 작성)을 메시지로 안내한다. 소셜 로그인 실패와 같은 방침이다.
     */
    @ExceptionHandler(ReportGenerationFailedException.class)
    public ResponseEntity<ErrorResponse> handleReportGenerationFailed(ReportGenerationFailedException e) {
        log.warn("경위서 생성 실패", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ErrorResponse("REPORT_GENERATION_FAILED", e.getMessage()));
    }

    // ---- 스프링 MVC가 요청을 해석하다 실패한 경우 (#72) ----
    //
    // 아래 핸들러가 없으면 이 예외들은 Spring Boot의 /error로 다시 디스패치된다. 그 디스패치에서는
    // JWT 필터가 다시 돌지 않아 익명 요청이 되고, 결국 유효한 토큰으로 보낸 요청이 401로 나갔다.
    // 앱은 401을 받으면 재발급 후 로그아웃시키므로, 입력값 하나 틀린 요청이 로그아웃으로 이어진다.
    // 여기서 직접 응답해 원래 상태 코드와 ErrorResponse 형식을 지킨다.

    /**
     * 본문을 읽지 못한 경우 — 깨진 JSON, 없는 enum 값, 타입이 다른 값.
     *
     * Jackson의 메시지에는 내부 클래스 이름과 사용자가 보낸 값이 섞여 있어 그대로 내보내지 않는다.
     * 어느 필드가 문제인지만 알려준다. 필드 이름은 우리 DTO의 이름이라 내보내도 된다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("INVALID_REQUEST", describeUnreadable(e)));
    }

    /**
     * 경로 변수·쿼리 파라미터의 타입이 맞지 않는 경우 (예: /api/accidents/abc).
     *
     * 따로 두지 않으면 원인인 NumberFormatException이 IllegalArgumentException 핸들러에 걸려
     * "For input string" 같은 내부 메시지가 그대로 나간다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("INVALID_REQUEST", e.getName() + " 값의 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("INVALID_REQUEST", "필수 항목이 없습니다: " + e.getRequestPartName()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("INVALID_REQUEST", "필수 항목이 없습니다: " + e.getParameterName()));
    }

    /** multipart 본문 자체가 깨진 경우. 용량 초과는 더 구체적인 핸들러가 먼저 받는다. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException e) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("INVALID_REQUEST", "파일 업로드 요청 형식이 올바르지 않습니다."));
    }

    /**
     * 위에서 처리하지 않은 모든 예외.
     *
     * 스프링이 상태 코드를 알고 있는 예외(없는 경로 404, 지원하지 않는 메서드 405, Content-Type 415 등)는
     * 그 코드를 그대로 쓴다. 나머지는 서버 오류라 원인은 로그로만 남기고 일반 안내를 내린다.
     * 여기가 없으면 예상하지 못한 예외가 500이 아니라 401로 나간다.
     *
     * <p><b>메서드 보안(@PreAuthorize 등)을 도입할 때 주의:</b> 지금은 접근 통제를 서비스 계층
     * (getOwnedAccident 등)에서 하고, 스프링 시큐리티 예외는 필터 체인에서만 나서 여기에 닿지 않는다.
     * 메서드 보안을 쓰면 AccessDeniedException이 디스패치 안에서 나 이 폴백에 걸리고,
     * 403이어야 할 응답이 500으로 나가며 서버 오류 로그까지 남는다. 그때는 AccessDeniedException
     * 핸들러를 이 메서드보다 먼저 추가해야 한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse known
            && known.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(known.getStatusCode())
                .headers(known.getHeaders())
                .body(describeClientError(known.getStatusCode().value()));
        }
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    private static ErrorResponse describeClientError(int status) {
        return switch (status) {
            case 404 -> new ErrorResponse("NOT_FOUND", "요청한 경로를 찾을 수 없습니다.");
            case 405 -> new ErrorResponse("METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다.");
            case 406 -> new ErrorResponse("NOT_ACCEPTABLE", "지원하지 않는 응답 형식입니다.");
            case 415 -> new ErrorResponse("UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 Content-Type입니다.");
            default -> new ErrorResponse("INVALID_REQUEST", "요청이 올바르지 않습니다.");
        };
    }

    /**
     * 읽지 못한 본문에서 문제가 된 필드 경로를 뽑는다 (예: "accidentType", "photos[0].takenAt").
     * 경로를 알 수 없으면(깨진 JSON 등) 일반 안내로 대신한다.
     */
    private static String describeUnreadable(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            StringBuilder path = new StringBuilder();
            for (JsonMappingException.Reference reference : mismatch.getPath()) {
                if (reference.getFieldName() != null) {
                    if (!path.isEmpty()) {
                        path.append('.');
                    }
                    path.append(reference.getFieldName());
                } else if (reference.getIndex() >= 0) {
                    path.append('[').append(reference.getIndex()).append(']');
                }
            }
            // DTO에 없는 키가 경로에 섞여 들어오는 경우에 대비해, 식별자로 쓸 수 있는 문자만 허용한다.
            if (path.toString().matches("[A-Za-z0-9_.\\[\\]]{1,100}")) {
                return path + " 값의 형식이 올바르지 않습니다.";
            }
        }
        return "요청 본문을 읽을 수 없습니다. JSON 형식을 확인해주세요.";
    }
}
