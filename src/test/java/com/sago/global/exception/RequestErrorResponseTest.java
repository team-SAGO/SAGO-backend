package com.sago.global.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sago.domain.user.User;
import com.sago.domain.user.UserRepository;
import com.sago.global.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 유효한 토큰으로 보낸 잘못된 요청이 원래 상태 코드와 ErrorResponse로 응답되는지 확인한다 (#72).
 *
 * 실제 서버를 띄운다. 이 버그는 서블릿 컨테이너가 오류를 /error로 다시 디스패치할 때 생기는데,
 * MockMvc는 그 단계를 거치지 않아 재현되지 않는다. 기존 컨트롤러 테스트가 이 문제를 놓친 이유다.
 *
 * 실제 요청이 오가 트랜잭션 롤백이 되지 않으므로, 만든 회원은 테스트가 끝나면 지운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RequestErrorResponseTest.FailingController.class)
class RequestErrorResponseTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
            .email("error-" + UUID.randomUUID() + "@example.com")
            .nickname("라이더")
            .build());
        token = jwtTokenProvider.createAccessToken(user.getUserId());
    }

    @AfterEach
    void tearDown() {
        userRepository.delete(user);
    }

    @Test
    @DisplayName("깨진 JSON은 401이 아니라 400이다")
    void malformedJsonIsBadRequest() throws IOException {
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/accidents", token,
            MediaType.APPLICATION_JSON, "{not json");

        assertError(response, 400, "INVALID_REQUEST");
    }

    @Test
    @DisplayName("없는 enum 값은 400이고, 어느 필드가 문제인지 알려준다")
    void unknownEnumValueNamesTheField() throws IOException {
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/accidents", token,
            MediaType.APPLICATION_JSON, "{\"accidentType\":\"<b>NOPE</b>\"}");

        JsonNode body = assertError(response, 400, "INVALID_REQUEST");
        assertThat(body.get("message").asText())
            .contains("accidentType")
            // 사용자가 보낸 값과 Jackson 내부 메시지는 내보내지 않는다
            .doesNotContain("NOPE")
            .doesNotContain("com.sago");
    }

    @Test
    @DisplayName("경로 변수 타입이 틀리면 400이고, 내부 메시지를 내보내지 않는다")
    void pathVariableTypeMismatchHidesInternalMessage() throws IOException {
        ResponseEntity<String> response = send(HttpMethod.GET, "/api/accidents/abc", token, null, null);

        JsonNode body = assertError(response, 400, "INVALID_REQUEST");
        assertThat(body.get("message").asText())
            .contains("accidentId")
            .doesNotContain("For input string");
    }

    @Test
    @DisplayName("multipart 필수 파트가 없으면 400이다")
    void missingMultipartPartIsBadRequest() throws IOException {
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/users/me/profile-image", token,
            new MediaType("multipart", "form-data", Map.of("boundary", "x")), "--x--\r\n");

        JsonNode body = assertError(response, 400, "INVALID_REQUEST");
        assertThat(body.get("message").asText()).contains("image");
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415다")
    void unsupportedMediaTypeIs415() throws IOException {
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/accidents", token,
            MediaType.TEXT_PLAIN, "hello");

        assertError(response, 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405다")
    void unsupportedMethodIs405() throws IOException {
        ResponseEntity<String> response = send(HttpMethod.DELETE, "/api/accidents", token, null, null);

        assertError(response, 405, "METHOD_NOT_ALLOWED");
        // 무엇이 되는지 클라이언트가 알 수 있도록 스프링이 준 Allow 헤더를 유지한다
        assertThat(response.getHeaders().getAllow()).isNotEmpty();
    }

    @Test
    @DisplayName("없는 경로는 404다")
    void unknownPathIs404() throws IOException {
        ResponseEntity<String> response = send(HttpMethod.GET, "/api/does-not-exist", token, null, null);

        assertError(response, 404, "NOT_FOUND");
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500이고 원인은 응답에 싣지 않는다")
    void unexpectedExceptionIs500() throws IOException {
        ResponseEntity<String> response = send(HttpMethod.GET, "/api/test-errors/unexpected", token, null, null);

        JsonNode body = assertError(response, 500, "INTERNAL_ERROR");
        assertThat(body.get("message").asText()).doesNotContain("내부 상태");
    }

    @Test
    @DisplayName("핸들러 밖에서 난 오류도 원래 상태 코드로 나간다 — 에러 디스패치가 401로 바뀌지 않는다")
    void errorDispatchKeepsOriginalStatus() {
        // sendError는 예외 핸들러를 거치지 않고 컨테이너가 곧바로 /error로 디스패치한다.
        // 필터 등에서 난 오류가 이 경로를 탄다.
        ResponseEntity<String> response = send(HttpMethod.GET, "/api/test-errors/send-error", token, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
    }

    @Test
    @DisplayName("토큰이 없으면 잘못된 요청이어도 여전히 401이다 — 에러 경로를 열었다고 인증이 풀리지 않는다")
    void requestWithoutTokenIsStillUnauthorized() {
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/accidents", null,
            MediaType.APPLICATION_JSON, "{not json");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("/error로 직접 들어오는 요청은 토큰이 없으면 401이다")
    void directErrorPathStillRequiresAuthentication() {
        ResponseEntity<String> response = send(HttpMethod.GET, "/error", null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    private ResponseEntity<String> send(HttpMethod method, String path, String bearer,
                                        MediaType contentType, String body) {
        HttpHeaders headers = new HttpHeaders();
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        if (contentType != null) {
            headers.setContentType(contentType);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode assertError(ResponseEntity<String> response, int status, String code) throws IOException {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.get("message").asText()).isNotBlank();
        return body;
    }

    /** 정상 코드로는 만들기 어려운 실패를 일으키는 테스트 전용 엔드포인트. 이 테스트에서만 등록된다. */
    @RestController
    static class FailingController {

        @GetMapping("/api/test-errors/unexpected")
        String unexpected() {
            throw new IllegalStateException("내부 상태가 맞지 않습니다");
        }

        @GetMapping("/api/test-errors/send-error")
        void sendError(HttpServletResponse response) throws IOException {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }
}
