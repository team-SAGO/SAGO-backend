package com.sago.domain.contact;

import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentRepository;
import com.sago.domain.accident.AccidentType;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContactLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccidentRepository accidentRepository;

    @Autowired
    private ContactLogRepository contactLogRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String ownerToken;
    private String strangerToken;
    private Long accidentId;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(
            User.builder().email("rider@example.com").nickname("라이더").build());
        User stranger = userRepository.save(User.builder().email("other@example.com").build());
        ownerToken = jwtTokenProvider.createAccessToken(owner.getUserId());
        strangerToken = jwtTokenProvider.createAccessToken(stranger.getUserId());

        accidentId = accidentRepository.save(Accident.builder()
            .user(owner)
            .accidentType(AccidentType.VEHICLE)
            .occurredAt(LocalDateTime.now().minusMinutes(30))
            .build()).getAccidentId();
    }

    @Test
    @DisplayName("보낸 시각이 그대로 기록된다 — 늦게 도착한 요청도 실제 신고 시각이 남는다")
    void recordsReportedTime() throws Exception {
        mockMvc.perform(post("/api/accidents/{id}/contacts", accidentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactType\":\"EMERGENCY_112\",\"contactedAt\":\"2026-09-01T12:30:00\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.contactType").value("EMERGENCY_112"))
            .andExpect(jsonPath("$.contactedAt").value("2026-09-01T12:30:00"));
    }

    @Test
    @DisplayName("시각을 보내지 않으면 서버 시각으로 기록된다")
    void defaultsToServerTime() throws Exception {
        mockMvc.perform(post("/api/accidents/{id}/contacts", accidentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactType\":\"INSURANCE\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.contactedAt").isNotEmpty());
    }

    @Test
    @DisplayName("같은 유형을 여러 번 눌러도 전부 기록된다")
    void recordsRepeatedAttempts() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/accidents/{id}/contacts", accidentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"contactType\":\"EMERGENCY_119\"}"))
                .andExpect(status().isCreated());
        }

        // 첫 통화가 안 돼서 다시 건 것일 수 있다. 걸러내면 실제로 두 번 시도한 사실이 사라진다.
        assertThat(contactLogRepository.findByAccident_AccidentIdOrderByContactedAtAsc(accidentId))
            .hasSize(2);
    }

    @Test
    @DisplayName("연결 유형이 없으면 400이다")
    void contactTypeIsRequired() throws Exception {
        mockMvc.perform(post("/api/accidents/{id}/contacts", accidentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("미래 시각은 400이다")
    void futureTimeIsRejected() throws Exception {
        String future = LocalDateTime.now().plusHours(2).withNano(0).toString();

        mockMvc.perform(post("/api/accidents/{id}/contacts", accidentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactType\":\"EMERGENCY_112\",\"contactedAt\":\"" + future + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("연결 시각은 미래일 수 없습니다."));
    }

    @Test
    @DisplayName("남의 사고에는 기록할 수 없다")
    void strangerCannotRecord() throws Exception {
        mockMvc.perform(post("/api/accidents/{id}/contacts", accidentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactType\":\"EMERGENCY_112\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ACCIDENT_NOT_FOUND"));

        assertThat(contactLogRepository.count()).isZero();
    }

    @Test
    @DisplayName("기록은 시도한 순서대로 조회된다")
    void listsInContactedOrder() throws Exception {
        Accident accident = accidentRepository.findById(accidentId).orElseThrow();
        contactLogRepository.save(ContactLog.builder().accident(accident)
            .contactType(ContactType.INSURANCE).contactedAt(LocalDateTime.of(2026, 9, 1, 12, 40)).build());
        contactLogRepository.save(ContactLog.builder().accident(accident)
            .contactType(ContactType.EMERGENCY_112).contactedAt(LocalDateTime.of(2026, 9, 1, 12, 30)).build());

        mockMvc.perform(get("/api/accidents/{id}/contacts", accidentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].contactType").value("EMERGENCY_112"))
            .andExpect(jsonPath("$[1].contactType").value("INSURANCE"));
    }

    @Test
    @DisplayName("남의 사고 기록은 조회할 수 없다")
    void strangerCannotList() throws Exception {
        mockMvc.perform(get("/api/accidents/{id}/contacts", accidentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("토큰 없이 기록할 수 없다")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/accidents/{id}/contacts", accidentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactType\":\"EMERGENCY_112\"}"))
            .andExpect(status().isUnauthorized());
    }
}
