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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @DisplayName("사고 이력에는 내 사고만, 최신순으로 나온다")
    void historyContainsOnlyMyAccidentsInRecentOrder() throws Exception {
        User stranger = userRepository.save(User.builder().email("other@example.com").build());
        accidentRepository.save(accident(user(), LocalDateTime.of(2026, 9, 1, 10, 0)));
        accidentRepository.save(accident(user(), LocalDateTime.of(2026, 9, 5, 10, 0)));
        accidentRepository.save(accident(stranger, LocalDateTime.of(2026, 9, 6, 10, 0)));

        mockMvc.perform(get("/api/accidents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].occurredAt").value(org.hamcrest.Matchers.startsWith("2026-09-05")))
            .andExpect(jsonPath("$[1].occurredAt").value(org.hamcrest.Matchers.startsWith("2026-09-01")));
    }

    @Test
    @DisplayName("토큰 없이 사고 이력을 볼 수 없다")
    void historyRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/accidents"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 사고 상세는 조회된다")
    void detailOfMyAccident() throws Exception {
        Long id = accidentRepository.save(accident(user(), LocalDateTime.of(2026, 9, 5, 10, 0)))
            .getAccidentId();

        mockMvc.perform(get("/api/accidents/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accidentId").value(id))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("남의 사고 상세는 404다")
    void detailOfOthersAccidentIsNotFound() throws Exception {
        User stranger = userRepository.save(User.builder().email("other@example.com").build());
        Long id = accidentRepository.save(accident(stranger, LocalDateTime.now())).getAccidentId();

        mockMvc.perform(get("/api/accidents/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ACCIDENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("없는 사고를 조회해도 같은 404가 나온다")
    void unknownAccidentIsNotFound() throws Exception {
        mockMvc.perform(get("/api/accidents/{id}", 999999L)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ACCIDENT_NOT_FOUND"));
    }

    private User user() {
        return userRepository.findById(userId).orElseThrow();
    }

    private Accident accident(User owner, LocalDateTime occurredAt) {
        return Accident.builder()
            .user(owner)
            .accidentType(AccidentType.VEHICLE)
            .occurredAt(occurredAt)
            .build();
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
