package com.sago.domain.user;

import com.sago.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업로드가 실패했을 때 사용자가 무엇을 받는지 확인한다.
 *
 * 이 테스트가 없던 동안 모든 업로드 실패가 500으로 나가고 있었다. 검증 메시지는 만들어져
 * 있었지만 전역 핸들러에 등록되지 않아 응답에 실리지 않았고, 목 기반 서비스 테스트로는
 * 서비스가 예외를 던지는 것까지만 확인되어 드러나지 않았다.
 *
 * 실패 경로만 다룬다. 성공 경로는 실제 S3 버킷이 있어야 해서 여기서 확인할 수 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileImageUploadErrorTest {

    private static final String ENDPOINT = "/api/users/me/profile-image";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String accessToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(
            User.builder().email("rider@example.com").nickname("라이더").build());
        accessToken = jwtTokenProvider.createAccessToken(user.getUserId());
    }

    @Test
    @DisplayName("용량 상한을 넘으면 400과 함께 무엇이 문제인지 알려준다")
    void oversizedImageIsRejectedWithReason() throws Exception {
        // PROFILE_IMAGE 상한은 5MB. 요즘 휴대폰 사진 원본이 넘는 경우가 흔하다.
        byte[] oversized = new byte[6 * 1024 * 1024];

        mockMvc.perform(multipart(ENDPOINT)
                .file(new MockMultipartFile("image", "photo.jpg", "image/jpeg", oversized))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FILE"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("용량")));
    }

    @Test
    @DisplayName("허용되지 않은 확장자는 400과 함께 허용 목록을 알려준다")
    void unsupportedExtensionIsRejectedWithAllowedList() throws Exception {
        // 아이폰 기본 촬영 포맷인 heic는 PROFILE_IMAGE 허용 목록에 없다.
        mockMvc.perform(multipart(ENDPOINT)
                .file(new MockMultipartFile("image", "photo.heic", "image/heic", new byte[] {1, 2, 3}))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FILE"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("허용")));
    }

    @Test
    @DisplayName("빈 파일은 400으로 거부된다")
    void emptyFileIsRejected() throws Exception {
        mockMvc.perform(multipart(ENDPOINT)
                .file(new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[0]))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FILE"));
    }

    @Test
    @DisplayName("확장자가 없는 파일도 400으로 거부된다")
    void fileWithoutExtensionIsRejected() throws Exception {
        mockMvc.perform(multipart(ENDPOINT)
                .file(new MockMultipartFile("image", "photo", "image/jpeg", new byte[] {1, 2, 3}))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FILE"));
    }

    @Test
    @DisplayName("토큰 없이 업로드하면 401이다")
    void uploadRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart(ENDPOINT)
                .file(new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3})))
            .andExpect(status().isUnauthorized());
    }
}
