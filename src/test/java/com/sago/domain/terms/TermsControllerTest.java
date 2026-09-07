package com.sago.domain.terms;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 terms.yml을 읽어 들인 상태에서 동작을 확인한다.
 * 각 테스트는 트랜잭션 롤백으로 정리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TermsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TermsAgreementRepository termsAgreementRepository;

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
    @DisplayName("약관 목록은 로그인하지 않아도 조회된다")
    void documentsArePublic() throws Exception {
        mockMvc.perform(get("/api/terms"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].type").isNotEmpty())
            .andExpect(jsonPath("$[0].version").isNotEmpty())
            .andExpect(jsonPath("$[0].title").isNotEmpty());
    }

    @Test
    @DisplayName("동의 저장은 로그인이 필요하다")
    void agreeRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/terms/agreements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(true, true, true, true)))
            .andExpect(status().isUnauthorized());

        assertThat(termsAgreementRepository.count()).isZero();
    }

    @Test
    @DisplayName("필수 약관에 모두 동의하면 저장되고 재동의가 필요 없어진다")
    void agreeToAllRequired() throws Exception {
        mockMvc.perform(post("/api/terms/agreements")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(true, true, true, false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.type == 'SERVICE')].reagreeNeeded").value(false))
            .andExpect(jsonPath("$[?(@.type == 'MARKETING')].agreed").value(false));

        assertThat(termsAgreementRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("필수 약관을 거부하면 422로 거부되고 저장되지 않는다")
    void decliningRequiredTermsIsRejected() throws Exception {
        mockMvc.perform(post("/api/terms/agreements")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(true, false, true, true)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("REQUIRED_TERMS_NOT_AGREED"));

        assertThat(termsAgreementRepository.count()).isZero();
    }

    @Test
    @DisplayName("동의 여부를 빠뜨리면 400으로 거부된다")
    void missingAgreedFlagIsRejected() throws Exception {
        mockMvc.perform(post("/api/terms/agreements")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agreements\":[{\"type\":\"SERVICE\"}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("동의한 적이 없으면 필수 약관이 재동의 대상으로 나온다")
    void statusesBeforeAnyAgreement() throws Exception {
        mockMvc.perform(get("/api/terms/agreements")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.type == 'SERVICE')].reagreeNeeded").value(true))
            .andExpect(jsonPath("$[?(@.type == 'MARKETING')].reagreeNeeded").value(false));
    }

    private String body(boolean service, boolean privacy, boolean location, boolean marketing) {
        return """
            {"agreements":[
              {"type":"SERVICE","agreed":%b},
              {"type":"PRIVACY","agreed":%b},
              {"type":"LOCATION","agreed":%b},
              {"type":"MARKETING","agreed":%b}
            ]}
            """.formatted(service, privacy, location, marketing);
    }
}
