package com.sago.domain.accident;

import com.sago.domain.user.User;
import com.sago.domain.user.UserRepository;
import com.sago.global.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    @PersistenceContext
    private EntityManager entityManager;

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
    @DisplayName("한 시간 안에 다시 누르면 새 사고를 만들지 않고 진행 중인 사고를 200으로 돌려준다")
    void secondCreateWithinWindowResumesInProgressAccident() throws Exception {
        String first = mockMvc.perform(post("/api/accidents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accidentType\":\"VEHICLE\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long firstId = com.jayway.jsonpath.JsonPath.parse(first).read("$.accidentId", Long.class);

        // 앱을 다시 켜고 다른 유형으로 또 누른 경우. 이미 진행한 체크리스트와 어긋나지 않도록 값은 반영하지 않는다.
        mockMvc.perform(post("/api/accidents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accidentType\":\"SINGLE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accidentId").value(firstId))
            .andExpect(jsonPath("$.accidentType").value("VEHICLE"));

        assertThat(accidentRepository.findByUser_UserIdOrderByOccurredAtDesc(userId)).hasSize(1);
    }

    @Test
    @DisplayName("끝난 사고는 이어 쓰지 않고 새 사고를 만든다")
    void completedAccidentIsNotResumed() throws Exception {
        Accident completed = accident(user(), LocalDateTime.now());
        completed.complete();
        Long completedId = accidentRepository.saveAndFlush(completed).getAccidentId();

        mockMvc.perform(post("/api/accidents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accidentType\":\"VEHICLE\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accidentId").value(org.hamcrest.Matchers.not(completedId.intValue())));
    }

    @Test
    @DisplayName("만든 지 한 시간이 지난 진행 중 사고는 이어 쓰지 않는다 — 멈춘 사고에 다음 사고가 붙지 않는다")
    void staleInProgressAccidentIsNotResumed() throws Exception {
        Long staleId = accidentRepository.saveAndFlush(accident(user(), LocalDateTime.now())).getAccidentId();
        // created_at은 갱신 불가 컬럼이라 직접 과거로 돌린다
        entityManager.createNativeQuery("update accident set created_at = :createdAt where accident_id = :id")
            .setParameter("createdAt", LocalDateTime.now().minusHours(2))
            .setParameter("id", staleId)
            .executeUpdate();
        entityManager.clear();

        mockMvc.perform(post("/api/accidents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accidentType\":\"VEHICLE\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accidentId").value(org.hamcrest.Matchers.not(staleId.intValue())));
    }

    @Test
    @DisplayName("다른 회원의 진행 중 사고는 이어 쓰지 않는다")
    void othersInProgressAccidentIsNotResumed() throws Exception {
        User stranger = userRepository.save(User.builder().email("other@example.com").build());
        accidentRepository.saveAndFlush(accident(stranger, LocalDateTime.now()));

        mockMvc.perform(post("/api/accidents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accidentType\":\"VEHICLE\"}"))
            .andExpect(status().isCreated());

        assertThat(accidentRepository.findByUser_UserIdOrderByOccurredAtDesc(userId)).hasSize(1);
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

    @Test
    @DisplayName("사고를 끝내면 다음 요청은 이어 쓰지 않고 새 사고를 만든다")
    void completingAccidentAllowsNextAccident() throws Exception {
        String first = mockMvc.perform(post("/api/accidents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accidentType\":\"VEHICLE\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long firstId = com.jayway.jsonpath.JsonPath.parse(first).read("$.accidentId", Long.class);

        mockMvc.perform(post("/api/accidents/{id}/complete", firstId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 30분 뒤 진짜 다음 사고가 났을 때, 이전 사고로 흡수되지 않아야 한다
        mockMvc.perform(post("/api/accidents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accidentType\":\"SINGLE\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accidentId").value(org.hamcrest.Matchers.not((int) firstId)))
            .andExpect(jsonPath("$.accidentType").value("SINGLE"));
    }

    @Test
    @DisplayName("남의 사고는 끝낼 수 없다")
    void strangerCannotCompleteAccident() throws Exception {
        User stranger = userRepository.save(User.builder().email("other@example.com").build());
        Long id = accidentRepository.saveAndFlush(accident(stranger, LocalDateTime.now())).getAccidentId();

        mockMvc.perform(post("/api/accidents/{id}/complete", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ACCIDENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("토큰 없이 사고를 끝낼 수 없다")
    void completeRequiresAuthentication() throws Exception {
        Long id = accidentRepository.saveAndFlush(accident(user(), LocalDateTime.now())).getAccidentId();

        mockMvc.perform(post("/api/accidents/{id}/complete", id))
            .andExpect(status().isUnauthorized());
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
