package com.sago.domain.auth;

import com.sago.domain.user.User;
import com.sago.domain.user.UserRepository;
import com.sago.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그아웃·탈퇴가 실제로 토큰을 무효화하는지 요청 단위로 확인한다.
 *
 * 이 흐름이 이 작업의 핵심이다. 저장소가 없던 이전에는 로그아웃을 눌러도 서버가 아무것도
 * 모르는 상태여서, 이미 발급된 refresh 토큰으로 계속 재발급을 받을 수 있었다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User user;
    private String refreshToken;
    private String accessToken;

    @BeforeEach
    void setUp() {
        user = userRepository.save(
            User.builder().email("rider@example.com").nickname("라이더").build());
        refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());
        accessToken = jwtTokenProvider.createAccessToken(user.getUserId());
        refreshTokenStore.save(user, refreshToken);
    }

    @Test
    @DisplayName("저장된 refresh 토큰으로 재발급받을 수 있다")
    void reissueWithStoredToken() throws Exception {
        mockMvc.perform(post("/api/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(refreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("로그아웃하면 그 토큰으로는 재발급받을 수 없다")
    void reissueIsBlockedAfterLogout() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(refreshToken)))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(refreshToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("로그아웃은 인증 없이 호출할 수 있다")
    void logoutDoesNotRequireAccessToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(refreshToken)))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("로그아웃을 두 번 해도 성공으로 응답한다")
    void logoutIsIdempotent() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(refreshToken)))
                .andExpect(status().isNoContent());
        }
    }

    @Test
    @DisplayName("한 번 재발급에 쓴 토큰은 다시 쓸 수 없다")
    void usedRefreshTokenCannotBeReused() throws Exception {
        mockMvc.perform(post("/api/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(refreshToken)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(refreshToken)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("탈퇴하면 남아 있던 토큰으로 재발급받을 수 없다")
    void reissueIsBlockedAfterWithdrawal() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(refreshToken)))
            .andExpect(status().isUnauthorized());

        // 계정은 남되 탈퇴 표시가 된다 — 사고 기록이 참조하고 있어 행을 지울 수 없다.
        assertThat(userRepository.findById(user.getUserId()))
            .get()
            .satisfies(withdrawn -> assertThat(withdrawn.isWithdrawn()).isTrue());
    }

    @Test
    @DisplayName("탈퇴는 인증이 필요하다")
    void withdrawRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/users/me"))
            .andExpect(status().isUnauthorized());
    }

    private String json(String token) {
        return "{\"refreshToken\":\"" + token + "\"}";
    }
}
