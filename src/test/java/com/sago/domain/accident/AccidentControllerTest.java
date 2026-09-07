package com.sago.domain.accident;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 각 테스트는 트랜잭션 롤백으로 정리한다 (UserControllerTest와 동일한 이유).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccidentRepository accidentRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String accessToken;
    private Long userId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(
            User.builder().email("rider@example.com").nickname("라이더").build());
        userId = user.getUserId();
        accessToken = jwtTokenProvider.createAccessToken(userId);
    }

    @Test
    @DisplayName("토큰 없이 사고를 생성할 수 없다")
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/accidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accidentType\":\"VEHICLE\"}"))
            .andExpect(status().isUnauthorized());

        assertThat(accidentRepository.count()).isZero();
    }

    @Test
    @DisplayName("사고 유형만 보내도 생성되고, 발생 시각은 요청 시각으로 채워진다")
    void createWithMinimalPayload() throws Exception {
        mockMvc.perform(post("/api/accidents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accidentType\":\"SINGLE\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accidentId").isNumber())
            .andExpect(jsonPath("$.accidentType").value("SINGLE"))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.occurredAt").isNotEmpty());
    }

    @Test
    @DisplayName("생성된 사고는 토큰 주인에게 귀속된다")
    void createdAccidentBelongsToTokenOwner() throws Exception {
        mockMvc.perform(post("/api/accidents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accidentType\":\"PERSONAL\",\"injurySelf\":\"MINOR\","
                    + "\"latitude\":37.5665,\"longitude\":126.9780,\"memo\":\"신호 대기 중 추돌\"}"))
            .andExpect(status().isCreated());

        List<Accident> saved = accidentRepository.findByUser_UserIdOrderByOccurredAtDesc(userId);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).isOwnedBy(userId)).isTrue();
        assertThat(saved.get(0).getMemo()).isEqualTo("신호 대기 중 추돌");
        assertThat(saved.get(0).getInjurySelf()).isEqualTo(InjuryLevel.MINOR);
    }

    @Test
    @DisplayName("사고 유형이 없으면 400이 내려간다")
    void accidentTypeIsRequired() throws Exception {
        mockMvc.perform(post("/api/accidents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(accidentRepository.count()).isZero();
    }
}
