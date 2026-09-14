package com.sago.domain.checklist;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 조회·수정의 HTTP 계층과 권한을 확인한다.
 *
 * 항목을 미리 넣어두고 시작한다. 비어 있는 상태로 조회하면 Gemini 호출이 일어나
 * 테스트가 외부 통신에 의존하게 되기 때문이다. 생성 분기는 ChecklistServiceTest에서 다룬다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChecklistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccidentRepository accidentRepository;

    @Autowired
    private ChecklistItemRepository checklistItemRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String ownerToken;
    private String strangerToken;
    private Long accidentId;
    private Long itemId;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(
            User.builder().email("rider@example.com").nickname("라이더").build());
        User stranger = userRepository.save(User.builder().email("other@example.com").build());
        ownerToken = jwtTokenProvider.createAccessToken(owner.getUserId());
        strangerToken = jwtTokenProvider.createAccessToken(stranger.getUserId());

        Accident accident = accidentRepository.save(Accident.builder()
            .user(owner)
            .accidentType(AccidentType.VEHICLE)
            .occurredAt(LocalDateTime.now())
            .build());
        accidentId = accident.getAccidentId();

        itemId = checklistItemRepository.save(ChecklistItem.builder()
            .accident(accident)
            .content("부상·안전 확보")
            .orderNo(1)
            .source(ChecklistSource.STATIC)
            .build()).getChecklistItemId();
    }

    @Test
    @DisplayName("토큰 없이 체크리스트를 조회할 수 없다")
    void checklistRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/accidents/{id}/checklist", accidentId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("사고 주인은 체크리스트를 조회할 수 있다")
    void ownerCanReadChecklist() throws Exception {
        mockMvc.perform(get("/api/accidents/{id}/checklist", accidentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].content").value("부상·안전 확보"))
            .andExpect(jsonPath("$[0].completed").value(false))
            .andExpect(jsonPath("$[0].source").value("STATIC"));
    }

    @Test
    @DisplayName("남의 사고 체크리스트는 404로 응답한다")
    void othersChecklistIsNotFound() throws Exception {
        mockMvc.perform(get("/api/accidents/{id}/checklist", accidentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ACCIDENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("항목을 완료 처리하면 저장된다")
    void completeItem() throws Exception {
        mockMvc.perform(patch("/api/accidents/{aid}/checklist/{iid}", accidentId, itemId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completed").value(true))
            .andExpect(jsonPath("$.completedAt").isNotEmpty());

        assertThat(checklistItemRepository.findById(itemId))
            .get()
            .satisfies(item -> assertThat(item.isCompleted()).isTrue());
    }

    @Test
    @DisplayName("완료 여부를 빠뜨리면 400으로 거부된다")
    void missingCompletedFlagIsRejected() throws Exception {
        mockMvc.perform(patch("/api/accidents/{aid}/checklist/{iid}", accidentId, itemId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("남의 사고 항목은 수정할 수 없다")
    void strangerCannotUpdateItem() throws Exception {
        mockMvc.perform(patch("/api/accidents/{aid}/checklist/{iid}", accidentId, itemId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\":true}"))
            .andExpect(status().isNotFound());

        assertThat(checklistItemRepository.findById(itemId))
            .get()
            .satisfies(item -> assertThat(item.isCompleted()).isFalse());
    }
}
