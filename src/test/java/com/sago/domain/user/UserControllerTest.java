package com.sago.domain.user;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 필터에서 꺼낸 userId가 컨트롤러 principal까지 실제로 전달되는지, 그리고 토큰 없이는
 * 접근이 막히는지를 확인한다. 서비스 단위 테스트로는 검증되지 않는 구간이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String accessToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User user = userRepository.save(
            User.builder().email("rider@example.com").nickname("라이더").build());
        accessToken = jwtTokenProvider.createAccessToken(user.getUserId());
    }

    @Test
    @DisplayName("토큰 없이 프로필을 조회하면 401이 내려간다")
    void profileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("유효하지 않은 토큰도 401이 내려간다")
    void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("토큰의 주인 프로필이 조회된다")
    void getMyProfile() throws Exception {
        mockMvc.perform(get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("rider@example.com"))
            .andExpect(jsonPath("$.nickname").value("라이더"))
            .andExpect(jsonPath("$.profileSet").value(false));
    }

    @Test
    @DisplayName("프로필을 수정하면 저장되고 profileSet이 true가 된다")
    void updateMyProfile() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"새닉네임\",\"bikeModel\":\"PCX125\",\"bikeNumber\":\"12가3456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("새닉네임"))
            .andExpect(jsonPath("$.bikeNumber").value("12가3456"))
            .andExpect(jsonPath("$.profileSet").value(true));
    }

    @Test
    @DisplayName("차량번호 형식이 맞지 않으면 400이 내려간다")
    void invalidBikeNumberIsRejected() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bikeNumber\":\"ABC-123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
