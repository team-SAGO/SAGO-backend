package com.sago.global.exception;

import jakarta.servlet.http.Part;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 멀티파트 상한 초과 시 사용자가 받는 안내를 확인한다.
 *
 * MockMvc로는 이 경로를 재현할 수 없다. 상한 검사는 서블릿 컨테이너(Tomcat)가 요청을
 * 파싱할 때 하는데, MockMvc는 그 단계를 거치지 않는다. 그래서 핸들러를 직접 부른다.
 */
class UploadLimitResponseTest {

    @Test
    @DisplayName("Spring은 상한 초과 예외에 실제 상한 값을 담지 않는다 (-1)")
    void springDoesNotCarryTheLimitInTheException() {
        // 상한을 설정값에서 읽는 이유를 고정해 둔다. 이 전제가 바뀌면 이 테스트가 먼저 알려준다.
        MockHttpServletRequest tooLarge = new MockHttpServletRequest() {
            @Override
            public Collection<Part> getParts() {
                // Tomcat이 파일 상한을 넘었을 때 던지는 메시지 형식
                throw new IllegalStateException(
                    "The field image exceeds its maximum permitted size of 20971520 bytes.");
            }
        };
        tooLarge.setMethod("POST");
        tooLarge.setContentType("multipart/form-data; boundary=x");

        assertThatThrownBy(() -> new StandardMultipartHttpServletRequest(tooLarge))
            .isInstanceOf(MaxUploadSizeExceededException.class)
            .satisfies(e -> assertThat(((MaxUploadSizeExceededException) e).getMaxUploadSize())
                .isEqualTo(-1));
    }

    @Test
    @DisplayName("파일당 상한과 요청 전체 상한을 함께 알려준다")
    void describesBothLimits() {
        ResponseEntity<ErrorResponse> response =
            handler(DataSize.ofMegabytes(20), DataSize.ofMegabytes(105))
                .handleMaxUploadSize(new MaxUploadSizeExceededException(-1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().code()).isEqualTo("FILE_TOO_LARGE");
        // 어느 상한에 걸렸는지 예외로는 구분할 수 없어 둘 다 말한다.
        assertThat(response.getBody().message())
            .isEqualTo("파일 용량이 너무 큽니다. 파일 1개당 최대 20MB, 한 번에 최대 105MB까지 올릴 수 있습니다.");
    }

    @Test
    @DisplayName("상한이 하나만 설정돼 있으면 그것만 알려준다")
    void describesOnlyConfiguredLimit() {
        ErrorResponse body = handler(DataSize.ofMegabytes(20), DataSize.ofBytes(-1))
            .handleMaxUploadSize(new MaxUploadSizeExceededException(-1))
            .getBody();

        assertThat(body.message()).isEqualTo("파일 용량이 너무 큽니다. 파일 1개당 최대 20MB까지 올릴 수 있습니다.");
    }

    @Test
    @DisplayName("상한이 설정돼 있지 않으면 일반 안내로 대신한다")
    void fallsBackWhenNoLimitIsConfigured() {
        ErrorResponse body = handler(DataSize.ofBytes(-1), DataSize.ofBytes(-1))
            .handleMaxUploadSize(new MaxUploadSizeExceededException(-1))
            .getBody();

        assertThat(body.message()).isEqualTo("파일 용량이 너무 큽니다. 더 작은 파일로 다시 시도해주세요.");
    }

    private GlobalExceptionHandler handler(DataSize maxFileSize, DataSize maxRequestSize) {
        MultipartProperties properties = new MultipartProperties();
        properties.setMaxFileSize(maxFileSize);
        properties.setMaxRequestSize(maxRequestSize);
        return new GlobalExceptionHandler(properties);
    }
}
